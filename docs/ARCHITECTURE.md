# 🏗️ FABRIC MANAGEMENT - SYSTEM ARCHITECTURE

**Version:** 3.0  
**Status:** ✅ Production-Ready  
**Scope:** Modular Monolith Architecture with OS Subscription & Policy Engine  
**Last Updated:** 2025-01-27

---

## 🎯 ARCHITECTURE OVERVIEW

Fabric Management Platform uses a **Modular Monolith** architecture that combines the benefits of monolithic simplicity with modular design principles.

### Core Philosophy

- **Single Deploy** - One application, one database, one deployment
- **Modular Design** - Clear domain boundaries within monolith
- **In-Process Communication** - Zero network latency between modules
- **Event-Driven** - Asynchronous processing with Outbox pattern
- **Multi-Tenant** - Row-level security with tenant isolation

---

## 🏗️ MODULAR MONOLITH STRUCTURE

```
┌──────────────────────────────────────────────────────────┐
│                   FABRIC MANAGEMENT                       │
│                 (Modular Monolith Core)                  │
├──────────────────────────────────────────────────────────┤
│  common/         → Platform (Auth, User, Company, Policy, Audit, Config, Monitoring, Communication) + Infrastructure │
│  production/     → MasterData, Planning, Execution, Quality                     │
│  logistics/      → Inventory, Shipment, Customs                                 │
│  finance/        → Accounting, Costing, Billing                                 │
│  human/          → Employee, Payroll, Performance, Leave                        │
│  procurement/    → Supplier, Purchase, GRN, RFQ                                 │
│  integration/    → Adapters, Webhooks, Schedulers, Outbox                       │
│  insight/         → Analytics, Intelligence (AI, Forecasts)                     │
└──────────────────────────────────────────────────────────┘
```

---

## 🧱 MODULE DEPENDENCIES

| Module        | Allowed Dependencies                   | Purpose                  |
| ------------- | -------------------------------------- | ------------------------ |
| `common`      | none                                   | Platform foundation      |
| `production`  | common                                 | Manufacturing operations |
| `logistics`   | common, production                     | Supply chain management  |
| `finance`     | common, logistics, production          | Financial operations     |
| `human`       | common                                 | Human resources          |
| `procurement` | common, finance                        | Purchasing operations    |
| `integration` | common, production, logistics, finance | External integrations    |
| `insight`     | common, production, logistics, finance | Analytics & intelligence |

---

## 🔒 SECURITY ARCHITECTURE

### Authentication & Authorization

- **JWT-based Authentication** - Stateless token validation
- **Role-Based Access Control (RBAC)** - User roles and permissions
- **Policy-Based Authorization** - Fine-grained access control
- **Multi-Tenant Isolation** - Row-level security (RLS)

### Policy Engine (5-Layer Architecture)

Fabric Management Platform, **enterprise-grade policy engine** ile korunur:

**Policy Layers:**

1. **OS Subscription** - Tenant hangi OS'lere abone?
2. **Tenant** - Şirket seviyesi kurallar
3. **Company** - Departman ve hiyerarşi
4. **User** - Rol ve özel izinler
5. **Conditions** - Zaman, veri, iş kuralları

```java
@PolicyCheck(
    os = "YarnOS",
    resource = "fabric.yarn.create",
    action = "POST"
)
@PostMapping("/api/production/yarn")
public ResponseEntity<?> createYarn(@RequestBody YarnDto dto) {
    // Implementation
}
```

**Detaylı bilgi için:** [modular_monolith/POLICY_ENGINE.md](./modular_monolith/POLICY_ENGINE.md)

### ⭐ Composable Feature-Based Subscription Model

Platform, **esnek, özellik tabanlı** abonelik modeli kullanır:

#### **Subscription Architecture**

```
┌────────────────────────────────────────────┐
│  Layer 1: OS Subscription                  │
│  ────────────────────────                  │
│  Tenant YarnOS'a abone mi?                 │
├────────────────────────────────────────────┤
│  Layer 2: Feature Entitlement              │
│  ────────────────────────                  │
│  YarnOS'ta "yarn.blend" feature'ı var mı?  │
├────────────────────────────────────────────┤
│  Layer 3: Usage Quota                      │
│  ────────────────────────                  │
│  Fiber entity limiti aşıldı mı?            │
├────────────────────────────────────────────┤
│  Layer 4: RBAC/ABAC (Policy Engine)        │
│  ────────────────────────                  │
│  User yetkisi var mı?                      │
└────────────────────────────────────────────┘
```

#### **OS Catalog**

- **FabricOS** (Base) - $199/mo - ZORUNLU - Tüm tenantlar için
- **YarnOS, LoomOS, KnitOS, DyeOS** - $99-$149/mo - Production OS'lar
- **AnalyticsOS** - $149/mo - BI & Reporting
- **IntelligenceOS** - $299/mo - AI & ML
- **EdgeOS** - $199/mo - IoT & Sensors
- **AccountOS** - $79/mo - Full Accounting
- **CustomOS** - $399/mo - External Integrations

#### **Key Features**

- ✅ **String-Based Tiers** - Her OS'un kendi tier isimleri (Starter/Professional/Enterprise, Standard/Advanced)
- ✅ **JSONB Feature Storage** - Esnek feature entitlement
- ✅ **Usage Quotas** - API calls, users, storage, entity limits
- ✅ **Composable** - Kullanıcılar sadece ihtiyaç duydukları OS'ları alır

**Detaylı bilgi için:**

