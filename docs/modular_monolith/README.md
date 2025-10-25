# 🧩 FABRIC MANAGEMENT MODULAR MONOLITH DOCUMENTATION

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Module Protocols](#module-protocols)
4. [Communication Patterns](#communication-patterns)
5. [Security Policies](#security-policies)
6. [Testing Strategies](#testing-strategies)
7. [Deployment Guide](#deployment-guide)
8. [Quick Navigation](#quick-navigation)

---

## 🎯 OVERVIEW

Bu dokümantasyon, **Fabric Management Modular Monolith** mimarisinin detaylı rehberidir. Tek bir deploy edilebilir uygulama içinde, net domain sınırlarına sahip, bağımsız geliştirilebilir ve test edilebilir modüller sunar.

### **Temel Prensipler**

| Prensip                      | Açıklama                                               |
| ---------------------------- | ------------------------------------------------------ |
| **Single Deploy**            | Tek jar, tek deploy, ama her domain modül olarak izole |
| **Clean Boundaries**         | Her modül kendi sorumluluklarıyla sınırlı              |
| **In-Process Communication** | Domainler arası çağrılar in-process, düşük latency     |
| **Event-Driven**             | Domain event'leri ile asenkron iletişim                |
| **Multi-Tenant**             | Tenant bazlı izolasyon                                 |
| **Policy-Driven**            | Merkezi policy engine ile erişim kontrolü              |

### **Avantajlar**

✅ **Düşük Maliyet:** Tek deploy, tek runtime, düşük operasyonel maliyet  
✅ **Yüksek Performans:** In-process çağrılar, network overhead yok  
✅ **Kolay Geliştirme:** IDE içinde tek codebase, kolay debug  
✅ **Net Sınırlar:** Spring Modulith ile compile-time boundary kontrolü  
✅ **Kolay Test:** Testcontainers ile integration testler  
✅ **Ölçeklenebilir:** İleride gerekirse modüller microservice'e dönüştürülebilir

---

## 🏗️ ARCHITECTURE

Detaylı mimari bilgisi için: [ARCHITECTURE.md](./ARCHITECTURE.md)

### **Modül Yapısı**

```
fabric-management-backend/
├─ common/                 # TÜM CROSS-CUTTING CONCERNS
│  ├─ platform/           # İşletim altyapısı
│  │  ├─ auth/            # Authentication & Authorization
│  │  ├─ user/            # User Management
│  │  ├─ company/         # Company/Tenant Management
│  │  ├─ policy/          # Policy Engine
│  │  ├─ audit/           # Audit Logging
│  │  ├─ config/          # Configuration
│  │  ├─ monitoring/      # Health & Metrics
│  │  └─ communication/   # Notifications
│  ├─ infrastructure/     # Teknik altyapı
│  │  ├─ persistence/     # BaseEntity, AuditableEntity
│  │  ├─ events/          # DomainEvent, OutboxEvent
│  │  ├─ mapping/         # MapStruct
│  │  ├─ cqrs/            # Command, Query, Handler
│  │  ├─ web/             # ApiResponse, PagedResponse
│  │  └─ security/        # TenantFilter, JwtUtils
│  └─ util/               # Money, Unit, TimeHelper
├─ production/            # Business Domain
├─ logistics/             # Business Domain
├─ finance/               # Business Domain
├─ human/                 # Business Domain
├─ procurement/           # Business Domain
├─ integration/           # Business Domain
└─ insight/               # Business Domain
```

---

## 🔗 MODULE PROTOCOLS

Detaylı modül protokolleri için: [MODULE_PROTOCOLS.md](./MODULE_PROTOCOLS.md)

Her modül şu standartlara uyar:

- **Vertical Slice Architecture:** Her feature kendi controller/service/repository yapısına sahip
- **CQRS Pattern:** Command ve Query handler'lar ile ayrılmış sorumluluklar
- **Domain Events:** Domain içi değişiklikler event olarak publish edilir
- **Facade Pattern:** Cross-module iletişim sadece facade üzerinden
- **Repository Pattern:** Data access logic encapsulation

---

## 💬 COMMUNICATION PATTERNS

Detaylı iletişim desenleri için: [COMMUNICATION_PATTERNS.md](./COMMUNICATION_PATTERNS.md)

### **Modüller Arası İletişim**

| Pattern                  | Kullanım                       | Örnek                                         |
| ------------------------ | ------------------------------ | --------------------------------------------- |
| **Direct Call (Facade)** | Senkron, read-only             | `MaterialFacade.getMaterial()`                |
| **Domain Event**         | Asenkron, eventual consistency | `MaterialCreatedEvent`                        |
| **Outbox Pattern**       | Reliable event publishing      | Outbox tablo + scheduler                      |
| **CQRS**                 | Command vs Query separation    | `CreateMaterialCommand` vs `GetMaterialQuery` |

---

## 🔒 SECURITY POLICIES

Detaylı güvenlik politikaları için: [SECURITY_POLICIES.md](./SECURITY_POLICIES.md)

### **Güvenlik Katmanları**

1. **Authentication:** JWT/OAuth2 based (`common/platform/auth`)
2. **Authorization:** Policy-based access control (`common/platform/policy`)
3. **Multi-Tenancy:** Row-level security (`common/infrastructure/security`)
4. **Audit:** Comprehensive audit logging (`common/platform/audit`)
5. **Rate Limiting:** Request throttling (`common/infrastructure/web`)

---

## 🧪 TESTING STRATEGIES

Detaylı test stratejileri için: [TESTING_STRATEGIES.md](./TESTING_STRATEGIES.md)

### **Test Piramidi**

```
        /\
       /  \      E2E Tests (Az sayıda, yavaş)
      /────\
     /      \    Integration Tests (Orta sayıda, orta hız)
    /────────\
   /          \  Unit Tests (Çok sayıda, hızlı)
  /────────────\
```

---

## 🚀 DEPLOYMENT GUIDE

Detaylı deployment rehberi için: [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md)

### **Deployment Stratejisi**

- **Development:** Local Spring Boot application
- **Staging:** Docker container + PostgreSQL + Redis
- **Production:** Kubernetes deployment (Blue-Green)

---

## 🧭 QUICK NAVIGATION

### **Ana Dokümantasyon**

- [📊 PROJECT_PROGRESS.md](./PROJECT_PROGRESS.md) - Proje ilerleme takibi
- [📖 ARCHITECTURE.md](./ARCHITECTURE.md) - Genel mimari
- [🔐 IDENTITY_AND_SECURITY.md](./IDENTITY_AND_SECURITY.md) - Identity & Security model
- [📇 SUBSCRIPTION_INDEX.md](./SUBSCRIPTION_INDEX.md) - ⭐ **Subscription documentation index** (START HERE!)
- [💳 SUBSCRIPTION_MODEL.md](./SUBSCRIPTION_MODEL.md) - ⭐ **Feature-based subscription model** (main doc)
- [🚀 SUBSCRIPTION_QUICK_START.md](./SUBSCRIPTION_QUICK_START.md) - ⭐ **Subscription quick reference**
- [🛡️ POLICY_ENGINE.md](./POLICY_ENGINE.md) - Policy engine detayları
- [📋 MODULE_PROTOCOLS.md](./MODULE_PROTOCOLS.md) - Modül protokolleri
- [💬 COMMUNICATION_PATTERNS.md](./COMMUNICATION_PATTERNS.md) - İletişim desenleri
- [🔒 SECURITY_POLICIES.md](./SECURITY_POLICIES.md) - Güvenlik politikaları
- [🧪 TESTING_STRATEGIES.md](./TESTING_STRATEGIES.md) - Test stratejileri
- [🚀 DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment rehberi

### **Common Module Dokümantasyonu**

- [🔐 Auth Module](./common/platform/auth/AUTH_PROTOCOL.md)
- [👤 User Module](./common/platform/user/USER_PROTOCOL.md)
- [🏢 Company Module](./common/platform/company/COMPANY_PROTOCOL.md)
  - [💳 Subscription Management](./common/platform/company/SUBSCRIPTION.md) - ⭐ **Subscription implementation**
- [📜 Policy Module](./common/platform/policy/POLICY_PROTOCOL.md)
- [📊 Audit Module](./common/platform/audit/AUDIT_PROTOCOL.md)
- [⚙️ Config Module](./common/platform/config/CONFIG_PROTOCOL.md)
- [📈 Monitoring Module](./common/platform/monitoring/MONITORING_PROTOCOL.md)
- [💬 Communication Module](./common/platform/communication/COMMUNICATION_PROTOCOL.md)

### **Governance Domain** ⭐ NEW

- [🛡️ Governance Domain Overview](./governance/GOVERNANCE_DOMAIN_PROTOCOL.md)
  - [📋 Policy Registry](./governance/access/policy/POLICY_REGISTRY_PROTOCOL.md)
  - [✍️ Policy Review](./governance/access/review/POLICY_REVIEW_PROTOCOL.md)
  - [📊 Policy Audit](./governance/access/audit/POLICY_AUDIT_PROTOCOL.md)
  - [🔄 Policy Sync](./governance/access/sync/POLICY_SYNC_PROTOCOL.md)
  - [🔍 Access Review](./governance/compliance/review/ACCESS_REVIEW_PROTOCOL.md)
  - [⚠️ Anomaly Detection](./governance/compliance/anomaly/ANOMALY_DETECTION_PROTOCOL.md)

### **Operations Domain** ⭐ NEW - STRATEGIC CORE

- [🎯 Operations Domain Overview](./business/operations/OPERATIONS_PROTOCOL.md)
  - [📋 Job Management](./business/operations/job/JOB_PROTOCOL.md)
  - [👥 Assignment](./business/operations/assignment/ASSIGNMENT_PROTOCOL.md)
  - [⚙️ Workflow Engine](./business/operations/workflow/WORKFLOW_PROTOCOL.md)
  - [📊 Tracking](./business/operations/tracking/TRACKING_PROTOCOL.md)

### **Business Module Dokümantasyonu**

- [🏭 Production Module](./business/production/PRODUCTION_PROTOCOL.md)
- [📦 Logistics Module](./business/logistics/LOGISTICS_PROTOCOL.md)
- [💰 Finance Module](./business/finance/FINANCE_PROTOCOL.md)
- [👥 Human Module](./business/human/HUMAN_PROTOCOL.md)
- [🛒 Procurement Module](./business/procurement/PROCUREMENT_PROTOCOL.md)
- [🔗 Integration Module](./business/integration/INTEGRATION_PROTOCOL.md)
- [🧠 Insight Module](./business/insight/INSIGHT_PROTOCOL.md)

---

## 📞 SUPPORT

Sorular veya sorunlar için:

- **GitHub Issues:** [fabric-management-backend/issues](https://github.com/fabric-management/fabric-management-backend/issues)
- **Documentation:** [docs/](../README.md)
- **Development Protocol:** [FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md](../FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md)

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ **Composable Feature-Based Subscription Model**
