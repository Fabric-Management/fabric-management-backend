#!/bin/bash

# =============================================================================
# FABRIC MANAGEMENT SYSTEM - OPTIMIZED DEPLOYMENT SCRIPT
# =============================================================================
# Clean, simple, and following best practices
# Usage: ./scripts/deploy.sh [environment]
# =============================================================================

set -euo pipefail  # Exit on error, undefined variables, pipe failures

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${1:-local}"
COMPOSE_FILE="docker-compose.yml"
HEALTH_CHECK_RETRIES=30
HEALTH_CHECK_DELAY=2

# Functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose is not installed"
        exit 1
    fi
    
    # Check if Docker daemon is running
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

cleanup_existing() {
    log_info "Cleaning up existing containers..."
    
    # Stop and remove existing containers
    docker-compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
    
    # Clean up dangling images (optional)
    docker image prune -f &> /dev/null || true
    
    log_success "Cleanup completed"
}

start_infrastructure() {
    log_info "Starting infrastructure services..."
    
    # Start only infrastructure services first
    docker-compose -f "$COMPOSE_FILE" up -d postgres redis kafka zookeeper
    
    # Wait for PostgreSQL to be ready
    log_info "Waiting for PostgreSQL to be ready..."
    for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
        if docker-compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U fabric_user &> /dev/null; then
            log_success "PostgreSQL is ready"
            break
        fi
        
        if [ $i -eq $HEALTH_CHECK_RETRIES ]; then
            log_error "PostgreSQL failed to start"
            exit 1
        fi
        
        sleep $HEALTH_CHECK_DELAY
    done
    
    # Wait for Redis
    log_info "Waiting for Redis to be ready..."
    for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
        if docker-compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping &> /dev/null; then
            log_success "Redis is ready"
            break
        fi
        
        if [ $i -eq $HEALTH_CHECK_RETRIES ]; then
            log_error "Redis failed to start"
            exit 1
        fi
        
        sleep $HEALTH_CHECK_DELAY
    done
    
    log_success "Infrastructure services are running"
}

build_services() {
    log_info "Building application services..."
    
    # Build all services
    docker-compose -f "$COMPOSE_FILE" build --parallel
    
    log_success "Services built successfully"
}

start_services() {
    log_info "Starting application services..."
    
    # Start remaining services
    docker-compose -f "$COMPOSE_FILE" up -d
    
    log_success "All services started"
}

run_migrations() {
    log_info "Running database migrations..."
    
    # Check if migration script exists
    if [ -f "./scripts/run-migrations.sh" ]; then
        chmod +x ./scripts/run-migrations.sh
        ./scripts/run-migrations.sh
        log_success "Migrations completed"
    else
        log_warning "Migration script not found, skipping..."
    fi
}

health_check() {
    log_info "Performing health checks..."
    
    local all_healthy=true
    
    # Define services and their health endpoints
    declare -A services=(
        ["user-service"]="http://localhost:8081/actuator/health"
        ["company-service"]="http://localhost:8083/actuator/health"
        ["contact-service"]="http://localhost:8082/actuator/health"
    )
    
    # Wait a bit for services to start
    sleep 10
    
    # Check each service
    for service in "${!services[@]}"; do
        local url="${services[$service]}"
        
        log_info "Checking $service..."
        
        for i in $(seq 1 $HEALTH_CHECK_RETRIES); do
            if curl -f -s "$url" &> /dev/null; then
                log_success "$service is healthy"
                break
            fi
            
            if [ $i -eq $HEALTH_CHECK_RETRIES ]; then
                log_error "$service is not responding"
                all_healthy=false
            fi
            
            sleep $HEALTH_CHECK_DELAY
        done
    done
    
    if [ "$all_healthy" = true ]; then
        log_success "All services are healthy"
    else
        log_warning "Some services are not healthy"
    fi
}

show_status() {
    log_info "Service Status:"
    echo ""
    docker-compose -f "$COMPOSE_FILE" ps
    echo ""
    
    log_info "Access URLs:"
    echo "  • User Service:    http://localhost:8081"
    echo "  • Company Service: http://localhost:8083"
    echo "  • Contact Service: http://localhost:8082"
    echo "  • PostgreSQL:      localhost:5433"
    echo "  • Redis:           localhost:6379"
    echo "  • Kafka:           localhost:9092"
}

# Main execution
main() {
    echo "================================================"
    echo "  FABRIC MANAGEMENT SYSTEM DEPLOYMENT"
    echo "  Environment: $ENVIRONMENT"
    echo "================================================"
    echo ""
    
    # Execute deployment steps
    check_prerequisites
    cleanup_existing
    start_infrastructure
    build_services
    start_services
    run_migrations
    health_check
    show_status
    
    echo ""
    echo "================================================"
    log_success "Deployment completed successfully!"
    echo "================================================"
}

# Run main function
main "$@"
