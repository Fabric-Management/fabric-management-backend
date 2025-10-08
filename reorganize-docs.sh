#!/bin/bash
echo "🚀 Dokümantasyon reorganizasyonu başlatılıyor..."

# Yeni klasörler
mkdir -p docs/reports/2025-Q4/october
mkdir -p docs/{getting-started,operations,security,adr}

# Kök dizini temizle
git mv DOCKER_COMPOSE_FIXES_SUMMARY.md docs/reports/2025-Q4/october/docker-compose-fixes.md
git mv QUICK_FIXES_SUMMARY.md docs/reports/2025-Q4/october/quick-fixes-summary.md
git mv SECURITY_IMPROVEMENTS_OCTOBER_2025.md docs/reports/2025-Q4/october/security-improvements.md

# Sorunlu klasörü sil
rm -rf "docs/sorun cozme "

# Development dosyalarını düzelt
cd docs/development
for file in *.md; do
    lowercase=$(echo "$file" | tr '[:upper:]' '[:lower:]')
    if [ "$file" != "$lowercase" ]; then
        git mv "$file" "$lowercase"
    fi
done
cd ../..

echo "✅ Faz 1 tamamlandı!"
echo "📝 Commit yapın: git commit -m 'docs: reorganize structure to v3.0'"
