#!/bin/bash

# ==============================================
# FABRIC MANAGEMENT SYSTEM - KUBERNETES DEPLOYMENT
# ==============================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="fabric-management"
DEPLOYMENT_TYPE=${DEPLOYMENT_TYPE:-"development"}
TIMEOUT=${TIMEOUT:-"300s"}

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

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check kubectl
    if ! kubectl version --client > /dev/null 2>&1; then
        log_error "kubectl is not installed or not in PATH"
        exit 1
    fi

    # Check cluster connection
    if ! kubectl cluster-info > /dev/null 2>&1; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

# Deploy base configuration
deploy_base() {
    log_info "Deploying base configuration..."

    # Create namespace and RBAC
    kubectl apply -f deployment/k8s/base/namespace.yaml
    kubectl apply -f deployment/k8s/base/configmaps.yaml
    kubectl apply -f deployment/k8s/base/secrets.yaml

    log_success "Base configuration deployed"
}

# Deploy infrastructure
deploy_infrastructure() {
    log_info "Deploying infrastructure components..."

    # Deploy PostgreSQL
    kubectl apply -f deployment/k8s/infrastructure/postgres.yaml
    log_info "PostgreSQL deployment started"

    # Deploy Redis and RabbitMQ
    kubectl apply -f deployment/k8s/infrastructure/redis-rabbitmq.yaml
    log_info "Redis and RabbitMQ deployment started"

    # Wait for infrastructure to be ready
    log_info "Waiting for infrastructure to be ready..."
    kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=$TIMEOUT
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=$TIMEOUT
    kubectl wait --for=condition=ready pod -l app=rabbitmq -n $NAMESPACE --timeout=$TIMEOUT

    log_success "Infrastructure components deployed and ready"
}

# Deploy services
deploy_services() {
    log_info "Deploying microservices..."

    # Deploy in dependency order
    kubectl apply -f deployment/k8s/services/auth-service.yaml
    log_info "Auth service deployment started"

    # Wait for auth service to be ready
    kubectl wait --for=condition=ready pod -l app=auth-service -n $NAMESPACE --timeout=$TIMEOUT
    log_success "Auth service ready"

    # Deploy user service
    kubectl apply -f deployment/k8s/services/user-service.yaml
    log_info "User service deployment started"

    # Deploy contact and company services in parallel
    kubectl apply -f deployment/k8s/services/contact-service.yaml
    kubectl apply -f deployment/k8s/services/company-service.yaml
    log_info "Contact and Company services deployment started"

    # Wait for all services
    kubectl wait --for=condition=ready pod -l app=user-service -n $NAMESPACE --timeout=$TIMEOUT
    kubectl wait --for=condition=ready pod -l app=contact-service -n $NAMESPACE --timeout=$TIMEOUT
    kubectl wait --for=condition=ready pod -l app=company-service -n $NAMESPACE --timeout=$TIMEOUT

    log_success "All microservices deployed and ready"
}

# Deploy API Gateway
deploy_gateway() {
    log_info "Deploying API Gateway..."

    kubectl apply -f deployment/k8s/infrastructure/api-gateway.yaml
    kubectl wait --for=condition=ready pod -l app=api-gateway -n $NAMESPACE --timeout=$TIMEOUT

    log_success "API Gateway deployed and ready"
}

# Health check
health_check() {
    log_info "Performing health checks..."

    # Check service endpoints
    SERVICES=("auth-service" "user-service" "contact-service" "company-service")

    for service in "${SERVICES[@]}"; do
        # Port forward and check health
        kubectl port-forward svc/$service 8080:8080 -n $NAMESPACE &
        PF_PID=$!
        sleep 5

        if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            log_success "$service health check passed"
        else
            log_warning "$service health check failed"
        fi

        kill $PF_PID > /dev/null 2>&1 || true
    done
}

# Display deployment status
show_status() {
    log_info "Deployment Status:"
    echo
    kubectl get pods -n $NAMESPACE
    echo
    kubectl get services -n $NAMESPACE
    echo
    kubectl get ingress -n $NAMESPACE 2>/dev/null || true
    echo

    # Get API Gateway external IP
    EXTERNAL_IP=$(kubectl get svc api-gateway -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
    log_info "API Gateway External IP: $EXTERNAL_IP"

    if [ "$EXTERNAL_IP" != "pending" ] && [ "$EXTERNAL_IP" != "" ]; then
        log_success "Access your application at: http://$EXTERNAL_IP"
    else
        log_warning "External IP is still pending. Use 'kubectl get svc api-gateway -n $NAMESPACE' to check status."
    fi
}

# Rollback function
rollback() {
    log_warning "Rolling back deployment..."

    # Rollback services
    kubectl rollout undo deployment/auth-service -n $NAMESPACE
    kubectl rollout undo deployment/user-service -n $NAMESPACE
    kubectl rollout undo deployment/contact-service -n $NAMESPACE
    kubectl rollout undo deployment/company-service -n $NAMESPACE
    kubectl rollout undo deployment/api-gateway -n $NAMESPACE

    log_success "Rollback completed"
}

# Cleanup function
cleanup() {
    log_warning "Cleaning up deployment..."
    kubectl delete namespace $NAMESPACE --ignore-not-found=true
    log_success "Cleanup completed"
}

# Main execution
case "${1:-deploy}" in
    deploy)
        log_info "Starting deployment of Fabric Management System"
        log_info "Namespace: $NAMESPACE"
        log_info "Deployment Type: $DEPLOYMENT_TYPE"

        check_prerequisites
        deploy_base
        deploy_infrastructure
        deploy_services
        deploy_gateway
        health_check
        show_status

        log_success "Deployment completed successfully!"
        ;;
    rollback)
        rollback
        ;;
    cleanup)
        cleanup
        ;;
    status)
        show_status
        ;;
    *)
        echo "Usage: $0 {deploy|rollback|cleanup|status}"
        echo "  deploy   - Deploy the entire application stack"
        echo "  rollback - Rollback to previous deployment"
        echo "  cleanup  - Remove all deployed resources"
        echo "  status   - Show current deployment status"
        exit 1
        ;;
esac
