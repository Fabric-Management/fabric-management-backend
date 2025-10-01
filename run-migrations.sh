#!/bin/bash

# =============================================================================
# FABRIC MANAGEMENT SYSTEM - DATABASE MIGRATION SCRIPT
# =============================================================================
# Bu script tüm microservislerin migration dosyalarını çalıştırır

echo "🚀 Starting Fabric Management System Database Migration..."

# PostgreSQL container'ının hazır olmasını bekle
echo "⏳ Waiting for PostgreSQL to be ready..."
until docker exec fabric-postgres pg_isready -U fabric_user -d fabric_management; do
  echo "PostgreSQL is unavailable - sleeping"
  sleep 2
done

echo "✅ PostgreSQL is ready!"

# Migration dosyalarını çalıştır
echo "📦 Running Contact Service Migration..."
docker exec fabric-postgres psql -U fabric_user -d fabric_management -f /docker-entrypoint-initdb.d/V1__create_contact_tables.sql

echo "📦 Running Company Service Migration..."
# Company service migration dosyası varsa çalıştır
if [ -f "services/company-service/src/main/resources/db/migration/V1__create_company_tables.sql" ]; then
    docker exec fabric-postgres psql -U fabric_user -d fabric_management -f /docker-entrypoint-initdb.d/V1__create_company_tables.sql
fi

echo "✅ Database migration completed!"
echo "🔍 Checking created tables..."
docker exec fabric-postgres psql -U fabric_user -d fabric_management -c "\dt"

echo "🎉 Migration process finished successfully!"
