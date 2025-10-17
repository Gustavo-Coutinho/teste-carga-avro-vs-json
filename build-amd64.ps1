# Build script for Windows

# Define variables
$IMAGE_NAME = "gupoco/teste-carga-avro-vs-json"
$CONTAINER_NAME = "maven-build-container"
$PROJECT_DIR = Get-Location
$TARGET_DIR = Join-Path $PROJECT_DIR "target"
$VERSION_FILE = Join-Path $PROJECT_DIR "VERSION"
$USE_CACHE = $env:USE_DOCKER_CACHE -eq "false" ? $false : $true
$MAVEN_CACHE_VOLUME = "maven-cache-volume"

# Function to get and increment version
function Get-NextVersion {
    if (Test-Path $VERSION_FILE) {
        $currentVersion = Get-Content $VERSION_FILE -Raw | Out-String | ForEach-Object { $_.Trim() }
        $versionParts = $currentVersion.Split('.')
        $patch = [int]$versionParts[2] + 1
        $newVersion = "$($versionParts[0]).$($versionParts[1]).$patch"
    } else {
        $newVersion = "1.0.0"
    }
    Set-Content -Path $VERSION_FILE -Value $newVersion
    return $newVersion
}

# Get next version
$VERSION = Get-NextVersion
Write-Host "Building version: $VERSION"

# Create target directory if it doesn't exist
if (-not (Test-Path $TARGET_DIR)) {
    New-Item -Path $TARGET_DIR -ItemType Directory | Out-Null
}

# Create or verify Maven cache volume exists
Write-Host "Setting up Maven cache volume..."
$volumeExists = docker volume ls --format "{{.Name}}" | Select-String -Pattern "^${MAVEN_CACHE_VOLUME}$" -Quiet
if (-not $volumeExists) {
    Write-Host "Creating Maven cache volume: $MAVEN_CACHE_VOLUME"
    docker volume create $MAVEN_CACHE_VOLUME | Out-Null
} else {
    Write-Host "Using existing Maven cache volume: $MAVEN_CACHE_VOLUME"
}

# Run Maven in Docker directly with persistent cache
Write-Host "Building with Maven in Docker (with persistent dependency cache)..."
docker run --rm `
    -v "${PROJECT_DIR}:/app" `
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" `
    -w /app `
    maven:3.9-eclipse-temurin-17 `
    mvn clean package -DskipTests


# Check if JAR was built successfully
$JAR_FILE = Get-ChildItem -Path $TARGET_DIR -Name "*.jar" | Where-Object { $_ -notlike "*original*" } | Select-Object -First 1
if (-not $JAR_FILE) {
    Write-Error "JAR file not found in target directory. Build may have failed."
    exit 1
}

Write-Host "JAR file built: $JAR_FILE"

# Enable Docker BuildKit for faster builds
$env:DOCKER_BUILDKIT = "1"

# Check that the JAR file exists and is accessible
$JAR_FULL_PATH = Join-Path -Path $TARGET_DIR -ChildPath $JAR_FILE
if (-not (Test-Path $JAR_FULL_PATH)) {
    Write-Error "JAR file cannot be accessed at $JAR_FULL_PATH"
    exit 1
}
Write-Host "Verified JAR exists at: $JAR_FULL_PATH"

# Build Docker image with optimized caching
Write-Host "Building Docker image with BuildKit: ${IMAGE_NAME}:${VERSION}..."
Write-Host "Using cache: $USE_CACHE"

# Create a temporary build context directory
$BUILD_CONTEXT = Join-Path $PROJECT_DIR "docker-build-context"
New-Item -Path $BUILD_CONTEXT -ItemType Directory -Force | Out-Null
Write-Host "Created temporary build context at $BUILD_CONTEXT"

# Copy the JAR file to the build context
Copy-Item -Path $JAR_FULL_PATH -Destination "$BUILD_CONTEXT/app.jar" -Force
Write-Host "Copied JAR to build context as app.jar"

# Copy Dockerfile to the build context
Copy-Item -Path (Join-Path $PROJECT_DIR "Dockerfile") -Destination "$BUILD_CONTEXT/Dockerfile" -Force
Write-Host "Copied Dockerfile to build context"

# Update Dockerfile in the build context to use the simplified path
$DockerfileContent = Get-Content -Path "$BUILD_CONTEXT/Dockerfile" -Raw
$DockerfileContent = $DockerfileContent -replace "COPY \./target/\*.jar /app/aplicacao\.jar", "COPY app.jar /app/aplicacao.jar"
Set-Content -Path "$BUILD_CONTEXT/Dockerfile" -Value $DockerfileContent -Force
Write-Host "Updated Dockerfile in build context"

$buildArgs = @(
    "--platform", "linux/amd64",
    "--build-arg", "BUILDKIT_INLINE_CACHE=1",
    "-t", "${IMAGE_NAME}:${VERSION}",
    "-t", "${IMAGE_NAME}:latest"
)

if ($USE_CACHE) {
    $buildArgs += "--cache-from", "${IMAGE_NAME}:latest"
    Write-Host "Pulling latest image for cache..."
    # docker pull "${IMAGE_NAME}:latest" 2>$null || Write-Host "No cache image found, proceeding without cache"
}

docker build @buildArgs $BUILD_CONTEXT

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build failed!"
    exit 1
}

# Clean up temporary build context
Remove-Item -Path $BUILD_CONTEXT -Recurse -Force
Write-Host "Cleaned up temporary build context"

Write-Host "Docker image built successfully: ${IMAGE_NAME}:${VERSION}"

# Clean up oldest local images (keep only the 2 most recent)
Write-Host "Cleaning up oldest local images..."
$existingImages = docker images "${IMAGE_NAME}" --format "{{.Repository}}:{{.Tag}} {{.CreatedAt}}" | 
    Where-Object { $_ -notlike "*latest*" } |
    Sort-Object { ($_ -split ' ', 2)[1] } -Descending |
    Select-Object -Skip 2

if ($existingImages) {
    $existingImages | ForEach-Object {
        $imageTag = ($_ -split ' ')[0]
        Write-Host "Removing old image: $imageTag"
        docker rmi $imageTag -f 2>$null
    }
} else {
    Write-Host "No old images to clean up."
}

# tag the newest image as latest for local use
Write-Host "Tagging image as latest for local use..."
docker tag "${IMAGE_NAME}:${VERSION}" "${IMAGE_NAME}:latest"

# Push Docker image to registry with parallel layer uploads
Write-Host "Pushing Docker image to registry..."

# Push version tag first (usually the one we care about most)
Write-Host "Pushing ${IMAGE_NAME}:${VERSION}..."
docker push "${IMAGE_NAME}:${VERSION}"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker push failed for version ${VERSION}!"
    exit 1
}

# Push latest tag (this will be faster due to shared layers)
Write-Host "Pushing ${IMAGE_NAME}:latest..."
docker push "${IMAGE_NAME}:latest"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker push failed for latest tag!"
    exit 1
}

Write-Host "Build and upload complete!"
Write-Host "Version: $VERSION"
Write-Host "Image: ${IMAGE_NAME}:${VERSION}"

docker compose down
docker compose up -d