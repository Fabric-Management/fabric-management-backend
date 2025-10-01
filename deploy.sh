#!/bin/bash

# =============================================================================
# FABRIC MANAGEMENT SYSTEM - DEPLOYMENT SCRIPT
# =============================================================================
# Bu script tüm sistemi Docker ile deploy eder

set -e  # Hata durumunda script'i durdur

echo "🚀 Starting Fabric Management System Deployment..."

# Mevcut container'ları durdur ve temizle
echo "🧹 Cleaning up existing containers..."
docker-compose -f docker-compose.yml down
docker-compose -f docker-compose-complete.yml down 2>/dev/null || true

# Infrastructure servislerini başlat
echo "🏗️ Starting infrastructure services..."
docker-compose -f docker-compose.yml up -d

# Servislerin hazır olmasını bekle
echo "⏳ Waiting for infrastructure services to be ready..."
sleep 30

# Migration'ları çalıştır
echo "📦 Running database migrations..."
chmod +x run-migrations.sh
./run-migrations.sh

# Microservisleri build et ve başlat
echo "🔨 Building and starting microservices..."
docker-compose -f docker-compose-complete.yml up -d --build

# Servislerin hazır olmasını bekle
echo "⏳ Waiting for microservices to be ready..."
sleep 60

# Health check
echo "🔍 Checking service health..."
echo "User Service:"
curl -f http://localhost:8081/api/v1/users/actuator/health || echo "❌ User Service not ready"

echo "Contact Service:"
curl -f http://localhost:8082/api/v1/contacts/actuator/health || echo "❌ Contact Service not ready"

echo "Company Service:"
curl -f http://localhost:8083/api/v1/companies/actuator/health || echo "❌ Company Service not ready"

echo "🎉 Deployment completed!"
echo "📊 Service Status:"
docker-compose -f docker-compose-complete.yml ps

echo ""
echo "🌐 Service URLs:"
echo "User Service: http://localhost:8081/api/v1/users"
echo "Contact Service: http://localhost:8082/api/v1/contacts"
echo "Company Service: http://localhost:8083/api/v1/companies"
echo ""
echo "📚 API Documentation:"
echo "User Service Swagger: http://localhost:8081/api/v1/users/swagger-ui.html"
echo "Contact Service Swagger: http://localhost:8082/api/v1/contacts/swagger-ui.html"
echo "Company Service Swagger: http://localhost:8083/api/v1/companies/swagger-ui.html"
