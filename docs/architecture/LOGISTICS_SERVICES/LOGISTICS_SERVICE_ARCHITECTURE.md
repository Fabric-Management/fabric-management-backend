# Logistics Service Architecture

## 🎯 Amaç

Fiziksel sevkiyat takibi, kargo firması entegrasyonu ve lojistik operasyonları yöneten servis.

## 📋 Sorumluluklar

- Inbound (gelen) sevkiyat takibi
- Outbound (giden) sevkiyat takibi
- Kargo firması entegrasyonu
- Sevkiyat durumu güncellemeleri
- Teslimat bildirimleri
- Lojistik raporları

## 🔗 Diğer Servislerle İlişkiler

### Çağırdığı Servisler

- **order-service**: Sevkiyat fişi bilgileri için
- **identity-service**: Kullanıcı kimlik doğrulama için
- **company-service**: Şirket bilgileri için

### Çağıran Servisler

- **order-service**: Sevkiyat durumu güncellemeleri için
- **inventory-service**: Teslimat sonrası stok güncellemesi için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- Shipment: Sevkiyat ana entity'si
- DeliveryRoute: Teslimat rotası
- Carrier: Kargo firması bilgileri
- TrackingInfo: Takip bilgileri
- DeliveryStatus: Teslimat durumu
- LogisticsReport: Lojistik raporları

## 🔄 Event'ler

### Yayınlar

- ShipmentCreated: Sevkiyat oluşturulduğunda
- ShipmentInTransit: Sevkiyat yolda olduğunda
- ShipmentDelivered: Sevkiyat teslim edildiğinde
- DeliveryFailed: Teslimat başarısız olduğunda

### Dinler

- DeliveryNoteCreated: Sevkiyat fişi oluşturulduğunda sevkiyat kaydı oluşturur
- OrderShipped: Sipariş sevk edildiğinde takip süreci başlatır

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
- External API Integration (Kargo firmaları)

## 📝 Notlar

**Önemli Noktalar:**

- Fiziksel sevkiyat takibi yapılır
- Kargo firması entegrasyonu sağlanır
- Inbound (gelen) vs Outbound (giden) sevkiyat ayrımı yapılır
- Gerçek zamanlı takip bilgileri sağlanır
