#!/bin/bash
# =============================================================================
# KAFKA TOPIC INITIALIZATION SCRIPT
# =============================================================================
# Creates required Kafka topics for Fabric Management System
# Usage: ./scripts/kafka-init-topics.sh
#
# Run this script AFTER Kafka container is running!

set -e  # Exit on error

# Kafka container name (adjust if different)
KAFKA_CONTAINER="fabric-kafka"

echo "🚀 Creating Kafka topics..."

# Check if Kafka container is running
if ! docker ps | grep -q "$KAFKA_CONTAINER"; then
    echo "❌ ERROR: Kafka container '$KAFKA_CONTAINER' is not running!"
    echo "   Start Kafka first: docker-compose up -d kafka"
    exit 1
fi

# Wait for Kafka to be ready
echo "⏳ Waiting for Kafka to be ready..."
sleep 5

echo ""
echo "📝 Creating topics..."

# Create company-events topic
echo "  → company-events"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic company-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 \
  --config segment.ms=86400000 2>/dev/null || true

# Create contact topics
echo "  → contact.created"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic contact.created \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 2>/dev/null || true

echo "  → contact.verified"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic contact.verified \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 2>/dev/null || true

echo "  → contact.deleted"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic contact.deleted \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 2>/dev/null || true

# Create user-events topic
echo "  → user-events"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic user-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 2>/dev/null || true

# Create policy audit topic (if needed)
echo "  → policy.audit"
docker exec $KAFKA_CONTAINER kafka-topics --create --if-not-exists --topic policy.audit \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 2>/dev/null || true

echo ""
echo "✅ Kafka topics created successfully!"
echo ""
echo "📋 All topics:"
docker exec $KAFKA_CONTAINER kafka-topics --list --bootstrap-server localhost:9092

echo ""
echo "📊 Topic details:"
docker exec $KAFKA_CONTAINER kafka-topics --describe --bootstrap-server localhost:9092
