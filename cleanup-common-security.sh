#!/bin/bash

# Cleanup script for common-security module refactoring
# Remove unnecessary components that don't belong in a shared security module

BASE_DIR="/Users/user/Coding/fabric-management/fabric-management-backend/common/common-security"

echo "🧹 Cleaning up common-security module..."

# Remove domain models (belong in auth-service/user-service)
echo "❌ Removing model directory..."
rm -rf "$BASE_DIR/src/main/java/com/fabricmanagement/common/security/model"

# Remove runtime configurations (belong in individual services)
echo "❌ Removing resources directory..."
rm -rf "$BASE_DIR/src/main/resources"

echo "✅ Cleanup completed!"
echo ""
echo "📂 Remaining structure should contain only:"
echo "   ├── config/ (SecurityAutoConfiguration.java, JwtProperties.java)"
echo "   ├── context/ (SecurityContextUtil.java)"
echo "   ├── exception/ (JWT exceptions)"
echo "   └── jwt/ (JWT infrastructure)"
echo ""
echo "🔍 Verifying remaining files..."
find "$BASE_DIR/src/main/java" -name "*.java" | sort
