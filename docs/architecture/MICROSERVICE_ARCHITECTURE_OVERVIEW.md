# Fabric Management System - Microservice Architecture Overview

## 📋 Overview

Bu dokümantasyon, Fabric Management System'deki 24 microservice'in detaylı mimarisini, sorumluluklarını ve birbirleriyle olan ilişkilerini açıklar.

## 🏗️ Complete Microservice Architecture

### **Core Services (4 Services)**

#### 1. **Identity Service** (Port: 8081) ✅

- **Status**: Dokümantasyon tamamlandı
- **Dependencies**: Notification Service, Reporting Service
- **Dependents**: Tüm servisler (authentication için)

#### 2. **User Service** (Port: 8082) ✅

- **Status**: Dokümantasyon tamamlandı
- **Dependencies**: Identity Service, Contact Service, Notification Service
- **Dependents**: HR Service, Performance Service, Reporting Service

#### 3. **Contact Service** (Port: 8083) ✅

- **Status**: Dokümantasyon tamamlandı
- **Dependencies**: User Service, Company Service, Notification Service
- **Dependents**: Company Service, HR Service, Procurement Service

#### 4. **Company Service** (Port: 8084) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Contact Service
- **Dependents**: HR Service, Warehouse Service, Procurement Service, Accounting Service, Quality Control Service

### **HR Management Services (4 Services)**

#### 5. **HR Service** (Port: 8085) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, User Service, Company Service, Contact Service
- **Dependents**: Payroll Service, Leave Service, Performance Service

#### 6. **Payroll Service** (Port: 8086) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: HR Service, Accounting Service, Notification Service
- **Dependents**: Accounting Service, Reporting Service

#### 7. **Leave Service** (Port: 8087) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: HR Service, User Service, Notification Service
- **Dependents**: Reporting Service, Performance Service

#### 8. **Performance Service** (Port: 8088) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: HR Service, User Service, Leave Service
- **Dependents**: Reporting Service, AI Service

### **Inventory Management Services (5 Services)**

#### 9. **Inventory Service** (Port: 8089) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service
- **Dependents**: Order Service, Production Service, Accounting Service

#### 10. **Catalog Service** (Port: 8090) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service
- **Dependents**: Order Service, Pricing Service

#### 11. **Pricing Service** (Port: 8091) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service, Catalog Service
- **Dependents**: Order Service, Accounting Service

#### 12. **Procurement Service** (Port: 8092) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Company Service, Contact Service, Inventory Service
- **Dependents**: Accounting Service, Invoice Service, Quality Control Service

#### 13. **Quality Control Service** (Port: 8093) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Inventory Service, Company Service
- **Dependents**: AI Service, Reporting Service, Notification Service

### **Order Management Services (1 Service)**

#### 14. **Order Service** (Port: 8094) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service, Inventory Service, Catalog Service, Pricing Service
- **Dependents**: Logistics Service, Production Service, Accounting Service

### **Logistics Management Services (1 Service)**

#### 15. **Logistics Service** (Port: 8095) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service, Order Service
- **Dependents**: Production Service, Accounting Service, Reporting Service

### **Production Management Services (1 Service)**

#### 16. **Production Service** (Port: 8096) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service, Inventory Service, Order Service, Logistics Service
- **Dependents**: Accounting Service, Quality Control Service, Reporting Service

### **Financial Services (4 Services)**

#### 17. **Accounting Service** (Port: 8097) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Company Service, Inventory Service
- **Dependents**: Invoice Service, Payment Service, Billing Service, Payroll Service

#### 18. **Invoice Service** (Port: 8098) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Accounting Service, Procurement Service, Company Service
- **Dependents**: Payment Service, Billing Service, Reporting Service

#### 19. **Payment Service** (Port: 8099) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Invoice Service, Accounting Service, Notification Service
- **Dependents**: Billing Service, Reporting Service

#### 20. **Billing Service** (Port: 8100) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Accounting Service, Invoice Service, Payment Service
- **Dependents**: Reporting Service, Notification Service

### **AI & Analytics Services (3 Services)**

#### 21. **AI Service** (Port: 8101) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Performance Service, Quality Control Service
- **Dependents**: Reporting Service, Notification Service

#### 22. **Reporting Service** (Port: 8102) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, User Service, AI Service, Tüm business servisler
- **Dependents**: Notification Service

#### 23. **Notification Service** (Port: 8103) ❌

- **Status**: Dokümantasyon eksik
- **Dependencies**: Identity Service, Contact Service
- **Dependents**: Tüm servisler (notification için)

### **Quality Management Services (1 Service)**

#### 24. **Quality Control Service** (Port: 8104) ✅

