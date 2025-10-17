#!/bin/bash
# Build script for Linux/macOS ARM64 platform

set -e  # Exit on error

# Define variables
IMAGE_NAME="gupoco/teste-carga-avro-vs-json"
CONTAINER_NAME="maven-build-container"
PROJECT_DIR="$(pwd)"
TARGET_DIR="${PROJECT_DIR}/target"
VERSION_FILE="${PROJECT_DIR}/VERSION"
USE_CACHE="${USE_DOCKER_CACHE:-true}"
if [ "$USE_CACHE" = "false" ]; then
    USE_CACHE=false
else
    USE_CACHE=true
fi
MAVEN_CACHE_VOLUME="maven-cache-volume"

# Function to get and increment version
get_next_version() {
    if [ -f "$VERSION_FILE" ]; then
        currentVersion=$(cat "$VERSION_FILE" | tr -d '[:space:]')
        IFS='.' read -r -a versionParts <<< "$currentVersion"
        patch=$((${versionParts[2]} + 1))
        newVersion="${versionParts[0]}.${versionParts[1]}.$patch"
    else
        newVersion="1.0.0"
    fi
    echo "$newVersion" > "$VERSION_FILE"
    echo "$newVersion"
}

# Get next version
VERSION=$(get_next_version)-arm64
echo "Building version: $VERSION"

# Create target directory if it doesn't exist
if [ ! -d "$TARGET_DIR" ]; then
    mkdir -p "$TARGET_DIR"
fi

# Create or verify Maven cache volume exists
echo "Setting up Maven cache volume..."
if ! docker volume ls --format "{{.Name}}" | grep -q "^${MAVEN_CACHE_VOLUME}$"; then
    echo "Creating Maven cache volume: $MAVEN_CACHE_VOLUME"
    docker volume create "$MAVEN_CACHE_VOLUME" > /dev/null
else
    echo "Using existing Maven cache volume: $MAVEN_CACHE_VOLUME"
fi

# Pull the correct platform Maven image
echo "Pulling Maven image for ARM64 platform..."
docker pull --platform linux/arm64 maven:3.9-eclipse-temurin-17

# Run Maven in Docker directly with persistent cache
echo "Building with Maven in Docker (with persistent dependency cache)..."
docker run --rm \
    --platform linux/arm64 \
    -v "${PROJECT_DIR}:/app" \
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" \
    -w /app \
    maven:3.9-eclipse-temurin-17 \
    mvn clean package -DskipTests

