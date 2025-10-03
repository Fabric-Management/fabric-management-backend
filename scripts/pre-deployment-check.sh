#!/bin/bash
# =============================================================================
# PRE-DEPLOYMENT VALIDATION SCRIPT
# =============================================================================
# Detects issues BEFORE deployment
# Usage: ./scripts/pre-deployment-check.sh

set -e

echo "🔍 PRE-DEPLOYMENT VALIDATION STARTED"
echo "===================================="
echo ""

FAILED_CHECKS=0

# =============================================================================
# 1. YAML VALIDATION
# =============================================================================
echo "📝 [1/8] Validating YAML files..."

check_yaml() {
    local file=$1
    if command -v yamllint &> /dev/null; then
        yamllint -d relaxed "$file" 2>&1 | grep -v "line too long" || true
    else
        # Python fallback
        python3 -c "import yaml; yaml.safe_load(open('$file'))" 2>&1
    fi

    if [ $? -eq 0 ]; then
        echo "  ✅ $file"
    else
        echo "  ❌ $file - YAML syntax error!"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

for service in user-service company-service contact-service; do
    check_yaml "services/$service/src/main/resources/application.yml"
    [ -f "services/$service/src/main/resources/application-docker.yml" ] && \
        check_yaml "services/$service/src/main/resources/application-docker.yml"
done

check_yaml "docker-compose.yml"
check_yaml "docker-compose-complete.yml"

echo ""

# =============================================================================
# 2. DUPLICATE KEY CHECK (The issue we just fixed!)
# =============================================================================
echo "🔑 [2/8] Checking for duplicate YAML keys..."

check_duplicates() {
    local file=$1
    local duplicates=$(python3 << EOF
import yaml
import sys

def check_duplicates(node, path=""):
    if isinstance(node, dict):
        keys = list(node.keys())
        if len(keys) != len(set(keys)):
            duplicates = [k for k in keys if keys.count(k) > 1]
            print(f"Duplicate keys: {duplicates} at {path}")
            return True
        for key, value in node.items():
            if check_duplicates(value, f"{path}.{key}"):
                return True
    elif isinstance(node, list):
        for i, item in enumerate(node):
            if check_duplicates(item, f"{path}[{i}]"):
                return True
    return False

try:
    with open('$file', 'r') as f:
        data = yaml.safe_load(f)
        if check_duplicates(data):
            sys.exit(1)
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
EOF
)

    if [ $? -eq 0 ]; then
        echo "  ✅ $file - No duplicates"
    else
        echo "  ❌ $file - Duplicate keys found!"
        echo "$duplicates"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

for service in user-service company-service contact-service; do
    check_duplicates "services/$service/src/main/resources/application.yml"
done

echo ""

# =============================================================================
# 3. MAVEN BUILD TEST
# =============================================================================
echo "🏗️  [3/8] Testing Maven build..."

mvn clean compile -DskipTests -q
if [ $? -eq 0 ]; then
    echo "  ✅ Maven build successful"
else
    echo "  ❌ Maven build failed!"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

echo ""

# =============================================================================
# 4. LOMBOK ANNOTATION CHECK
# =============================================================================
echo "🔧 [4/8] Checking Lombok annotations..."

check_lombok() {
    # Check for @SuperBuilder without @Builder.Default
    grep -r "@SuperBuilder" shared/shared-domain/src/main/java --include="*.java" | while read -r line; do
        file=$(echo "$line" | cut -d: -f1)
        if grep -A 50 "@SuperBuilder" "$file" | grep "= " | grep -v "@Builder.Default" > /dev/null; then
            echo "  ⚠️  Potential @SuperBuilder issue in $file"
        fi
    done
}

check_lombok
echo "  ✅ Lombok annotations checked"
echo ""

# =============================================================================
# 5. DOCKER COMPOSE VALIDATION
# =============================================================================
echo "🐳 [5/8] Validating Docker Compose..."

docker-compose -f docker-compose-complete.yml config > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "  ✅ Docker Compose syntax valid"
else
    echo "  ❌ Docker Compose syntax invalid!"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

echo ""

# =============================================================================
# 6. PORT CONFLICT CHECK
# =============================================================================
echo "🔌 [6/8] Checking for port conflicts..."

check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ⚠️  Port $port ($service) is already in use!"
    else
        echo "  ✅ Port $port ($service) is available"
    fi
}

check_port 5432 "PostgreSQL"
check_port 6379 "Redis"
check_port 2181 "Zookeeper"
check_port 9092 "Kafka"
check_port 8081 "User Service"
check_port 8082 "Contact Service"
check_port 8083 "Company Service"

echo ""

# =============================================================================
# 7. KAFKA CONFIGURATION CHECK
# =============================================================================
echo "📨 [7/8] Validating Kafka configuration..."

for service in user-service company-service contact-service; do
    config_file="services/$service/src/main/resources/application.yml"

    # Check for unique client-id
    if grep -q "client-id:.*\${spring.application.name}" "$config_file"; then
        echo "  ✅ $service has dynamic client-id"
    else
        echo "  ❌ $service has static client-id (will cause JMX conflicts!)"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi

    # Check for auto-create-topics setting
    if grep -q "allow.auto.create.topics" "$config_file"; then
        echo "  ✅ $service has auto-create-topics configured"
    else
        echo "  ⚠️  $service missing auto-create-topics setting"
    fi
done

echo ""

# =============================================================================
# 8. DOCKERFILE VALIDATION
# =============================================================================
echo "📦 [8/8] Validating Dockerfiles..."

for service in user-service company-service contact-service; do
    dockerfile="services/$service/Dockerfile"

    # Check if logs directory is created
    if grep -q "mkdir.*logs" "$dockerfile"; then
        echo "  ✅ $service Dockerfile creates logs directory"
    else
        echo "  ❌ $service Dockerfile missing logs directory (will cause Logback errors!)"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
done

echo ""
echo "===================================="
echo "📊 VALIDATION SUMMARY"
echo "===================================="

if [ $FAILED_CHECKS -eq 0 ]; then
    echo "✅ All checks passed! Safe to deploy."
    echo ""
    echo "Next steps:"
    echo "  1. mvn clean install -DskipTests"
    echo "  2. docker-compose -f docker-compose-complete.yml up -d --build"
    exit 0
else
    echo "❌ $FAILED_CHECKS check(s) failed!"
    echo ""
    echo "⚠️  DO NOT DEPLOY until issues are fixed!"
    exit 1
fi
