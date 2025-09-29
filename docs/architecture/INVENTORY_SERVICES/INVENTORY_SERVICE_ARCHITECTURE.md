# Inventory Service Architecture

## 🎯 Amaç

Stok takibi, envanter yönetimi ve gerçek zamanlı stok durumu kontrolü işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Stok seviyelerinin takibi
- Envanter giriş/çıkış işlemleri
- Stok rezervasyonları
- Stok uyarıları ve bildirimleri
- Stok raporları ve analizi
- Stok sayım işlemleri

## 🔗 Diğer Servislerle İlişkiler

### Bağımlı Olduğu Servisler

- catalog-service: Kumaş bilgileri için
- pricing-service: Stok değerleme için
- identity-service: Kullanıcı kimlik doğrulama ve yetkilendirme
- company-service: Şirket bilgileri ve multi-tenancy

### Bu Servisi Kullanan Servisler

- procurement-service: Stok durumu kontrolü için
- quality-control-service: Kalite kontrol sonrası stok güncellemesi için
- accounting-service: Stok değerleme için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- Inventory: Stok ana entity'si
- StockMovement: Stok hareketleri
- StockReservation: Stok rezervasyonları
- StockAlert: Stok uyarıları
- StockCount: Stok sayım işlemleri
- InventoryReport: Stok raporları

## 🔄 Event'ler

### Yayınlanan Event'ler

- StockUpdated: Stok güncellendiğinde
- StockLowAlert: Stok seviyesi düşük olduğunda
- StockReserved: Stok rezerve edildiğinde
- StockCountCompleted: Stok sayımı tamamlandığında

### Dinlenen Event'ler

- FabricTypeCreated: Yeni kumaş tipi için stok kaydı oluşturur
- QualityControlCompleted: Kalite kontrol sonrası stok günceller
- ProcurementReceived: Satın alma teslim alındığında stok artırır

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
- Redis (stok cache için)