- [modular_monolith/SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md) - Kapsamlı dokümantasyon
- [modular_monolith/SUBSCRIPTION_QUICK_START.md](./modular_monolith/SUBSCRIPTION_QUICK_START.md) - Hızlı başlangıç

---

## 🗃️ DATA ARCHITECTURE

### Database Design

- **Single PostgreSQL Database** - ACID transactions across modules
- **Module-Prefixed Schemas** - Clear data boundaries
- **Row-Level Security (RLS)** - Tenant isolation at database level
- **Flyway Migrations** - Version-controlled schema evolution

### Schema Structure

```
-- Common Platform (Authentication, Authorization, Subscription)
common_auth, common_user, common_company, common_policy, common_audit
common_subscription, common_subscription_quota, common_feature_catalog
common_os_definition

-- Production Domain (Manufacturing)
prod_fiber, prod_yarn, prod_manufacturing

-- Logistics Domain (Supply Chain)
logi_inventory, logi_shipment

-- Finance Domain (Accounting & Billing)
fin_accounting, fin_billing

-- Human Domain (HR & Payroll)
hr_employee, hr_payroll

-- Procurement Domain (Purchasing)
proc_supplier, proc_purchase

-- Integration Domain (External Systems)
integ_outbox, integ_webhook

-- Insight Domain (Analytics & AI)
ins_analytics, ins_intelligence
```

**⭐ Subscription Tables:**

- `common_subscription` - OS subscriptions per tenant
- `common_subscription_quota` - Usage quotas (users, API calls, storage, entities)
- `common_feature_catalog` - Feature entitlement catalog
- `common_os_definition` - OS definitions & available tiers

---

## 🔄 COMMUNICATION PATTERNS

### In-Process Communication

- **Direct Method Calls** - Zero latency between modules
- **Facade Pattern** - Clean module interfaces
- **Event Publishing** - Asynchronous module communication
- **Shared Context** - Common security and tenant context

### Event-Driven Architecture

- **Domain Events** - Module-specific business events
- **Outbox Pattern** - Reliable event publishing
- **Kafka Integration** - External system communication
- **Event Sourcing** - Audit trail and state reconstruction

---

## 🚀 DEPLOYMENT ARCHITECTURE

### Single Application Deployment

- **One JAR File** - `fabric-management-backend.jar`
- **Single Port** - 8080 (main application)
- **Shared JVM** - All modules in same process
- **Blue-Green Deployment** - Zero-downtime updates

### Infrastructure Components

- **PostgreSQL** - Main database
- **Redis** - Caching and session storage
- **Kafka** - Event streaming (optional)
- **Prometheus/Grafana** - Monitoring and alerting

---

## 📊 MONITORING & OBSERVABILITY

### Health Checks

- **Spring Actuator** - `/actuator/health`
- **Module Health** - Individual module status
- **Database Health** - Connection and query performance
- **Cache Health** - Redis connectivity

### Metrics & Tracing

- **Micrometer** - Application metrics
- **OpenTelemetry** - Distributed tracing
- **Prometheus** - Metrics collection
- **Grafana** - Visualization and alerting

---

## 🔧 DEVELOPMENT ARCHITECTURE

### Module Structure

```
src/main/java/com/fabric/
├─ common/          # Shared utilities
├─ core/            # Platform capabilities
├─ production/      # Manufacturing
├─ logistics/       # Supply chain
├─ finance/         # Financial operations
├─ human/           # Human resources
├─ procurement/     # Purchasing
├─ integration/     # External systems
└─ insight/         # Analytics & AI
```

### Technology Stack

- **Spring Boot 3.2** - Application framework
- **Spring Modulith** - Module boundaries
- **PostgreSQL 15** - Database
- **Redis 7** - Caching
- **Kafka 3** - Event streaming
- **Docker** - Containerization
- **Kubernetes** - Orchestration

---

## 🎯 SCALING STRATEGY

### Horizontal Scaling

- **Multi-Instance Deployment** - Multiple application instances
- **Load Balancing** - Traffic distribution
- **Database Scaling** - Read replicas for analytics
- **Cache Clustering** - Redis cluster for high availability

### Vertical Scaling

- **JVM Tuning** - Memory and GC optimization
- **Database Optimization** - Query performance tuning
- **Connection Pooling** - Database connection management
- **Caching Strategy** - Multi-level caching

---

## 🔄 EVOLUTION STRATEGY

### Module Extraction

- **Independent Modules** - Clear boundaries enable extraction
- **API Facades** - Well-defined interfaces
- **Event Contracts** - Stable communication protocols
- **Database Separation** - Schema isolation

### Future Microservices

When needed, modules can be extracted to independent microservices:

- **Insight Module** - Analytics and AI services
- **Integration Module** - External system adapters
- **Production Module** - Manufacturing operations
- **Finance Module** - Financial operations

---

## ✅ ARCHITECTURE BENEFITS

### Operational Benefits

- **Simplified Deployment** - Single application deployment
- **Reduced Complexity** - No service mesh or API gateway needed
- **Lower Costs** - Minimal infrastructure overhead
- **Easier Debugging** - Single process, single logs

### Development Benefits

- **Faster Development** - No service-to-service communication
- **ACID Transactions** - Cross-module data consistency
- **Shared Context** - Common security and tenant context
- **Easier Testing** - Single application testing

### Business Benefits

- **Faster Time-to-Market** - Simplified development and deployment
- **Lower Operational Costs** - Reduced infrastructure complexity
- **Better Performance** - In-process communication
- **Easier Maintenance** - Single codebase to maintain

---

**Architecture Version:** 3.0  
**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ Composable Feature-Based Subscription Model
