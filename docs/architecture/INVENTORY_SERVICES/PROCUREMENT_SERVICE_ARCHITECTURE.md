# Procurement Service Architecture

## 🎯 Amaç

Satın alma süreçleri, tedarikçi yönetimi ve sipariş takibi işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Satın alma siparişlerinin yönetimi
- Tedarikçi bilgilerinin saklanması
- Sipariş onay süreçleri
- Teslimat takibi
- Fatura yönetimi
- Tedarikçi performans analizi

## 🔗 Diğer Servislerle İlişkiler

### Bağımlı Olduğu Servisler

- catalog-service: Kumaş bilgileri için
- pricing-service: Fiyat karşılaştırması için
- inventory-service: Stok durumu kontrolü için
- identity-service: Kullanıcı kimlik doğrulama ve yetkilendirme
- company-service: Şirket bilgileri ve multi-tenancy

### Bu Servisi Kullanan Servisler

- accounting-service: Fatura muhasebesi için
- quality-control-service: Teslim alınan malzeme kalite kontrolü için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- PurchaseOrder: Satın alma siparişi
- Supplier: Tedarikçi bilgileri
- PurchaseOrderItem: Sipariş kalemleri
- Delivery: Teslimat bilgileri
- Invoice: Fatura bilgileri
- SupplierPerformance: Tedarikçi performansı

## 🔄 Event'ler

### Yayınlanan Event'ler

- PurchaseOrderCreated: Sipariş oluşturulduğunda
- PurchaseOrderApproved: Sipariş onaylandığında
- DeliveryReceived: Teslimat alındığında
- InvoiceReceived: Fatura alındığında

### Dinlenen Event'ler

- StockLowAlert: Düşük stok uyarısında otomatik sipariş önerisi oluşturur
- QualityControlFailed: Kalite kontrol başarısız olduğunda tedarikçiye bildirim gönderir

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