- **Status**: Dokümantasyon tamamlandı
- **Dependencies**: Identity Service, Inventory Service, Company Service
- **Dependents**: AI Service, Reporting Service, Notification Service

## 🔗 Service Interaction Matrix

### **High-Level Service Dependencies**

```mermaid
graph TB
    subgraph "Core Layer"
        IS[Identity Service]
        US[User Service]
        CS[Contact Service]
        COS[Company Service]
    end

    subgraph "HR Layer"
        HRS[HR Service]
        PS[Payroll Service]
        LS[Leave Service]
        PMS[Performance Service]
    end

    subgraph "Inventory Layer"
        IS3[Inventory Service]
        CS2[Catalog Service]
        PS2[Pricing Service]
        PS3[Procurement Service]
        QCS[Quality Control Service]
    end

    subgraph "Order Layer"
        OS[Order Service]
    end

    subgraph "Logistics Layer"
        LS[Logistics Service]
    end

    subgraph "Production Layer"
        PS4[Production Service]
    end

    subgraph "Financial Layer"
        AS[Accounting Service]
        IS2[Invoice Service]
        PS5[Payment Service]
        BS[Billing Service]
    end

    subgraph "AI & Analytics Layer"
        AIS[AI Service]
        RS[Reporting Service]
        NS[Notification Service]
    end

    subgraph "Quality Layer"
        QCS[Quality Control Service]
    end

    %% Core dependencies
    US --> IS
    CS --> IS
    CS --> US
    COS --> IS
    COS --> CS

    %% HR dependencies
    HRS --> IS
    HRS --> US
    HRS --> COS
    HRS --> CS
    PS --> HRS
    PS --> AS
    PS --> NS
    LS --> HRS
    LS --> US
    LS --> NS
    PMS --> HRS
    PMS --> US
    PMS --> LS

    %% Inventory dependencies
    IS3 --> IS
    IS3 --> COS
    CS2 --> IS
    CS2 --> COS
    PS2 --> IS
    PS2 --> COS
    PS2 --> CS2
    PS3 --> COS
    PS3 --> CS
    PS3 --> IS3
    QCS --> IS
    QCS --> IS3
    QCS --> COS

    %% Order dependencies
    OS --> IS
    OS --> COS
    OS --> IS3
    OS --> CS2
    OS --> PS2

    %% Logistics dependencies
    LS --> IS
    LS --> COS
    LS --> OS

    %% Production dependencies
    PS4 --> IS
    PS4 --> COS
    PS4 --> IS3
    PS4 --> OS
    PS4 --> LS

    %% Financial dependencies
    AS --> IS
    AS --> COS
    AS --> IS3
    IS2 --> AS
    IS2 --> PS3
    IS2 --> COS
    PS5 --> IS2
    PS5 --> AS
    PS5 --> NS
    BS --> AS
    BS --> IS2
    BS --> PS5

    %% AI & Analytics dependencies
    AIS --> IS
    AIS --> PMS
    AIS --> QCS
    RS --> IS
    RS --> US
    RS --> AIS
    RS --> HRS
    RS --> AS
    RS --> IS3
    RS --> QCS
    NS --> IS
    NS --> CS

    %% Quality dependencies
    QCS --> IS
    QCS --> IS3
    QCS --> COS

    %% Cross-layer dependencies
    PS --> AS
    IS3 --> AS
    PS3 --> AS
    QCS --> AIS
    QCS --> RS
    QCS --> NS
```

## 📊 Detailed Service Relationships

### **1. Company Service (Port: 8084)**

**Sorumluluklar:**

- Şirket profil yönetimi
- Şirket ayarları ve konfigürasyonları
- Multi-tenant yönetimi
- Şirket hiyerarşisi

**İlişkiler:**

- **→ HR Service**: Şirket çalışanları için referans
- **→ Warehouse Service**: Şirket depoları için referans
- **→ Procurement Service**: Şirket tedarikçileri için referans
- **→ Accounting Service**: Şirket muhasebe bilgileri için referans
- **→ Quality Control Service**: Şirket kalite performansı için referans

### **2. HR Service (Port: 8085)**

**Sorumluluklar:**

- Çalışan yönetimi
- Departman yönetimi
- Pozisyon yönetimi
- HR politikaları

**İlişkiler:**

- **← Company Service**: Şirket bilgileri
- **← User Service**: Kullanıcı profil bilgileri
- **← Contact Service**: Çalışan iletişim bilgileri
- **→ Payroll Service**: Çalışan maaş bilgileri
- **→ Leave Service**: Çalışan izin bilgileri
- **→ Performance Service**: Çalışan performans bilgileri

