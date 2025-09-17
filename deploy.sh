#!/bin/bash

# =============================================================================
# FABRIC MANAGEMENT SYSTEM - DEPLOYMENT SCRIPT
# =============================================================================
# Deployment script for Docker and Kubernetes environments

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Environment selection
ENVIRONMENT=${1:-"local"}

case $ENVIRONMENT in
    "local"|"dev")
        COMPOSE_FILE="docker-compose.yml"
        ENV_FILE=".env"
        log_info "Deploying to LOCAL/DEVELOPMENT environment"
        ;;
    "prod"|"production")
        COMPOSE_FILE="docker-compose.prod.yml"
        ENV_FILE=".env.prod"
        log_info "Deploying to PRODUCTION environment"
        ;;
    "k8s"|"kubernetes")
        log_info "Deploying to KUBERNETES environment"
        ;;
    *)
        log_error "Invalid environment. Use: local, prod, or k8s"
        exit 1
        ;;
esac

# Docker deployment
deploy_docker() {
    log_info "Starting Docker deployment..."

    # Clean up any existing containers first
    log_info "Cleaning up existing containers..."
    docker-compose -f $COMPOSE_FILE down --remove-orphans 2>/dev/null || true
    docker container prune -f 2>/dev/null || true

    # Check if .env file exists
    if [ ! -f "$ENV_FILE" ]; then
        log_warning ".env file not found. Creating from .env.example..."
        cp .env.example "$ENV_FILE"
        log_warning "Please update $ENV_FILE with your actual values"
    fi

    # Verify port configuration
    log_info "Verifying port configuration..."
    if [ -f "$ENV_FILE" ]; then
        log_info "Port mappings from $ENV_FILE:"
        grep -E "SERVICE_PORT=" "$ENV_FILE" || echo "Using default ports: 8081, 8082, 8083, 8084"
    fi

    # Build and start services
    log_info "Building and starting services..."
    docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE up -d --build --remove-orphans

    # Wait for services to be healthy
    log_info "Waiting for services to be healthy..."
    sleep 30

    # Check service health
    check_docker_health

    log_success "Docker deployment completed!"
    log_info "Services are available at:"
    echo "  - Auth Service: http://localhost:8081/api/v1/auth"
    echo "  - User Service: http://localhost:8082/api/v1/users"
    echo "  - Company Service: http://localhost:8083/api/v1/companies"
    echo "  - Contact Service: http://localhost:8084/api/v1/contacts"
    echo "  - PostgreSQL: localhost:5433"
    echo "  - Redis: localhost:6379"
    if [ "$ENVIRONMENT" = "local" ]; then
        echo "  - PgAdmin: http://localhost:5050"
        echo "  - Redis Commander: http://localhost:8085"
    fi
}

# Kubernetes deployment
deploy_kubernetes() {
    log_info "Starting Kubernetes deployment..."

    # Check if kubectl is available
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed or not in PATH"
        exit 1
    fi

    # Apply namespace
    log_info "Creating namespace..."
    kubectl apply -f deployment/k8s/base/namespace.yaml

    # Apply base configurations
    log_info "Applying base configurations..."
    kubectl apply -f deployment/k8s/base/

    # Apply infrastructure
    log_info "Deploying infrastructure services..."
    kubectl apply -f deployment/k8s/infrastructure/

    # Wait for infrastructure to be ready
    log_info "Waiting for infrastructure services..."
    kubectl wait --for=condition=ready pod -l app=postgres -n fabric-management --timeout=300s
    kubectl wait --for=condition=ready pod -l app=redis -n fabric-management --timeout=300s

    # Apply microservices
    log_info "Deploying microservices..."
    kubectl apply -f deployment/k8s/services/

    # Wait for services to be ready
    log_info "Waiting for microservices..."
    kubectl wait --for=condition=ready pod -l app=auth-service -n fabric-management --timeout=300s
    kubectl wait --for=condition=ready pod -l app=user-service -n fabric-management --timeout=300s
    kubectl wait --for=condition=ready pod -l app=company-service -n fabric-management --timeout=300s
    kubectl wait --for=condition=ready pod -l app=contact-service -n fabric-management --timeout=300s

    # Get service information
    log_success "Kubernetes deployment completed!"
    log_info "Getting service information..."
    kubectl get services -n fabric-management
    kubectl get pods -n fabric-management
}

# Health check for Docker
check_docker_health() {
    log_info "Checking service health..."

    services=("auth-service:8081" "user-service:8082" "company-service:8083" "contact-service:8084")

    for service in "${services[@]}"; do
        IFS=':' read -r name port <<< "$service"
        if curl -s -f "http://localhost:$port/actuator/health" > /dev/null; then
            log_success "$name is healthy"
        else
            log_warning "$name health check failed"
        fi
    done
}

# Stop services
stop_services() {
    if [ "$ENVIRONMENT" = "k8s" ]; then
        log_info "Stopping Kubernetes services..."
        kubectl delete namespace fabric-management
    else
        log_info "Stopping Docker services..."
        docker-compose -f $COMPOSE_FILE down --remove-orphans
        log_info "Removing any dangling containers and networks..."
        docker container prune -f
        docker network prune -f
    fi
    log_success "Services stopped"
}

# Main execution
case "${2:-deploy}" in
    "deploy")
        if [ "$ENVIRONMENT" = "k8s" ]; then
            deploy_kubernetes
        else
            deploy_docker
        fi
        ;;
    "stop")
        stop_services
        ;;
    "restart")
        stop_services
        sleep 5
        if [ "$ENVIRONMENT" = "k8s" ]; then
            deploy_kubernetes
        else
            deploy_docker
        fi
        ;;
    *)
        log_error "Invalid action. Use: deploy, stop, or restart"
        exit 1
        ;;
esac
