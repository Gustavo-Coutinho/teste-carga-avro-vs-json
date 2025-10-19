# Script de build para Windows

# Define variáveis
$IMAGE_NAME = "gupoco/teste-carga-avro-vs-json"
$CONTAINER_NAME = "maven-build-container"
$PROJECT_DIR = Get-Location
$TARGET_DIR = Join-Path $PROJECT_DIR "target"
$VERSION_FILE = Join-Path $PROJECT_DIR "VERSION"
$USE_CACHE = $env:USE_DOCKER_CACHE -eq "false" ? $false : $true
$MAVEN_CACHE_VOLUME = "maven-cache-volume"

# Função para obter e incrementar a versão
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

# Obtém a próxima versão
$VERSION = Get-NextVersion
Write-Host "Building version: $VERSION"

# Cria o diretório target caso não exista
if (-not (Test-Path $TARGET_DIR)) {
    New-Item -Path $TARGET_DIR -ItemType Directory | Out-Null
}

# Cria ou verifica se o volume de cache do Maven existe
Write-Host "Setting up Maven cache volume..."
$volumeExists = docker volume ls --format "{{.Name}}" | Select-String -Pattern "^${MAVEN_CACHE_VOLUME}$" -Quiet
if (-not $volumeExists) {
    Write-Host "Creating Maven cache volume: $MAVEN_CACHE_VOLUME"
    docker volume create $MAVEN_CACHE_VOLUME | Out-Null
} else {
    Write-Host "Using existing Maven cache volume: $MAVEN_CACHE_VOLUME"
}

# Executa o Maven em Docker com cache persistente
Write-Host "Building with Maven in Docker (with persistent dependency cache)..."
docker run --rm `
    -v "${PROJECT_DIR}:/app" `
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" `
    -w /app `
    maven:3.9-eclipse-temurin-17 `
    mvn clean package -DskipTests


# Verifica se o JAR foi gerado com sucesso
$JAR_FILE = Get-ChildItem -Path $TARGET_DIR -Name "*.jar" | Where-Object { $_ -notlike "*original*" } | Select-Object -First 1
if (-not $JAR_FILE) {
    Write-Error "JAR file not found in target directory. Build may have failed."
    exit 1
}

Write-Host "JAR file built: $JAR_FILE"

# Habilita o Docker BuildKit para builds mais rápidos
$env:DOCKER_BUILDKIT = "1"

# Verifica se o arquivo JAR existe e está acessível
$JAR_FULL_PATH = Join-Path -Path $TARGET_DIR -ChildPath $JAR_FILE
if (-not (Test-Path $JAR_FULL_PATH)) {
    Write-Error "JAR file cannot be accessed at $JAR_FULL_PATH"
    exit 1
}
Write-Host "Verified JAR exists at: $JAR_FULL_PATH"

# Constrói a imagem Docker com cache otimizado
Write-Host "Building Docker image with BuildKit: ${IMAGE_NAME}:${VERSION}..."
Write-Host "Using cache: $USE_CACHE"

# Cria um diretório temporário para o contexto de build
$BUILD_CONTEXT = Join-Path $PROJECT_DIR "docker-build-context"
New-Item -Path $BUILD_CONTEXT -ItemType Directory -Force | Out-Null
Write-Host "Created temporary build context at $BUILD_CONTEXT"

# Copia o arquivo JAR para o contexto de build
Copy-Item -Path $JAR_FULL_PATH -Destination "$BUILD_CONTEXT/app.jar" -Force
Write-Host "Copied JAR to build context as app.jar"

# Copia o Dockerfile para o contexto de build
Copy-Item -Path (Join-Path $PROJECT_DIR "Dockerfile") -Destination "$BUILD_CONTEXT/Dockerfile" -Force
Write-Host "Copied Dockerfile to build context"

# Atualiza o Dockerfile no contexto de build para usar o caminho simplificado
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

# Remove o contexto de build temporário
Remove-Item -Path $BUILD_CONTEXT -Recurse -Force
Write-Host "Cleaned up temporary build context"

Write-Host "Docker image built successfully: ${IMAGE_NAME}:${VERSION}"

# Remove as imagens locais mais antigas (mantém apenas as 2 mais recentes)
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

# Marca a imagem mais recente como latest para uso local
Write-Host "Tagging image as latest for local use..."
docker tag "${IMAGE_NAME}:${VERSION}" "${IMAGE_NAME}:latest"

# Envia a imagem Docker para o registro com upload paralelo de camadas
Write-Host "Pushing Docker image to registry..."

# Envia primeiro a tag de versão (geralmente a mais importante)
Write-Host "Pushing ${IMAGE_NAME}:${VERSION}..."
docker push "${IMAGE_NAME}:${VERSION}"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker push failed for version ${VERSION}!"
    exit 1
}

# Envia a tag latest (mais rápido por reutilizar camadas)
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