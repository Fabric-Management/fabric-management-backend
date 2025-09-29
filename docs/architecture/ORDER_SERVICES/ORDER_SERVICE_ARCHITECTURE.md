# Order Service Architecture

## 🎯 Amaç

Sipariş yönetimi, satış/satın alma siparişleri ve basit fatura işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Sales Order (Satış siparişi) yönetimi
- Purchase Order (Satın alma siparişi) yönetimi
- Sevkiyat fişi oluşturma
- Basit fatura/irsaliye kayıtları
- Sipariş durumu takibi
- Sipariş onay süreçleri

## 🔗 Diğer Servislerle İlişkiler

### Çağırdığı Servisler

- **catalog-service**: Kumaş bilgileri için
- **pricing-service**: Fiyat hesaplamaları için
- **inventory-service**: Stok durumu kontrolü için
- **identity-service**: Kullanıcı kimlik doğrulama için
- **company-service**: Şirket bilgileri için

### Çağıran Servisler

- **logistics-service**: Sevkiyat fişi oluşturulduğunda
- **accounting-service**: Fatura bilgileri için (gelecekte)

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- SalesOrder: Satış siparişi ana entity'si
- PurchaseOrder: Satın alma siparişi ana entity'si
- OrderItem: Sipariş kalemleri
- DeliveryNote: Sevkiyat fişi
- SimpleInvoice: Basit fatura kaydı
- OrderStatus: Sipariş durumu takibi

## 🔄 Event'ler

### Yayınlar

- OrderCreated: Sipariş oluşturulduğunda
- OrderApproved: Sipariş onaylandığında
- DeliveryNoteCreated: Sevkiyat fişi oluşturulduğunda
- InvoiceGenerated: Fatura oluşturulduğunda

### Dinler

- StockReserved: Stok rezerve edildiğinde sipariş durumunu günceller
- PaymentReceived: Ödeme alındığında sipariş durumunu günceller

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign

## 📝 Notlar

**Önemli Noktalar:**

- Purchase Order (satın alma) ve Sales Order (satış) ayrımı yapılır
- Sevkiyat fişi oluşturma bu serviste gerçekleşir
- Basit fatura/irsaliye kayıtları tutulur
- İleri aşamada accounting-service'e taşınacak detaylı muhasebe işlemleri
