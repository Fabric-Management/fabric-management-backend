#!/bin/bash

# ==============================================
# FABRIC MANAGEMENT SYSTEM - BUILD ALL SERVICES
# ==============================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REGISTRY=${DOCKER_REGISTRY:-"fabric-management"}
TAG=${BUILD_TAG:-"latest"}
BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Build function
build_service() {
    local service=$1
    log_info "Building $service..."

    # Build with Maven first
    mvn clean package -pl services/$service -am -DskipTests -q

    if [ $? -eq 0 ]; then
        log_success "Maven build completed for $service"
    else
        log_error "Maven build failed for $service"
        exit 1
    fi

    # Build Docker image
    docker build \
        --build-arg BUILD_DATE="$BUILD_DATE" \
        --build-arg GIT_COMMIT="$GIT_COMMIT" \
        -t $REGISTRY/$service:$TAG \
        -t $REGISTRY/$service:$GIT_COMMIT \
        -f services/$service/Dockerfile \
        .

    if [ $? -eq 0 ]; then
        log_success "Docker image built for $service"
    else
        log_error "Docker build failed for $service"
        exit 1
    fi
}

# Main execution
log_info "Starting build process for Fabric Management System"
log_info "Registry: $REGISTRY"
log_info "Tag: $TAG"
log_info "Git Commit: $GIT_COMMIT"
log_info "Build Date: $BUILD_DATE"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    log_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Maven is available
if ! mvn --version > /dev/null 2>&1; then
    log_error "Maven is not installed or not in PATH."
    exit 1
fi

# Build common modules first
log_info "Building common modules..."
mvn clean install -pl common/common-core,common/common-security -DskipTests -q

if [ $? -eq 0 ]; then
    log_success "Common modules built successfully"
else
    log_error "Failed to build common modules"
    exit 1
fi

# Services to build
SERVICES=("auth-service" "user-service" "contact-service" "company-service")

# Build each service
for service in "${SERVICES[@]}"; do
    build_service $service
done

# List built images
log_info "Built Docker images:"
docker images | grep $REGISTRY

# Push to registry if specified
if [ "$PUSH_TO_REGISTRY" = "true" ]; then
    log_info "Pushing images to registry..."
    for service in "${SERVICES[@]}"; do
        docker push $REGISTRY/$service:$TAG
        docker push $REGISTRY/$service:$GIT_COMMIT
        log_success "Pushed $service to registry"
    done
fi

log_success "Build process completed successfully!"
log_info "To deploy to Kubernetes, run: ./deploy-k8s.sh"
