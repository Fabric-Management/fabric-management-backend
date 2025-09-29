# Pricing Service Architecture

## 🎯 Amaç

Kumaş fiyatlandırma politikaları, maliyet hesaplamaları ve fiyat yönetimi işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Kumaş fiyatlarının tanımlanması ve yönetimi
- Maliyet hesaplamaları
- Fiyat politikalarının uygulanması
- Fiyat geçmişinin takibi
- Toplu fiyat güncellemeleri
- Fiyat analizi ve raporlama

## 🔗 Diğer Servislerle İlişkiler

### Bağımlı Olduğu Servisler

- catalog-service: Kumaş bilgileri için
- identity-service: Kullanıcı kimlik doğrulama ve yetkilendirme
- company-service: Şirket bilgileri ve multi-tenancy

### Bu Servisi Kullanan Servisler

- procurement-service: Satın alma fiyat karşılaştırması için
- inventory-service: Stok değerleme için
- accounting-service: Maliyet muhasebesi için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- Price: Fiyat ana entity'si
- PricePolicy: Fiyat politikası kuralları
- CostCalculation: Maliyet hesaplama
- PriceHistory: Fiyat geçmişi
- PriceRule: Fiyat kural tanımları

## 🔄 Event'ler

### Yayınlanan Event'ler

- PriceUpdated: Fiyat güncellendiğinde
- PricePolicyChanged: Fiyat politikası değiştiğinde
- CostCalculated: Maliyet hesaplandığında

### Dinlenen Event'ler

- FabricTypeCreated: Yeni kumaş tipi için varsayılan fiyat oluşturur
- FabricTypeUpdated: Kumaş tipi güncellendiğinde fiyatları kontrol eder

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
