# Common Modules Overview

## 📋 Overview

Common modules, Fabric Management System'de tüm microservice'ler tarafından kullanılan ortak bileşenleri içerir. Minimalist yaklaşım benimsenerek, sadece gerçekten gerekli bileşenler dahil edilmiştir.

## 🎯 Common Modules

### **common-core**

- [BaseEntity](common-core/BASE_ENTITY.md) - Temel entity sınıfı
- [ApiResponse](common-core/API_RESPONSE.md) - Standart API response formatı
- [GlobalExceptionHandler](common-core/GLOBAL_EXCEPTION_HANDLER.md) - Merkezi hata yönetimi
- [Common Exceptions](common-core/COMMON_EXCEPTIONS.md) - Ortak exception sınıfları

### **common-security**

- [JwtTokenProvider](common-security/JWT_TOKEN_PROVIDER.md) - JWT token yönetimi
- [SecurityContextUtil](common-security/SECURITY_CONTEXT_UTIL.md) - Güvenlik context yardımcıları
- [JwtAuthenticationFilter](common-security/JWT_AUTHENTICATION_FILTER.md) - JWT authentication filter

## 🔧 Usage Guidelines

### **✅ KEEP - Essential Components**

- **BaseEntity**: Her entity'de gerçekten var, audit trail, soft delete
- **ApiResponse**: Response standardizasyonu, frontend tutarlılığı
- **GlobalExceptionHandler**: Tek yerden hata yönetimi, tutarlı error response
- **Common Exceptions**: Tutarlı exception handling, error code standardı

### **❌ REMOVE - Over-Engineering Components**

- **BaseController**: Generic CRUD kısıtlaması
- **BaseService**: Business logic kısıtlaması
- **BaseRepository**: JPA zaten sağlıyor
- **BaseDto**: Inheritance karmaşıklığı

## 📦 Maven Dependencies

```xml
<!-- Minimalist common-core -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>common-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Essential security -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>common-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 🎯 Benefits

1. **Test edilebilirlik**: Mock'lamak kolay, unit test'ler basit
2. **Flexibility**: Service'ler kendi ihtiyaçlarına göre geliştirilebilir
3. **Maintainability**: Kod daha anlaşılır, debug etmek kolay
4. **Performance**: Overhead yok, compile time optimizasyonları
5. **Code Consistency**: Tutarlı kod yapısı
6. **Reduced Boilerplate**: Tekrarlayan kod azalması