### **3. Payroll Service (Port: 8086)**

**Sorumluluklar:**

- Maaş yönetimi
- Bordro işlemleri
- Vergi hesaplamaları
- Yan haklar yönetimi

**İlişkiler:**

- **← HR Service**: Çalışan bilgileri
- **← Accounting Service**: Muhasebe kayıtları
- **← Notification Service**: Bordro bildirimleri
- **→ Accounting Service**: Bordro muhasebe kayıtları
- **→ Reporting Service**: Bordro raporları

### **4. Leave Service (Port: 8087)**

**Sorumluluklar:**

- İzin talebi yönetimi
- İzin bakiye takibi
- Onay workflow'u
- Tatil günleri yönetimi

**İlişkiler:**

- **← HR Service**: Çalışan bilgileri
- **← User Service**: Kullanıcı bilgileri
- **← Notification Service**: İzin bildirimleri
- **→ Reporting Service**: İzin raporları
- **→ Performance Service**: İzin performans etkisi

### **5. Performance Service (Port: 8088)**

**Sorumluluklar:**

- Performans değerlendirmeleri
- Hedef belirleme
- KPI yönetimi
- Terfi yönetimi

**İlişkiler:**

- **← HR Service**: Çalışan bilgileri
- **← User Service**: Kullanıcı bilgileri
- **← Leave Service**: İzin performans etkisi
- **→ Reporting Service**: Performans raporları
- **→ AI Service**: Performans analizi

### **6. Warehouse Service (Port: 8089)**

**Sorumluluklar:**

- Depo yönetimi
- Lokasyon yönetimi
- Depo operasyonları
- Envanter takibi

**İlişkiler:**

- **← Company Service**: Şirket bilgileri
- **← Stock Service**: Stok bilgileri
- **→ Stock Service**: Depo stok bilgileri
- **→ Procurement Service**: Depo tedarik bilgileri
- **→ Quality Control Service**: Depo kalite kontrolü

### **7. Stock Service (Port: 8090)**

**Sorumluluklar:**

- Stok yönetimi
- Stok hareketleri
- Stok uyarıları
- Stok değerleme

**İlişkiler:**

- **← Warehouse Service**: Depo bilgileri
- **← Fabric Service**: Kumaş bilgileri
- **← Procurement Service**: Tedarik bilgileri
- **→ Warehouse Service**: Stok durumu
- **→ Procurement Service**: Stok ihtiyaçları
- **→ Accounting Service**: Stok muhasebe kayıtları

### **8. Fabric Service (Port: 8091)**

**Sorumluluklar:**

- Kumaş türü yönetimi
- Kumaş özellikleri
- Kalite kontrol
- Kumaş spesifikasyonları

**İlişkiler:**

- **← Company Service**: Şirket bilgileri
- **→ Stock Service**: Kumaş stok bilgileri
- **→ Procurement Service**: Kumaş tedarik bilgileri
- **→ Quality Control Service**: Kumaş kalite kontrolü
- **→ Accounting Service**: Kumaş maliyet bilgileri

### **9. Procurement Service (Port: 8092)**

**Sorumluluklar:**

- Satın alma siparişi yönetimi
- Tedarikçi yönetimi
- Tedarik workflow'u
- Tedarikçi değerlendirmesi

**İlişkiler:**

- **← Company Service**: Şirket bilgileri
- **← Contact Service**: Tedarikçi iletişim bilgileri
- **← Fabric Service**: Kumaş bilgileri
- **← Stock Service**: Stok ihtiyaçları
- **→ Accounting Service**: Tedarik muhasebe kayıtları
- **→ Invoice Service**: Tedarik faturaları
- **→ Quality Control Service**: Tedarik kalite kontrolü

### **10. Accounting Service (Port: 8093)**

**Sorumluluklar:**

- Genel muhasebe
- Hesap planı
- Yevmiye kayıtları
- Mali raporlar

**İlişkiler:**

- **← Identity Service**: Kullanıcı bilgileri
- **← Company Service**: Şirket bilgileri
- **← Fabric Service**: Kumaş maliyet bilgileri
- **→ Invoice Service**: Muhasebe kayıtları
- **→ Payment Service**: Ödeme muhasebe kayıtları
- **→ Billing Service**: Faturalama muhasebe kayıtları
- **→ Payroll Service**: Bordro muhasebe kayıtları

### **11. Invoice Service (Port: 8094)**

**Sorumluluklar:**

- Fatura oluşturma
- Fatura yönetimi
- Fatura onayı
- Fatura takibi

**İlişkiler:**

