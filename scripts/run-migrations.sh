#!/bin/bash

# =============================================================================
# DATABASE MIGRATION SCRIPT
# =============================================================================
# Runs Flyway migrations for all services
# Usage: ./scripts/run-migrations.sh [service-name]
# =============================================================================

set -euo pipefail

# Load .env file if exists (for local development)
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-fabric_management}"
DB_USER="${DB_USER:-fabric_user}"
DB_PASSWORD="${DB_PASSWORD:-fabric_password}"
SERVICE="${1:-all}"

# Functions
log_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check database connectivity
check_database() {
    log_info "Checking database connectivity..."
    
    if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c '\q' 2>/dev/null; then
        log_success "Database connection successful"
        return 0
    else
        log_error "Cannot connect to database"
        return 1
    fi
}

# Run migrations for a specific service
run_service_migration() {
    local service_name=$1
    local service_path="services/${service_name}"
    
    if [ ! -d "$service_path" ]; then
        log_error "Service directory not found: $service_path"
        return 1
    fi
    
    log_info "Running migrations for $service_name..."
    
    # Check if migration files exist
    if [ ! -d "${service_path}/src/main/resources/db/migration" ]; then
        log_info "No migrations found for $service_name, skipping..."
        return 0
    fi
    
    # Run Flyway migration using Maven
    cd "$service_path"
    
    if mvn flyway:migrate \
        -Dflyway.url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
        -Dflyway.user="$DB_USER" \
        -Dflyway.password="$DB_PASSWORD" \
        -Dflyway.schemas="${service_name//-/_}" \
        -Dflyway.table="flyway_schema_history_${service_name//-/_}"; then
        
        log_success "Migrations completed for $service_name"
    else
        log_error "Migration failed for $service_name"
        return 1
    fi
    
    cd - > /dev/null
}

# Check prerequisites
check_prerequisites() {
    local missing=0
    
    if ! command -v psql >/dev/null 2>&1; then
        log_error "psql not found. Please install PostgreSQL client."
        missing=1
    fi
    
    if ! command -v mvn >/dev/null 2>&1; then
        log_error "mvn not found. Please install Maven."
        missing=1
    fi
    
    return $missing
}

# Main execution
main() {
    echo "================================================"
    echo "  DATABASE MIGRATION RUNNER"
    echo "================================================"
    echo ""
    
    # Check prerequisites
    if ! check_prerequisites; then
        exit 1
    fi
    
    # Check database connectivity first
    if ! check_database; then
        exit 1
    fi
    
    # Run migrations based on parameter
    if [ "$SERVICE" == "all" ]; then
        # Dynamically discover services
        log_info "Discovering services..."
        local services=()
        for service_dir in services/*/pom.xml; do
            if [ -f "$service_dir" ]; then
                service_name=$(basename "$(dirname "$service_dir")")
                services+=("$service_name")
            fi
        done
        
        log_info "Found services: ${services[*]}"
        
        # Run for all discovered services
        for service in "${services[@]}"; do
            run_service_migration "$service"
        done
    else
        # Run for specific service
        run_service_migration "$SERVICE"
    fi
    
    echo ""
    log_success "All migrations completed successfully!"
}

# Run main function
main "$@"
