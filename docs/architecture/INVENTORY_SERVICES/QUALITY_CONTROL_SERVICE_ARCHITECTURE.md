# Quality Control Service Architecture

## 🎯 Amaç

Kumaş kalite kontrolü, hata tespiti ve tedarikçi performans değerlendirmesi işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Kumaş kalite kontrol süreçleri
- Hata tespiti ve kategorilendirme
- Tedarikçi kalite performansı takibi
- Kalite standartları yönetimi
- Kalite raporları ve analizi
- Hata trend analizi

## 🔗 Diğer Servislerle İlişkiler

### Bağımlı Olduğu Servisler

- catalog-service: Kumaş bilgileri için
- procurement-service: Tedarikçi bilgileri için
- identity-service: Kullanıcı kimlik doğrulama ve yetkilendirme
- company-service: Şirket bilgileri ve multi-tenancy

### Bu Servisi Kullanan Servisler

- inventory-service: Kalite kontrol sonrası stok güncellemesi için
- procurement-service: Tedarikçi performans değerlendirmesi için
- accounting-service: Kalite maliyetleri için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- QualityControl: Kalite kontrol ana entity'si
- QualityDefect: Hata tanımları
- QualityStandard: Kalite standartları
- QualityTest: Kalite test sonuçları
- SupplierQualityScore: Tedarikçi kalite skoru
- QualityReport: Kalite raporları

## 🔄 Event'ler

### Yayınlanan Event'ler

- QualityControlCompleted: Kalite kontrol tamamlandığında
- QualityDefectDetected: Hata tespit edildiğinde
- SupplierQualityScoreUpdated: Tedarikçi kalite skoru güncellendiğinde
- QualityStandardChanged: Kalite standardı değiştiğinde

### Dinlenen Event'ler

- DeliveryReceived: Teslimat alındığında kalite kontrol süreci başlatır
- PurchaseOrderCreated: Sipariş oluşturulduğunda kalite standartlarını kontrol eder

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
