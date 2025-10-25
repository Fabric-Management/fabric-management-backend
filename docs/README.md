# 📚 Fabric Management - Architecture & Standards

**Version:** 7.0 - Modular Monolith with OS Subscription & Policy Engine  
**Last Updated:** 2025-01-27  
**Purpose:** Core architecture documentation & development standards

---

## 🎯 Quick Start

| Role                    | Document                                                                                       | Description                              |
| ----------------------- | ---------------------------------------------------------------------------------------------- | ---------------------------------------- |
| **🌟 Global**           | [FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md](./FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md)       | Modular Monolith development standards   |
| **🧩 Modular Monolith** | [modular_monolith/README.md](./modular_monolith/README.md)                                     | Detailed modular monolith documentation  |
| **💳 Subscription**     | [modular_monolith/SUBSCRIPTION_INDEX.md](./modular_monolith/SUBSCRIPTION_INDEX.md)             | ⭐ **Subscription documentation index**  |
|                         | [modular_monolith/SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md)             | Feature-based subscription model         |
|                         | [modular_monolith/SUBSCRIPTION_QUICK_START.md](./modular_monolith/SUBSCRIPTION_QUICK_START.md) | Subscription quick reference             |
| **🛡️ Policy**           | [modular_monolith/POLICY_ENGINE.md](./modular_monolith/POLICY_ENGINE.md)                       | 5-layer policy engine (Enterprise-grade) |
| **📋 TODO**             | [TODO/README.md](./TODO/README.md)                                                             | Production migration & roadmap           |
| **🤖 AI**               | [AI_ASSISTANT_LEARNINGS.md](./AI_ASSISTANT_LEARNINGS.md)                                       | AI behavior & project philosophy         |
| **💎 Developer**        | [DEVELOPER_PROTOCOL.md](./DEVELOPER_PROTOCOL.md)                                               | Developer DNA manifesto (mandatory)      |
| **🏗️ Architect**        | [ARCHITECTURE.md](./ARCHITECTURE.md)                                                           | System architecture                      |
| **👥 Roles**            | [architecture/ROLES_QUICK_REFERENCE.md](./architecture/ROLES_QUICK_REFERENCE.md)               | System roles & permissions               |

---

## 📂 Documentation Structure

```
docs/
├── 🌟 FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md  # Modular Monolith development standards
├── 💎 DEVELOPER_PROTOCOL.md                      # Developer kişiliği, ahlakı, mantığı
├── 🤖 AI_ASSISTANT_LEARNINGS.md                  # AI coding principles
├── 🏗️ ARCHITECTURE.md                            # System architecture
├── 📖 DOCUMENTATION_PRINCIPLES.md                # Doc standards
├── 🧪 TESTING_PRINCIPLES.md                      # Testing standards
│
├── 🧩 modular_monolith/                  # Modular Monolith detailed documentation
│   ├── README.md                         # Main index
│   ├── PROJECT_PROGRESS.md               # Progress tracking
│   ├── ARCHITECTURE.md                   # Detailed architecture
│   ├── ⭐ SUBSCRIPTION_INDEX.md          # ⭐ **Subscription documentation index**
│   ├── SUBSCRIPTION_MODEL.md             # ⭐ Feature-based subscription model (main)
│   ├── SUBSCRIPTION_QUICK_START.md       # ⭐ Subscription quick reference
│   ├── POLICY_ENGINE.md                  # 5-layer policy engine
│   ├── MODULE_PROTOCOLS.md               # Module standards
│   ├── COMMUNICATION_PATTERNS.md         # Inter-module communication
│   ├── SECURITY_POLICIES.md              # Security guidelines
│   ├── TESTING_STRATEGIES.md             # Testing approach
│   ├── DEPLOYMENT_GUIDE.md               # Deployment procedures
│   ├── IDENTITY_AND_SECURITY.md          # Identity & security model
│   ├── GOVERNANCE_DOMAIN.md              # Governance domain overview
│   ├── OPERATIONS_DOMAIN.md              # Operations domain overview
│   ├── common/                           # Common module docs
│   │   ├── platform/                     # Platform modules (auth, user, company, etc.)
│   │   │   ├── company/                  # ⭐ Company & Subscription management
│   │   │   │   └── SUBSCRIPTION.md       # ⭐ Subscription implementation details
│   │   └── infrastructure/               # Infrastructure modules (persistence, events, etc.)
│   └── business/                         # Business module docs
│       ├── production/                   # Production module
│       ├── logistics/                    # Logistics module
│       ├── finance/                      # Finance module
│       ├── human/                        # Human module
│       ├── procurement/                  # Procurement module
│       ├── integration/                  # Integration module
│       └── insight/                      # Insight module
│
├── 📋 TODO/                              # Production migration & roadmap
│   ├── README.md                         # Overview & review schedule
│   └── PRODUCTION_SECURITY_MIGRATION.md  # JWT + mTLS migration plan
│
├── 📐 development/                       # Development standards
│   ├── principles.md                     # SOLID, DRY, NO USERNAME
│   ├── code_structure_guide.md           # Code organization
│   ├── ORCHESTRATION_PATTERN.md          # Atomic operations
│   ├── INTERNAL_ENDPOINT_PATTERN.md      # @InternalEndpoint pattern
│   ├── data_types_standards.md           # UUID standards
│   └── microservices_api_standards.md    # API standards
│
├── 🏛️ architecture/                      # Architecture patterns
│   ├── TENANT_MODEL_AND_ROLES_GUIDE.md   # Multi-tenancy model (detailed)
│   └── ROLES_QUICK_REFERENCE.md          # System roles (quick reference)
│
└── 🚀 deployment/                        # Deployment patterns
    ├── ENVIRONMENT_VARIABLES.md          # Config pattern
    └── DATABASE_MIGRATION_STRATEGY.md    # DB migration pattern
```

