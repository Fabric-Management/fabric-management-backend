#!/bin/bash

# Load environment variables
set -a
source .env
set +a

echo "🚀 Starting Fabric Management Infrastructure..."
echo ""

# Start services (backend, PostgreSQL & Redis)
docker-compose up -d app postgres redis

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 5

# Check service status
docker-compose ps

echo ""
echo "✅ Infrastructure started!"
echo ""
echo "📊 Service URLs:"
echo "  - Backend: http://localhost:${APP_PORT:-8080}"
echo "  - PostgreSQL: localhost:${POSTGRES_PORT:-5432}"
echo "  - Redis: localhost:${REDIS_PORT:-6379}"
echo ""
echo "🔍 Check logs: docker-compose logs -f"
echo "🛑 Stop services: docker-compose down"
