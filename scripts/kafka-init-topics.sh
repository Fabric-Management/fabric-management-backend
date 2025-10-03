#!/bin/bash
# =============================================================================
# KAFKA TOPIC INITIALIZATION SCRIPT
# =============================================================================
# Creates required Kafka topics for Fabric Management System
# Usage: Run after Kafka is ready

echo "🚀 Creating Kafka topics..."

# Wait for Kafka to be ready
echo "⏳ Waiting for Kafka..."
sleep 10

# Create topics
kafka-topics --create --if-not-exists --topic company-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 \
  --config segment.ms=86400000

kafka-topics --create --if-not-exists --topic contact.created \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --topic contact.verified \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --topic contact.deleted \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --create --if-not-exists --topic user-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

echo "✅ Kafka topics created successfully!"
echo ""
echo "📋 Topic list:"
kafka-topics --list --bootstrap-server localhost:9092