---

## 🔴 CRITICAL - Must Read

### Developer Onboarding (60 min)

1. **DEVELOPER_PROTOCOL.md** (20 min) - Developer DNA
2. **development/principles.md** (30 min) - SOLID, DRY, NO USERNAME
3. **development/data_types_standards.md** (10 min) - UUID mandatory

### Architecture Understanding (150 min)

1. **ARCHITECTURE.md** (30 min) - System overview
2. **modular_monolith/ARCHITECTURE.md** (30 min) - Modular monolith details
3. **modular_monolith/SUBSCRIPTION_INDEX.md** (5 min) - ⭐ **Subscription index** (start here!)
4. **modular_monolith/SUBSCRIPTION_MODEL.md** (40 min) - ⭐ Feature-based subscription model
5. **modular_monolith/SUBSCRIPTION_QUICK_START.md** (10 min) - ⭐ Subscription quick reference
6. **modular_monolith/POLICY_ENGINE.md** (35 min) - Policy engine (5-layer)

---

## ⚡ Sacred Rules

1. **ZERO HARDCODED** - Everything via ${ENV_VAR:default}
2. **PRODUCTION-READY** - No shortcuts, no temporary solutions
3. **HYBRID PATTERN** - Orchestration (core) + Choreography (side effects) + Parallel (validations)
4. **@InternalEndpoint** - Annotation over hardcoded paths
5. **EXTEND SHARED** - Base configs, zero boilerplate
6. **NO USERNAME** - contactValue (email/phone) for auth
7. **UUID TYPE SAFETY** - UUID everywhere internally
8. **ASYNC KAFKA** - CompletableFuture mandatory
9. **CHECK EXISTING** - Before creating new
10. **MINIMAL COMMENTS** - Self-documenting code

---

**Manifesto:**  
Production-ready or nothing. Enterprise-grade or nothing. Excellence or nothing.

**Why?**  
This project is our everything. This code is our legacy. This system is our craftsmanship.

---

## 🏆 PLATFORM HIGHLIGHTS

### **Enterprise-Grade Features**

| Feature             | Level            | Equivalent                |
| ------------------- | ---------------- | ------------------------- |
| **Policy Engine**   | Enterprise-Grade | AWS IAM, Google Zanzibar  |
| **OS Subscription** | Enterprise-Grade | Stripe Billing, Chargebee |
| **Multi-Tenancy**   | Enterprise-Grade | Salesforce, HubSpot       |
| **Performance**     | Enterprise-Grade | < 10ms policy decisions   |

### **5-Layer Policy Architecture**

```
Layer 1: OS Subscription    → YarnOS var mı? ACTIVE mi?
Layer 2: Tenant             → Blacklist'te mi? Limit aşıldı mı?
Layer 3: Company            → Departman uygun mu? Fason agreement var mı?
Layer 4: User               → Role uygun mu? Permission var mı?
Layer 5: Conditions         → Zaman uygun mu? Field conditions OK mi?

Decision: ALLOW veya DENY (< 10ms)
Cache: Redis 5 min TTL
Audit: Her decision loglanır
```

### **⭐ Composable Feature-Based Subscription Model**

#### **FabricOS (Base Platform)** — **ZORUNLU** ($199/mo)

- **Tier:** Base (tek tier)
- **İçerik:** auth, user, policy, audit, company, monitoring
- **Kısıtlı Modüller:** logistics/inventory, finance, human, procurement, production/planning
- **Hedef:** Tüm tenantlar için temel platform

#### **Optional OS Add-ons** — **COMPOSABLE**

| OS                 | Tier'lar                         | Başlangıç | Açıklama                     |
| ------------------ | -------------------------------- | --------- | ---------------------------- |
| **YarnOS**         | Starter/Professional/Enterprise  | $99/mo    | İplik üretimi (fiber + yarn) |
| **LoomOS**         | Starter/Professional/Enterprise  | $149/mo   | Dokuma üretimi (weaving)     |
| **KnitOS**         | Starter/Professional/Enterprise  | $129/mo   | Örme üretimi (knitting)      |
| **DyeOS**          | Starter/Professional/Enterprise  | $119/mo   | Boya & Apre (finishing)      |
| **AnalyticsOS**    | Standard/Advanced/Enterprise     | $149/mo   | BI & Raporlama               |
| **IntelligenceOS** | Professional/Enterprise          | $299/mo   | AI & Tahminleme              |
| **EdgeOS**         | Starter/Professional/Enterprise  | $199/mo   | IoT & Sensörler              |
| **AccountOS**      | Standard/Professional/Enterprise | $79/mo    | Resmi Muhasebe               |
| **CustomOS**       | Standard/Professional/Enterprise | $399/mo   | Dış Entegrasyonlar           |

**🔑 Key Features:**

- ✅ **Composable** - Sadece ihtiyaç duyulan OS'lar alınır
- ✅ **String-Based Tiers** - Her OS'un kendi tier isimleri (enum yok!)
- ✅ **Feature Entitlement** - JSONB ile granular feature kontrolü
- ✅ **Usage Quotas** - API, storage, entity limitleri
- ✅ **4-Layer Policy Engine** - OS → Feature → Quota → RBAC

**📚 Detaylı Bilgi:**

- [SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md) - Kapsamlı dokümantasyon
- [SUBSCRIPTION_QUICK_START.md](./modular_monolith/SUBSCRIPTION_QUICK_START.md) - Hızlı başlangıç