- **← Accounting Service**: Muhasebe kayıtları
- **← Procurement Service**: Tedarik faturaları
- **← Company Service**: Şirket bilgileri
- **→ Payment Service**: Fatura ödemeleri
- **→ Billing Service**: Faturalama işlemleri
- **→ Reporting Service**: Fatura raporları

### **12. Payment Service (Port: 8095)**

**Sorumluluklar:**

- Ödeme işleme
- Ödeme yöntemleri
- Ödeme gateway entegrasyonu
- Ödeme takibi

**İlişkiler:**

- **← Invoice Service**: Fatura ödemeleri
- **← Accounting Service**: Muhasebe kayıtları
- **← Notification Service**: Ödeme bildirimleri
- **→ Billing Service**: Ödeme faturalama
- **→ Reporting Service**: Ödeme raporları

### **13. Billing Service (Port: 8096)**

**Sorumluluklar:**

- Faturalama yönetimi
- Faturalama döngüleri
- Faturalama kuralları
- Faturalama otomasyonu

**İlişkiler:**

- **← Accounting Service**: Muhasebe kayıtları
- **← Invoice Service**: Faturalama işlemleri
- **← Payment Service**: Ödeme faturalama
- **→ Reporting Service**: Faturalama raporları
- **→ Notification Service**: Faturalama bildirimleri

### **14. AI Service (Port: 8097)**

**Sorumluluklar:**

- ChatGPT entegrasyonu
- AI destekli analitik
- Tahmine dayalı analitik
- Makine öğrenmesi modelleri

**İlişkiler:**

- **← Identity Service**: Kullanıcı bilgileri
- **← Performance Service**: Performans analizi
- **← Quality Control Service**: Kalite analizi
- **→ Reporting Service**: AI raporları
- **→ Notification Service**: AI bildirimleri

### **15. Reporting Service (Port: 8098)**

**Sorumluluklar:**

- Rapor oluşturma
- Dashboard yönetimi
- Veri görselleştirme
- Özel raporlar

**İlişkiler:**

- **← Identity Service**: Kullanıcı bilgileri
- **← User Service**: Kullanıcı profil bilgileri
- **← AI Service**: AI raporları
- **← HR Service**: HR raporları
- **← Accounting Service**: Mali raporlar
- **← Warehouse Service**: Depo raporları
- **← Quality Control Service**: Kalite raporları
- **→ Notification Service**: Rapor bildirimleri

### **16. Notification Service (Port: 8099)**

**Sorumluluklar:**

- Email bildirimleri
- SMS bildirimleri
- Push bildirimleri
- Bildirim şablonları

**İlişkiler:**

- **← Identity Service**: Kullanıcı bilgileri
- **← Contact Service**: İletişim bilgileri
- **→ Tüm servisler**: Bildirim gönderimi

## 🎯 Implementation Priority

### **Phase 1: Core Services** (Tamamlandı ✅)

1. Identity Service
2. User Service
3. Contact Service
4. Company Service (Eksik ❌)

### **Phase 2: HR Management** (Eksik ❌)

5. HR Service
6. Payroll Service
7. Leave Service
8. Performance Service

### **Phase 3: Inventory Management** (Eksik ❌)

9. Inventory Service
10. Catalog Service
11. Pricing Service
12. Procurement Service
13. Quality Control Service

### **Phase 4: Order & Logistics Services** (Eksik ❌)

14. Order Service
15. Logistics Service

### **Phase 5: Production Services** (Eksik ❌)

16. Production Service

### **Phase 6: Financial Services** (Eksik ❌)

17. Accounting Service
18. Invoice Service
19. Payment Service
20. Billing Service

### **Phase 7: AI & Analytics** (Eksik ❌)

21. AI Service
22. Reporting Service
23. Notification Service

### **Phase 8: Quality Management** (Tamamlandı ✅)

24. Quality Control Service

## 📈 Business Value Matrix

| Service Category      | Business Impact | Technical Complexity | Priority |
| --------------------- | --------------- | -------------------- | -------- |
| Core Services         | High            | Medium               | High     |
| HR Management         | High            | Medium               | High     |
| Inventory Management  | High            | High                 | High     |
| Order Management      | High            | Medium               | High     |
| Logistics Management  | High            | Medium               | High     |
| Production Management | High            | High                 | High     |
| Financial Services    | High            | High                 | High     |
| AI & Analytics        | Medium          | High                 | Medium   |
| Quality Management    | High            | Medium               | High     |

Bu dokümantasyon, kalan 19 microservice'in detaylı mimarisini ve birbirleriyle olan ilişkilerini gösterir. Her servis için sorumluluklar, bağımlılıklar ve etkileşimler net bir şekilde tanımlanmıştır.
