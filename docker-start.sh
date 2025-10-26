#!/bin/bash

# Load environment variables
set -a
source .env
set +a

echo "🚀 Starting Fabric Management Infrastructure..."
echo ""

# Start only essential services (PostgreSQL & Redis)
docker-compose up -d postgres redis

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 5

# Check service status
docker-compose ps

echo ""
echo "✅ Infrastructure started!"
echo ""
echo "📊 Service URLs:"
echo "  - PostgreSQL: localhost:5433"
echo "  - Redis: localhost:6379"
echo ""
echo "🔍 Check logs: docker-compose logs -f"
echo "🛑 Stop services: docker-compose down"
