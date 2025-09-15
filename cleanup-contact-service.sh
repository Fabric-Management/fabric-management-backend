#!/bin/bash
# Contact-Service Cleanup Script
# Bu boş klasörler kod yazmayacağımız için silinecek

cd "/Users/user/Coding/fabric-management/fabric-management-backend/services/contact-service"

echo "🧹 Contact-Service boş klasörlerini temizliyorum..."

# Boş geocoding provider klasörlerini sil
rm -rf "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/cache"
rm -rf "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/fallback"
rm -rf "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/google"
rm -rf "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/locationiq"

# Gereksiz dosyaları temizle
rm -f "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/REMOVED_EMPTY_FOLDERS.md"

# .DS_Store dosyalarını temizle
find . -name ".DS_Store" -delete

echo "✅ Temizlik tamamlandı!"
echo "🎯 Kalan geocoding yapısı:"
ls -la "src/main/java/com/fabricmanagement/contact/infrastructure/adapter/out/geocoding/"
