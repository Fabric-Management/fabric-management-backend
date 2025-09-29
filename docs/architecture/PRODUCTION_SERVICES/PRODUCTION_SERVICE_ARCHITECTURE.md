# Production Service Architecture

## 🎯 Amaç

Üretim yönetimi, üretim emirleri ve kumaş tüketimi işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Üretim emri yönetimi
- Kumaş tüketimi takibi
- Makine yönetimi
- Üretim planlaması
- Üretim raporları
- Kalite kontrol entegrasyonu

## 🔗 Diğer Servislerle İlişkiler

### Çağırdığı Servisler

- **catalog-service**: Kumaş bilgileri için
- **inventory-service**: Stok tüketimi için
- **quality-control-service**: Kalite kontrol için
- **identity-service**: Kullanıcı kimlik doğrulama için
- **company-service**: Şirket bilgileri için

### Çağıran Servisler

- **inventory-service**: Üretim sonrası stok artışı için
- **order-service**: Üretim tamamlandığında sipariş durumu güncellemesi için

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- ProductionOrder: Üretim emri ana entity'si
- ProductionLine: Üretim hattı
- Machine: Makine bilgileri
- MaterialConsumption: Malzeme tüketimi
- ProductionSchedule: Üretim planlaması
- ProductionReport: Üretim raporları

## 🔄 Event'ler

### Yayınlar

- ProductionOrderCreated: Üretim emri oluşturulduğunda
- ProductionStarted: Üretim başladığında
- ProductionCompleted: Üretim tamamlandığında
- MaterialConsumed: Malzeme tüketildiğinde

### Dinler

- OrderConfirmed: Sipariş onaylandığında üretim emri oluşturur
- QualityControlPassed: Kalite kontrol geçtiğinde üretimi tamamlar

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign

## 📝 Notlar

**Önemli Noktalar:**

- İleriye dönük - şimdilik pasif
- Üretim emri, kumaş tüketimi, makine yönetimi
- Gelecekte aktif hale getirilecek
- Şu anda temel yapı hazırlanmış durumda
