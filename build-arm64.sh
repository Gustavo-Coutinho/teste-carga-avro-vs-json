#!/bin/bash
# Script de build para Linux/macOS em ARM64

set -e  # Encerra em caso de erro

# Define variáveis
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

# Função para obter e incrementar a versão
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

# Obtém a próxima versão
VERSION=$(get_next_version)-arm64
echo "Building version: $VERSION"

# Cria o diretório target caso não exista
if [ ! -d "$TARGET_DIR" ]; then
    mkdir -p "$TARGET_DIR"
fi

# Cria ou verifica se o volume de cache do Maven existe
echo "Setting up Maven cache volume..."
if ! docker volume ls --format "{{.Name}}" | grep -q "^${MAVEN_CACHE_VOLUME}$"; then
    echo "Creating Maven cache volume: $MAVEN_CACHE_VOLUME"
    docker volume create "$MAVEN_CACHE_VOLUME" > /dev/null
else
    echo "Using existing Maven cache volume: $MAVEN_CACHE_VOLUME"
fi

# Baixa a imagem do Maven para a plataforma correta
echo "Pulling Maven image for ARM64 platform..."
docker pull --platform linux/arm64 maven:3.9-eclipse-temurin-17

# Executa o Maven em Docker com cache persistente
echo "Building with Maven in Docker (with persistent dependency cache)..."
docker run --rm \
    --platform linux/arm64 \
    -v "${PROJECT_DIR}:/app" \
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" \
    -w /app \
    maven:3.9-eclipse-temurin-17 \
    mvn clean package -DskipTests

# Verifica se o JAR foi gerado com sucesso
JAR_FILE=$(find "$TARGET_DIR" -maxdepth 1 -name "*.jar" ! -name "*original*" -type f | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found in target directory. Build may have failed." >&2
    exit 1
fi

echo "JAR file built: $(basename "$JAR_FILE")"

# Cria um diretório temporário para o contexto de build
BUILD_CONTEXT="${PROJECT_DIR}/docker-build-context"
mkdir -p "$BUILD_CONTEXT"
echo "Created temporary build context at $BUILD_CONTEXT"

# Copia o arquivo JAR para o contexto de build
cp "$JAR_FILE" "$BUILD_CONTEXT/app.jar"
echo "Copied JAR to build context as app.jar"

# Copia o Dockerfile para o contexto de build
cp "${PROJECT_DIR}/Dockerfile" "$BUILD_CONTEXT/Dockerfile"
echo "Copied Dockerfile to build context"

# Atualiza o Dockerfile no contexto de build para usar o caminho simplificado
sed -i 's|COPY .*target/.* /app/aplicacao.jar|COPY app.jar /app/aplicacao.jar|g' "$BUILD_CONTEXT/Dockerfile"
echo "Updated Dockerfile in build context"

# Habilita o Docker BuildKit para builds mais rápidos
export DOCKER_BUILDKIT=1

# Constrói a imagem Docker com cache otimizado
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

# Remove o contexto de build temporário
rm -rf "$BUILD_CONTEXT"
echo "Cleaned up temporary build context"

echo "Docker image built successfully: ${IMAGE_NAME}:${VERSION}"

# Remove as imagens locais mais antigas (mantém apenas as 2 mais recentes)
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

# Marca a imagem mais recente como latest-arm64 para uso local
echo "Tagging image as latest-arm64 for local use..."
docker tag "${IMAGE_NAME}:${VERSION}" "${IMAGE_NAME}:latest-arm64"

# Envia a imagem Docker para o registro com upload paralelo de camadas
echo "Pushing Docker image to registry..."

# Envia primeiro a tag de versão (geralmente a mais importante)
echo "Pushing ${IMAGE_NAME}:${VERSION}..."
docker push "${IMAGE_NAME}:${VERSION}"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed for version ${VERSION}!" >&2
    exit 1
fi

# Envia a tag latest-arm64 (mais rápido por reutilizar camadas)
echo "Pushing ${IMAGE_NAME}:latest-arm64..."
docker push "${IMAGE_NAME}:latest-arm64"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed for latest-arm64 tag!" >&2
    exit 1
fi

echo "Build and upload complete!"
echo "Version: $VERSION"
echo "Image: ${IMAGE_NAME}:${VERSION} and ${IMAGE_NAME}:latest-arm64"

# Usa o arquivo docker-compose específico para ARM64
docker compose -f docker-compose-arm64.yaml down
docker compose -f docker-compose-arm64.yaml up -d