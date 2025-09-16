#!/bin/bash
echo "🧹 Cleaning common-core module..."

# Navigate to common-core directory
cd "$(dirname "$0")"

echo "📁 Current directory: $(pwd)"

# Remove duplicate BaseEntity files (keep only the one in domain/base/)
echo "🗑️  Removing duplicate BaseEntity files..."
find . -name "BaseEntity.java" -not -path "*/domain/base/*" -delete
echo "✅ Duplicate BaseEntity files removed"

# Remove duplicate BaseController files (keep only the one in infrastructure/web/BaseController.java)
echo "🗑️  Removing duplicate BaseController files..."
find . -name "BaseController.java" -not -path "*/infrastructure/web/BaseController.java" -delete
echo "✅ Duplicate BaseController files removed"

# Remove non-Java files from source directories
echo "🗑️  Removing non-Java files from source directories..."
find src/main/java -type f \( -name "*.md" -o -name "*.txt" -o -name "*.properties" -o -name "README*" -o -name "DELETED" -o -name "REMOVED_*" \) -delete
echo "✅ Non-Java files removed from source directories"

# Remove empty directories
echo "🗑️  Removing empty directories..."
find src/main/java -type d -empty -delete
echo "✅ Empty directories removed"

# Verify cleanup results
echo ""
echo "🔍 Verification Results:"
echo "📊 BaseEntity files found:"
find . -name "BaseEntity.java" -type f | wc -l | xargs echo "   Count:"
find . -name "BaseEntity.java" -type f | sed 's/^/   /'

echo "📊 BaseController files found:"
find . -name "BaseController.java" -type f | wc -l | xargs echo "   Count:"
find . -name "BaseController.java" -type f | sed 's/^/   /'

echo "📊 Non-Java files in source directories:"
non_java_count=$(find src/main/java -type f \( -name "*.md" -o -name "*.txt" -o -name "*.properties" -o -name "README*" -o -name "DELETED" -o -name "REMOVED_*" \) | wc -l)
echo "   Count: $non_java_count"
if [ "$non_java_count" -eq 0 ]; then
    echo "   ✅ No non-Java files found in source directories"
else
    echo "   ⚠️  Non-Java files still present:"
    find src/main/java -type f \( -name "*.md" -o -name "*.txt" -o -name "*.properties" -o -name "README*" -o -name "DELETED" -o -name "REMOVED_*" \) | sed 's/^/      /'
fi

echo ""
echo "🎯 Expected Results:"
echo "   ✅ Exactly 1 BaseEntity.java in domain/base/"
echo "   ✅ Exactly 1 BaseController.java in infrastructure/web/"
echo "   ✅ No non-Java files in src/main/java directories"
echo "   ✅ No empty directories"

echo ""
echo "🎉 Common-core module cleanup complete!"
echo "🔧 Next steps:"
echo "   1. Run: mvn clean compile"
echo "   2. Run: mvn clean package"
echo "   3. Verify CI/CD pipeline passes"
