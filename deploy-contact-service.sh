#!/bin/bash

# ==============================================
# CONTACT SERVICE DEPLOYMENT SCRIPT
# ==============================================

set -e

echo "🚀 Starting Contact Service Deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null; then
    print_error "docker-compose is not installed. Please install it and try again."
    exit 1
fi

# Check if .env file exists
if [[ ! -f ".env" ]]; then
    print_warning ".env file not found. Using default environment variables."
fi

# Check Google Maps API key
if grep -q "your_google_maps_api_key_here" .env 2>/dev/null; then
    print_warning "Google Maps API key not set in .env file. Address search functionality will not work."
    read -p "Do you want to continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_error "Deployment cancelled. Please set GOOGLE_MAPS_API_KEY in .env file."
        exit 1
    fi
fi

print_status "Building Contact Service..."

# Build only contact-service and its dependencies
docker-compose build contact-service

if [[ $? -ne 0 ]]; then
    print_error "Failed to build Contact Service"
    exit 1
fi

print_status "Starting infrastructure services (PostgreSQL, Redis, RabbitMQ)..."

# Start infrastructure services first
docker-compose up -d postgres-db redis rabbitmq

print_status "Waiting for infrastructure services to be healthy..."

# Wait for services to be healthy
max_attempts=30
attempt=0

while [[ $attempt -lt $max_attempts ]]; do
    if docker-compose ps postgres-db | grep -q "(healthy)"; then
        print_status "PostgreSQL is ready"
        break
    fi

    attempt=$((attempt + 1))
    echo "Waiting for PostgreSQL... ($attempt/$max_attempts)"
    sleep 2
done

if [[ $attempt -eq $max_attempts ]]; then
    print_error "PostgreSQL failed to start"
    exit 1
fi

print_status "Starting Contact Service..."

# Start contact-service
docker-compose up -d contact-service

print_status "Waiting for Contact Service to be ready..."

# Wait for contact-service to be healthy
attempt=0
while [[ $attempt -lt $max_attempts ]]; do
    if curl -f http://localhost:8082/actuator/health > /dev/null 2>&1; then
        print_status "Contact Service is ready!"
        break
    fi

    attempt=$((attempt + 1))
    echo "Waiting for Contact Service... ($attempt/$max_attempts)"
    sleep 3
done

if [[ $attempt -eq $max_attempts ]]; then
    print_error "Contact Service failed to start"
    print_error "Check logs with: docker-compose logs contact-service"
    exit 1
fi

print_status "✅ Contact Service deployed successfully!"
echo
echo "🌐 Contact Service is available at:"
echo "   - API: http://localhost:8082"
echo "   - Swagger UI: http://localhost:8082/swagger-ui.html"
echo "   - Health Check: http://localhost:8082/actuator/health"
echo
echo "📊 Other services:"
echo "   - PostgreSQL: localhost:5433"
echo "   - Redis: localhost:6379"
echo "   - RabbitMQ Management: http://localhost:15672"
echo
echo "📝 Useful commands:"
echo "   - View logs: docker-compose logs -f contact-service"
echo "   - Stop services: docker-compose stop"
echo "   - Remove services: docker-compose down"
echo "   - View all services: docker-compose ps"
echo
print_status "Deployment completed successfully! 🎉"
