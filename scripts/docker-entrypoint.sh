#!/bin/sh
# =============================================================================
# FABRIC MANAGEMENT - DOCKER ENTRYPOINT SCRIPT
# =============================================================================
# Smart entrypoint with dependency checking and health monitoring

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "${GREEN}========================================${NC}"
echo "${GREEN}  Fabric Management Service Starting${NC}"
echo "${GREEN}========================================${NC}"

# =============================================================================
# WAIT FOR DEPENDENCIES
# =============================================================================

wait_for_service() {
    local host=$1
    local port=$2
    local service=$3
    local max_attempts=60
    local attempt=0
    
    echo "${YELLOW}⏳ Waiting for $service at $host:$port...${NC}"
    
    while [ $attempt -lt $max_attempts ]; do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo "${GREEN}✅ $service is ready!${NC}"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo "   Attempt $attempt/$max_attempts..."
        sleep 2
    done
    
    echo "${RED}❌ ERROR: $service is not available after $max_attempts attempts${NC}"
    return 1
}

# Wait for PostgreSQL
if [ -n "$POSTGRES_HOST" ]; then
    wait_for_service "${POSTGRES_HOST}" "${POSTGRES_PORT:-5432}" "PostgreSQL" || exit 1
fi

# Wait for Redis
if [ -n "$REDIS_HOST" ]; then
    wait_for_service "${REDIS_HOST}" "${REDIS_PORT:-6379}" "Redis" || exit 1
fi

# Wait for Kafka (if using Docker network)
if [ -n "$KAFKA_BOOTSTRAP_SERVERS" ] && [ "$SPRING_PROFILES_ACTIVE" = "docker" ]; then
    # Extract kafka host and port from bootstrap servers
    KAFKA_HOST=$(echo "$KAFKA_BOOTSTRAP_SERVERS" | cut -d: -f1)
    KAFKA_PORT=$(echo "$KAFKA_BOOTSTRAP_SERVERS" | cut -d: -f2)
    wait_for_service "$KAFKA_HOST" "$KAFKA_PORT" "Kafka" || exit 1
fi

# =============================================================================
# JVM CONFIGURATION
# =============================================================================

# Set JMX port based on service (align with documentation)
JMX_PORT="${JMX_PORT:-9010}"

# Set default JVM options if not provided
if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UseCompressedOops \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"
fi

# Add monitoring JVM options with configurable port
JAVA_OPTS="$JAVA_OPTS \
           -Dcom.sun.management.jmxremote \
           -Dcom.sun.management.jmxremote.authenticate=false \
           -Dcom.sun.management.jmxremote.ssl=false \
           -Dcom.sun.management.jmxremote.local.only=false \
           -Dcom.sun.management.jmxremote.port=${JMX_PORT} \
           -Dcom.sun.management.jmxremote.rmi.port=${JMX_PORT} \
           -Djava.rmi.server.hostname=localhost"

echo "${GREEN}📊 JVM Configuration:${NC}"
echo "   Memory: MaxRAMPercentage=75%, InitialRAMPercentage=50%"
echo "   GC: G1GC with 200ms max pause time"
echo "   JMX: Enabled on port 9010"

# =============================================================================
# APPLICATION INFO
# =============================================================================

echo ""
echo "${GREEN}🚀 Starting Application...${NC}"
echo "   Service: ${SPRING_APPLICATION_NAME:-Unknown}"
echo "   Profile: ${SPRING_PROFILES_ACTIVE:-default}"
echo "   Java Version: $(java -version 2>&1 | head -n 1)"
echo "   Available Memory: $(free -m 2>/dev/null | grep Mem | awk '{print $2}' || echo 'N/A')MB"
echo ""

# =============================================================================
# START APPLICATION
# =============================================================================

# Execute the main command
exec java $JAVA_OPTS -jar /app/app.jar "$@"