# Check if JAR was built successfully
JAR_FILE=$(find "$TARGET_DIR" -maxdepth 1 -name "*.jar" ! -name "*original*" -type f | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found in target directory. Build may have failed." >&2
    exit 1
fi

echo "JAR file built: $(basename "$JAR_FILE")"

# Create a temporary build context directory
BUILD_CONTEXT="${PROJECT_DIR}/docker-build-context"
mkdir -p "$BUILD_CONTEXT"
echo "Created temporary build context at $BUILD_CONTEXT"

# Copy the JAR file to the build context
cp "$JAR_FILE" "$BUILD_CONTEXT/app.jar"
echo "Copied JAR to build context as app.jar"

# Copy Dockerfile to the build context
cp "${PROJECT_DIR}/Dockerfile" "$BUILD_CONTEXT/Dockerfile"
echo "Copied Dockerfile to build context"

# Update Dockerfile in the build context to use the simplified path
sed -i 's|COPY .*target/.* /app/aplicacao.jar|COPY app.jar /app/aplicacao.jar|g' "$BUILD_CONTEXT/Dockerfile"
echo "Updated Dockerfile in build context"

# Enable Docker BuildKit for faster builds
export DOCKER_BUILDKIT=1

# Build Docker image with optimized caching
echo "Building Docker image with BuildKit: ${IMAGE_NAME}:${VERSION}..."
echo "Using cache: $USE_CACHE"

buildArgs=(
    "--platform" "linux/arm64"
    "--build-arg" "BUILDKIT_INLINE_CACHE=1"
    "-t" "${IMAGE_NAME}:${VERSION}"
    "-t" "${IMAGE_NAME}:latest-arm64"
)

if [ "$USE_CACHE" = true ]; then
    buildArgs+=("--cache-from" "${IMAGE_NAME}:latest-arm64")
    echo "Pulling latest image for cache..."
    docker pull "${IMAGE_NAME}:latest-arm64" 2>/dev/null || echo "No cache image found, proceeding without cache"
fi

docker build "${buildArgs[@]}" "$BUILD_CONTEXT"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed!" >&2
    exit 1
fi

# Clean up temporary build context
rm -rf "$BUILD_CONTEXT"
echo "Cleaned up temporary build context"

echo "Docker image built successfully: ${IMAGE_NAME}:${VERSION}"

# Clean up oldest local images (keep only the 2 most recent)
echo "Cleaning up oldest local images..."
existingImages=$(docker images "${IMAGE_NAME}" --format "{{.Repository}}:{{.Tag}} {{.CreatedAt}}" | \
    grep -v ":latest" | \
    grep "arm64" | \
    sort -k2 -r | \
    tail -n +3)

if [ -n "$existingImages" ]; then
    echo "$existingImages" | while read -r line; do
        imageTag=$(echo "$line" | awk '{print $1}')
        echo "Removing old image: $imageTag"
        docker rmi "$imageTag" -f 2>/dev/null || true
    done
else
    echo "No old images to clean up."
fi

# Tag the newest image as latest for local use
echo "Tagging image as latest-arm64 for local use..."
docker tag "${IMAGE_NAME}:${VERSION}" "${IMAGE_NAME}:latest-arm64"

# Push Docker image to registry with parallel layer uploads
echo "Pushing Docker image to registry..."

# Push version tag first (usually the one we care about most)
echo "Pushing ${IMAGE_NAME}:${VERSION}..."
docker push "${IMAGE_NAME}:${VERSION}"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed for version ${VERSION}!" >&2
    exit 1
fi

# Push latest tag (this will be faster due to shared layers)
echo "Pushing ${IMAGE_NAME}:latest-arm64..."
docker push "${IMAGE_NAME}:latest-arm64"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed for latest-arm64 tag!" >&2
    exit 1
fi

echo "Build and upload complete!"
echo "Version: $VERSION"
echo "Image: ${IMAGE_NAME}:${VERSION} and ${IMAGE_NAME}:latest-arm64"

# Create a special docker-compose-arm64.yml file with platform specific settings
cat > docker-compose-arm64.yml << EOF
version: '3.8'

services:
  kafka-carga-app:
    image: ${IMAGE_NAME}:latest-arm64
    platform: linux/arm64
    container_name: kafka-carga-sandbox
    env_file:
      - .env
    environment:
      - TIPO_APLICACAO=\${TIPO_APLICACAO}
      - KAFKA_BOOTSTRAP_SERVERS=\${KAFKA_BOOTSTRAP_SERVERS}
      - KAFKA_CLUSTER_API_KEY=\${KAFKA_CLUSTER_API_KEY}
      - KAFKA_CLUSTER_API_SECRET=\${KAFKA_CLUSTER_API_SECRET}
      - SCHEMA_REGISTRY_URL=\${SCHEMA_REGISTRY_URL}
      - SCHEMA_REGISTRY_API_KEY=\${SCHEMA_REGISTRY_API_KEY}
      - SCHEMA_REGISTRY_API_SECRET=\${SCHEMA_REGISTRY_API_SECRET}
    networks:
      - kafka-network
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G

networks:
  kafka-network:
    driver: bridge
EOF

# Use the ARM64-specific docker-compose file
docker compose -f docker-compose-arm64.yml down
docker compose -f docker-compose-arm64.yml up -d