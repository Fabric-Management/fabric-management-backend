# Catalog Service Architecture

## 🎯 Amaç

Kumaş tiplerinin katalog yönetimi, kategorilendirme ve özellik tanımlama işlemlerini gerçekleştiren servis.

## 📋 Sorumluluklar

- Kumaş tiplerinin CRUD işlemleri
- Kumaş kategorilerinin yönetimi
- Kumaş özelliklerinin tanımlanması
- Kumaş spesifikasyonlarının saklanması
- Kumaş görsellerinin yönetimi
- Kumaş arama ve filtreleme

## 🔗 Diğer Servislerle İlişkiler

### Bağımlı Olduğu Servisler

- identity-service: Kullanıcı kimlik doğrulama ve yetkilendirme
- company-service: Şirket bilgileri ve multi-tenancy

### Bu Servisi Kullanan Servisler

- pricing-service: Fiyatlandırma için kumaş bilgileri
- inventory-service: Stok takibi için kumaş katalog bilgileri
- procurement-service: Satın alma için kumaş spesifikasyonları
- quality-control-service: Kalite kontrol için kumaş özellikleri

## 📦 Common Module Kullanımı

- common-core: BaseEntity, ApiResponse, GlobalExceptionHandler, Common Exceptions
- common-security: SecurityContextUtil, JwtTokenProvider

## 🗂️ Domain Model

- FabricType: Kumaş tipi ana entity'si
- FabricCategory: Kumaş kategorisi
- FabricSpecification: Kumaş teknik özellikleri
- FabricImage: Kumaş görselleri
- FabricAttribute: Kumaş özellik tanımları

## 🔄 Event'ler

### Yayınlanan Event'ler

- FabricTypeCreated: Yeni kumaş tipi oluşturulduğunda
- FabricTypeUpdated: Kumaş tipi güncellendiğinde
- FabricTypeDeleted: Kumaş tipi silindiğinde

### Dinlenen Event'ler

- CompanyCreated: Yeni şirket oluşturulduğunda varsayılan kategoriler oluşturur

## 📊 Teknoloji Stack

- Spring Boot
- PostgreSQL
- Spring Data JPA
- Spring Cloud OpenFeign
