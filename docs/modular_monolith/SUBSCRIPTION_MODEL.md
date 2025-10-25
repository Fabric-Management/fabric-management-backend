# 💳 FEATURE-BASED SUBSCRIPTION MODEL

**Version:** 1.0  
**Last Updated:** 2025-10-25  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Subscription Philosophy](#subscription-philosophy)
3. [OS Catalog](#os-catalog)
4. [Feature Entitlement System](#feature-entitlement-system)
5. [Usage Limits & Quotas](#usage-limits--quotas)
6. [Pricing Tiers](#pricing-tiers)
7. [Implementation Guide](#implementation-guide)
8. [API Reference](#api-reference)
9. [Customer Journey](#customer-journey)

---

## 🎯 OVERVIEW

FabricOS kullanıcıları, **Composable Feature-Based Subscription** modeli ile ihtiyaçlarına göre sistem özelliklerini seçebilirler.

### **Temel Prensipler**

| Prensip                 | Açıklama                                                      |
| ----------------------- | ------------------------------------------------------------- |
| **🧩 Composable**       | Kullanıcılar sadece ihtiyaç duydukları OS'ları satın alır     |
| **📊 Usage-Based**      | API call, user, storage gibi limitler ile esnek fiyatlandırma |
| **🎚️ Granular Control** | Feature-level access control (policy engine entegrasyonu)     |
| **🔄 Flexible**         | Aylık upgrade/downgrade, add-on ekleme/çıkarma                |
| **💡 Transparent**      | Her feature'ın fiyatı net, gizli maliyet yok                  |

### **Architecture Overview**

```
┌─────────────────────────────────────────────────────────────┐
│                    SUBSCRIPTION ENGINE                       │
├─────────────────────────────────────────────────────────────┤
│  Layer 1: OS Subscription Check                              │
│  ─────────────────────────────────────                       │
│  "Does tenant have active YarnOS?"                           │
│                                                              │
│  Layer 2: Feature Entitlement Check                          │
│  ─────────────────────────────────────                       │
│  "Does YarnOS subscription include 'yarn.iot.sensors'?"      │
│                                                              │
│  Layer 3: Usage Quota Check                                  │
│  ─────────────────────────────                               │
│  "Has tenant exceeded API call limit?"                       │
│                                                              │
│  Layer 4: Policy Engine (RBAC + ABAC)                        │
│  ─────────────────────────────────────                       │
│  "Does user have permission + context allows?"               │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎨 SUBSCRIPTION PHILOSOPHY

### **❌ Geleneksel SaaS Tier Sistemi (Kullanmıyoruz)**

```
Basic Plan     $49/mo   → Limited features, 5 users
Plus Plan      $149/mo  → More features, 20 users
Professional   $299/mo  → Almost all, 50 users
Enterprise     $999/mo  → Everything, unlimited
```

**Sorunlar:**

- Kullanıcı kullanmadığı özelliklere para ödüyor
- "Plus mı yoksa Professional mı?" kafa karışıklığı
- Tier atlama sırasında büyük fiyat farkları
- İşletme büyüdükçe zorla upgrade gerekiyor

### **✅ FabricOS Composable Model (Kullandığımız)**

```
FabricOS Base  $199/mo  → Temel platform (zorunlu)
   +
Optional OS Add-ons:
  □ YarnOS        + $99/mo
  □ LoomOS        + $149/mo
  □ AnalyticsOS   + $149/mo
  □ IntelligenceOS + $299/mo
```

**Avantajlar:**

- ✅ Sadece ihtiyaç duyulan modüller için ödeme
- ✅ İşletme büyüdükçe yeni OS ekleyebilme
- ✅ Şeffaf fiyatlandırma (hesaplanabilir maliyet)
- ✅ Farklı işletme tiplerini destekler

---

## 🧩 OS CATALOG

### **1. FabricOS (Base Platform)** — **ZORUNLU**

**Fiyat:** $199/mo (veya kullanıcı sayısına göre)  
**Açıklama:** Tüm tenantlar için temel sistem. Diğer tüm OS'lar bu platform üzerine kurulur.

**İçerik:**
| Module | Açıklama | Features |
|--------|----------|----------|
| **auth** | Kimlik doğrulama | JWT, OAuth2, SSO |
| **user** | Kullanıcı yönetimi | User CRUD, role management |
| **policy** | Policy engine | RBAC, ABAC, rule engine |
| **audit** | Denetim logları | All actions tracked |
| **config** | Konfigürasyon | System settings, preferences |
| **monitoring** | İzleme & metrikler | Health checks, metrics |
| **company** | Şirket/tenant yönetimi | Multi-tenant support |
| **logistics/inventory** | **Kısıtlı** stok takibi | Temel inventory (okuma ağırlıklı) |
| **finance** | **Kısıtlı** finans | Temel maliyet takibi |
| **human** | **Kısıtlı** İK | Basit employee management |
| **procurement** | **Kısıtlı** tedarik | Vendor management (basic) |
| **production/planning** | **Kısıtlı** planlama | Simple production planning |

**Usage Limits:**

- 10 users included
- 10,000 API calls/month
- 10 GB storage
- Email support

**Target Users:**

- Fason üretim yaptıran firmalar (kendi üretim tesisi yok)
- Supplier olmayan ama üretim takibi yapan şirketler
- Startups, küçük işletmeler

---

### **2. Optional OS Add-ons**

Her bir OS, FabricOS üzerine **TAM YETKİLİ** erişim sağlar.

#### **📦 YarnOS** — İplik İşletmeleri

**Fiyat:**

- Starter: $99/mo (5 users, basic)
- Professional: $199/mo (20 users, advanced)
- Enterprise: $399/mo (unlimited)

**Modules:**

- `production/fiber` (Fiber management - FULL access)
- `production/yarn` (Yarn production - FULL access)

**Features:**

| Feature ID                | Açıklama                | Starter | Professional | Enterprise             |
| ------------------------- | ----------------------- | ------- | ------------ | ---------------------- |
| `yarn.fiber.create`       | Fiber entity oluşturma  | ✅      | ✅           | ✅                     |
| `yarn.fiber.quality_test` | Fiber kalite testleri   | ✅      | ✅           | ✅                     |
| `yarn.blend.management`   | Karışım yönetimi        | ❌      | ✅           | ✅                     |
| `yarn.lot.tracking`       | Lot takibi              | ✅      | ✅           | ✅                     |
| `yarn.advanced.analytics` | Gelişmiş analizler      | ❌      | ❌           | ✅                     |
| `yarn.api.access`         | API erişimi             | ❌      | ✅           | ✅                     |
| `yarn.iot.sensors`        | IoT sensör entegrasyonu | ❌      | ❌           | ✅ (EdgeOS gerektirir) |

**Usage Limits:**

| Resource        | Starter | Professional | Enterprise |
| --------------- | ------- | ------------ | ---------- |
| Users           | 5       | 20           | Unlimited  |
| API Calls/month | 25,000  | 100,000      | Unlimited  |
| Fiber entities  | 500     | 5,000        | Unlimited  |
| Yarn SKUs       | 1,000   | 10,000       | Unlimited  |
| Storage         | 25 GB   | 100 GB       | 500 GB     |

**Target Users:**

- İplik üretimi yapan fabrikalar
- Fiber tedarikçileri
- Karışım (blend) üreticileri

---

#### **🧵 LoomOS** — Dokuma İşletmeleri

**Fiyat:**

- Starter: $149/mo
- Professional: $299/mo
- Enterprise: $599/mo

**Modules:**

- `production/weaving` (Weaving production - FULL access)

**Features:**

| Feature ID                       | Açıklama              | Starter | Professional | Enterprise                     |
| -------------------------------- | --------------------- | ------- | ------------ | ------------------------------ |
| `weaving.loom.management`        | Tezgah yönetimi       | ✅      | ✅           | ✅                             |
| `weaving.pattern.design`         | Desen tasarımı        | ✅      | ✅           | ✅                             |
| `weaving.quality.control`        | Kalite kontrol        | ✅      | ✅           | ✅                             |
| `weaving.defect.tracking`        | Hata takibi           | ✅      | ✅           | ✅                             |
| `weaving.efficiency.analytics`   | Verimlilik analizleri | ❌      | ✅           | ✅                             |
| `weaving.real_time.monitoring`   | Anlık izleme          | ❌      | ❌           | ✅ (EdgeOS gerektirir)         |
| `weaving.predictive.maintenance` | Tahmine dayalı bakım  | ❌      | ❌           | ✅ (IntelligenceOS gerektirir) |

**Target Users:**

- Dokuma fabrikaları
- Jakarlı dokuma üreticileri
- Teknik kumaş üreticileri

---

#### **🧶 KnitOS** — Örme İşletmeleri

**Fiyat:**

- Starter: $129/mo
- Professional: $259/mo
- Enterprise: $519/mo

**Modules:**

- `production/knitting` (Knitting production - FULL access)

**Features:**

| Feature ID                     | Açıklama               | Starter | Professional | Enterprise                     |
| ------------------------------ | ---------------------- | ------- | ------------ | ------------------------------ |
| `knitting.machine.management`  | Örme makinesi yönetimi | ✅      | ✅           | ✅                             |
| `knitting.program.design`      | Program tasarımı       | ✅      | ✅           | ✅                             |
| `knitting.quality.inspection`  | Kalite muayenesi       | ✅      | ✅           | ✅                             |
| `knitting.gauge.optimization`  | Gramaj optimizasyonu   | ❌      | ✅           | ✅                             |
| `knitting.ai.defect_detection` | AI hata tespiti        | ❌      | ❌           | ✅ (IntelligenceOS gerektirir) |

**Target Users:**

- Örme kumaş üreticileri
- Triko imalatçıları
- Çorap üreticileri

---

#### **🎨 DyeOS** — Boya & Apre İşletmeleri

**Fiyat:**

- Starter: $119/mo
- Professional: $239/mo
- Enterprise: $479/mo

**Modules:**

- `production/finishing` (Dyeing & finishing - FULL access)

**Features:**

| Feature ID                      | Açıklama                  | Starter | Professional | Enterprise                     |
| ------------------------------- | ------------------------- | ------- | ------------ | ------------------------------ |
| `finishing.recipe.management`   | Reçete yönetimi           | ✅      | ✅           | ✅                             |
| `finishing.color.matching`      | Renk eşleştirme           | ✅      | ✅           | ✅                             |
| `finishing.chemical.tracking`   | Kimyasal takibi           | ✅      | ✅           | ✅                             |
| `finishing.lab.management`      | Laboratuvar yönetimi      | ❌      | ✅           | ✅                             |
| `finishing.ai.color_prediction` | AI renk tahmini           | ❌      | ❌           | ✅ (IntelligenceOS gerektirir) |
| `finishing.iot.dyehouse`        | IoT boyahane entegrasyonu | ❌      | ❌           | ✅ (EdgeOS gerektirir)         |

**Target Users:**

- Boyahane işletmeleri
- Apre fabrikaları
- Baskı (printing) işletmeleri

---

#### **📊 AnalyticsOS (ReportOS)** — İş Zekası & Raporlama

**Fiyat:**

- Standard: $149/mo (pre-built dashboards)
- Advanced: $299/mo (custom reports)
- Enterprise: $599/mo (data warehouse + API)

**Modules:**

- `insight/analytics` (BI, dashboards, reporting - FULL access)

**Features:**

| Feature ID                 | Açıklama                 | Standard | Advanced | Enterprise |
| -------------------------- | ------------------------ | -------- | -------- | ---------- |
| `analytics.dashboard.view` | Dashboard görüntüleme    | ✅       | ✅       | ✅         |
| `analytics.report.export`  | Rapor export (PDF/Excel) | ✅       | ✅       | ✅         |
| `analytics.custom.reports` | Özel rapor oluşturma     | ❌       | ✅       | ✅         |
| `analytics.data.warehouse` | Data warehouse erişimi   | ❌       | ❌       | ✅         |
| `analytics.api.access`     | Analytics API            | ❌       | ❌       | ✅         |
| `analytics.embedded.bi`    | Embedded BI (iframe)     | ❌       | ❌       | ✅         |

**Target Users:**

- Herhangi bir işletme (cross-cutting concern)
- C-level executives
- İş analistleri

---

#### **🤖 IntelligenceOS (AIOS)** — Yapay Zeka & Tahminleme

**Fiyat:**

- Professional: $299/mo
- Enterprise: $799/mo

**Modules:**

- `insight/intelligence` (AI/ML, predictions, optimization - FULL access)

**Features:**

| Feature ID                        | Açıklama            | Professional | Enterprise |
| --------------------------------- | ------------------- | ------------ | ---------- |
| `intelligence.demand.forecasting` | Talep tahmini       | ✅           | ✅         |
| `intelligence.price.optimization` | Fiyat optimizasyonu | ✅           | ✅         |
| `intelligence.quality.prediction` | Kalite tahmini      | ✅           | ✅         |
| `intelligence.defect.detection`   | AI hata tespiti     | ❌           | ✅         |
| `intelligence.custom.models`      | Özel AI modelleri   | ❌           | ✅         |
| `intelligence.model.training`     | Model eğitimi       | ❌           | ✅         |

**Target Users:**

- Büyük işletmeler
- Optimizasyon arayan fabrikalar
- Data-driven karar alan şirketler

---

#### **🔌 EdgeOS** — IoT & Sensör Entegrasyonu

**Fiyat:**

- Starter: $199/mo (100 devices)
- Professional: $499/mo (500 devices)
- Enterprise: $999/mo (unlimited devices)

**Modules:**

- IoT & sensor integration (platform-wide)

**Features:**

| Feature ID                  | Açıklama         | Starter | Professional | Enterprise |
| --------------------------- | ---------------- | ------- | ------------ | ---------- |
| `edge.device.connect`       | Cihaz bağlantısı | ✅      | ✅           | ✅         |
| `edge.real_time.monitoring` | Anlık izleme     | ✅      | ✅           | ✅         |
| `edge.data.collection`      | Veri toplama     | ✅      | ✅           | ✅         |
| `edge.alert.management`     | Alarm yönetimi   | ✅      | ✅           | ✅         |
| `edge.device.limit`         | Cihaz limiti     | 100     | 500          | Unlimited  |
| `edge.custom.protocols`     | Özel protokoller | ❌      | ✅           | ✅         |
| `edge.edge.computing`       | Edge computing   | ❌      | ❌           | ✅         |

**Target Users:**

- Endüstri 4.0 fabrikaları
- Makine üreticileri
- Anlık veri toplayan işletmeler

---

#### **💼 AccountOS** — Resmi Muhasebe

**Fiyat:**

- Standard: $79/mo
- Professional: $159/mo
- Enterprise: $319/mo

**Modules:**

- `finance/accounting` (Official accounting, tax, invoicing - FULL access)

**Features:**

| Feature ID                      | Açıklama                   | Standard | Professional | Enterprise |
| ------------------------------- | -------------------------- | -------- | ------------ | ---------- |
| `account.ledger.management`     | Defter yönetimi            | ✅       | ✅           | ✅         |
| `account.invoice.create`        | Fatura oluşturma           | ✅       | ✅           | ✅         |
| `account.tax.calculation`       | Vergi hesaplama            | ✅       | ✅           | ✅         |
| `account.e_invoice.integration` | e-Fatura entegrasyonu (TR) | ❌       | ✅           | ✅         |
| `account.multi_currency`        | Çoklu para birimi          | ❌       | ✅           | ✅         |
| `account.financial.reporting`   | Mali raporlama             | ✅       | ✅           | ✅         |

**Target Users:**

- Muhasebe departmanları
- Mali müşavirlik ofisleri
- Küçük işletmeler (mali takip için)

---

#### **🔗 CustomOS** — Dış Sistem Entegrasyonları

**Fiyat:**

- Standard: $399/mo (5 integrations)
- Professional: $799/mo (20 integrations)
- Enterprise: Custom pricing

**Modules:**

- `integration` (ERP, IoT, EDI, webhooks - FULL access)

**Features:**

| Feature ID                   | Açıklama             | Standard | Professional | Enterprise |
| ---------------------------- | -------------------- | -------- | ------------ | ---------- |
| `custom.erp.integration`     | ERP entegrasyonu     | ✅       | ✅           | ✅         |
| `custom.webhook.management`  | Webhook yönetimi     | ✅       | ✅           | ✅         |
| `custom.api.adapter`         | API adapter          | ✅       | ✅           | ✅         |
| `custom.data.transformation` | Veri transformasyonu | ✅       | ✅           | ✅         |
| `custom.edi.b2b`             | EDI/B2B entegrasyonu | ❌       | ✅           | ✅         |
| `custom.integration.limit`   | Entegrasyon limiti   | 5        | 20           | Unlimited  |

**Target Users:**

- Büyük işletmeler (mevcut ERP var)
- B2B yapan şirketler
- Çoklu sistem kullanıcıları

---

## 🎛️ FEATURE ENTITLEMENT SYSTEM

### **Feature ID Naming Convention**

```
{os_code}.{module}.{feature_name}

Examples:
  yarn.fiber.create
  weaving.loom.management
  intelligence.demand.forecasting
  edge.device.connect
```

### **Feature Check Flow**

```java
// 1. OS Subscription Check (Layer 1)
boolean hasYarnOS = subscriptionService.hasActiveSubscription(tenantId, "YarnOS");
if (!hasYarnOS) {
    throw new SubscriptionRequiredException("YarnOS required");
}

// 2. Feature Entitlement Check (Layer 2)
boolean hasFeature = subscriptionService.hasFeature(tenantId, "yarn.blend.management");
if (!hasFeature) {
    throw new FeatureNotAvailableException("Upgrade to Professional tier");
}

// 3. Usage Quota Check (Layer 3)
boolean withinQuota = quotaService.checkQuota(tenantId, "api_calls");
if (!withinQuota) {
    throw new QuotaExceededException("API call limit exceeded");
}

// 4. Proceed with operation
createFiberBlend(...);
```

### **Database Schema**

```sql
-- Subscription table (existing)
CREATE TABLE common_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    os_code VARCHAR(50) NOT NULL,  -- "YarnOS", "LoomOS", etc.
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,   -- TRIAL, ACTIVE, EXPIRED, etc.
    pricing_tier VARCHAR(20),      -- STARTER, PROFESSIONAL, ENTERPRISE
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    trial_ends_at TIMESTAMP,
    features JSONB,                -- { "yarn.fiber.create": true, ... }
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- New: Usage Quotas table
CREATE TABLE common_subscription_quota (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subscription_id UUID REFERENCES common_subscription(id),
    quota_type VARCHAR(50) NOT NULL,  -- "users", "api_calls", "storage_gb", etc.
    quota_limit BIGINT NOT NULL,      -- Max allowed
    quota_used BIGINT DEFAULT 0,      -- Current usage
    reset_period VARCHAR(20),         -- "MONTHLY", "DAILY", "NONE"
    last_reset_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(tenant_id, subscription_id, quota_type)
);

-- New: Feature catalog (for admin UI)
CREATE TABLE common_feature_catalog (
    id UUID PRIMARY KEY,
    feature_id VARCHAR(100) UNIQUE NOT NULL,  -- "yarn.fiber.create"
    os_code VARCHAR(50) NOT NULL,              -- "YarnOS"
    feature_name VARCHAR(255) NOT NULL,
    description TEXT,
    available_in_tiers VARCHAR[] DEFAULT ARRAY['ENTERPRISE'], -- ['STARTER', 'PROFESSIONAL']
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## 📊 USAGE LIMITS & QUOTAS

### **Quota Types**

| Quota Type       | Açıklama                 | Reset Period        |
| ---------------- | ------------------------ | ------------------- |
| `users`          | Kullanıcı sayısı         | NONE (static limit) |
| `api_calls`      | API çağrısı sayısı       | MONTHLY             |
| `storage_gb`     | Depolama alanı (GB)      | NONE (static limit) |
| `fiber_entities` | Fiber entity sayısı      | NONE                |
| `yarn_skus`      | Yarn SKU sayısı          | NONE                |
| `iot_devices`    | IoT cihaz sayısı         | NONE                |
| `integrations`   | Aktif entegrasyon sayısı | NONE                |
| `custom_reports` | Özel rapor sayısı        | NONE                |

### **Quota Check Implementation**

```java
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final SubscriptionQuotaRepository quotaRepository;

    /**
     * Check if tenant is within quota for given resource.
     *
     * @throws QuotaExceededException if quota exceeded
     */
    public void enforceQuota(UUID tenantId, String quotaType) {
        SubscriptionQuota quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType)
            .orElseThrow(() -> new IllegalStateException("Quota not configured"));

        if (quota.getQuotaUsed() >= quota.getQuotaLimit()) {
            throw new QuotaExceededException(
                "Quota exceeded for " + quotaType +
                ". Used: " + quota.getQuotaUsed() + "/" + quota.getQuotaLimit()
            );
        }
    }

    /**
     * Increment quota usage.
     */
    @Transactional
    public void incrementQuota(UUID tenantId, String quotaType, long increment) {
        SubscriptionQuota quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType)
            .orElseThrow();

        quota.setQuotaUsed(quota.getQuotaUsed() + increment);
        quotaRepository.save(quota);
    }

    /**
     * Reset monthly quotas (scheduled job).
     */
    @Scheduled(cron = "0 0 0 1 * ?") // First day of month
    @Transactional
    public void resetMonthlyQuotas() {
        List<SubscriptionQuota> monthlyQuotas = quotaRepository
            .findByResetPeriod("MONTHLY");

        for (SubscriptionQuota quota : monthlyQuotas) {
            quota.setQuotaUsed(0L);
            quota.setLastResetAt(Instant.now());
        }

        quotaRepository.saveAll(monthlyQuotas);
    }
}
```

---

## 💰 PRICING TIERS

### **Tier Philosophy: Esnek ve OS'a Özel**

Her OS'un kendi tier yapısı vardır. Katı ENUM yerine **String-based** tier sistemi kullanılır.

### **Tier Naming by OS**

| OS                                    | Tier 1              | Tier 2           | Tier 3         |
| ------------------------------------- | ------------------- | ---------------- | -------------- |
| YarnOS, LoomOS, KnitOS, DyeOS, EdgeOS | **Starter**         | **Professional** | **Enterprise** |
| AnalyticsOS                           | **Standard**        | **Advanced**     | **Enterprise** |
| AccountOS, CustomOS                   | **Standard**        | **Professional** | **Enterprise** |
| IntelligenceOS                        | -                   | **Professional** | **Enterprise** |
| FabricOS                              | **Base** (tek tier) | -                | -              |

### **Tier Feature Matrix (Database-Driven)**

Feature entitlement'lar `common_feature_catalog` tablosunda saklanır:

```sql
-- Example: YarnOS blend management feature
INSERT INTO common_company.common_feature_catalog (
    id, tenant_id, feature_id, os_code, feature_name,
    available_in_tiers, is_active
) VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000000', -- System tenant
    'yarn.blend.management',
    'YarnOS',
    'Blend Management',
    '["Professional", "Enterprise"]'::jsonb,
    true
);

-- Example: AnalyticsOS custom reports
INSERT INTO common_company.common_feature_catalog (
    id, tenant_id, feature_id, os_code, feature_name,
    available_in_tiers, is_active
) VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000000',
    'analytics.custom.reports',
    'AnalyticsOS',
    'Custom Report Builder',
    '["Advanced", "Enterprise"]'::jsonb,
    true
);
```

**Configuration Class (Optional - for seed data):**

```java
@Configuration
public class FeatureCatalogSeeder {

    @Bean
    CommandLineRunner seedFeatureCatalog(FeatureCatalogRepository repository) {
        return args -> {
            if (repository.count() > 0) return; // Already seeded

            // YarnOS features
            repository.saveAll(List.of(
                createFeature("yarn.fiber.create", "YarnOS", "Fiber Entity Creation",
                    List.of("Starter", "Professional", "Enterprise")),
                createFeature("yarn.blend.management", "YarnOS", "Blend Management",
                    List.of("Professional", "Enterprise")),
                createFeature("yarn.advanced.analytics", "YarnOS", "Advanced Analytics",
                    List.of("Enterprise")),

                // AnalyticsOS features
                createFeature("analytics.dashboard.view", "AnalyticsOS", "Dashboard View",
                    List.of("Standard", "Advanced", "Enterprise")),
                createFeature("analytics.custom.reports", "AnalyticsOS", "Custom Reports",
                    List.of("Advanced", "Enterprise")),
                createFeature("analytics.api.access", "AnalyticsOS", "API Access",
                    List.of("Enterprise"))
            ));
        };
    }

    private FeatureCatalog createFeature(String id, String os, String name, List<String> tiers) {
        return FeatureCatalog.builder()
            .featureId(id)
            .osCode(os)
            .featureName(name)
            .availableInTiers(tiers)
            .isActive(true)
            .build();
    }
}
```

---

## 🛠️ IMPLEMENTATION GUIDE

### **Step 1: Tier Naming Conventions**

Her OS'un kendi tier isimlendirmesi vardır:

```java
// YarnOS, LoomOS, KnitOS, DyeOS, EdgeOS tier'ları
String[] PRODUCTION_OS_TIERS = {"Starter", "Professional", "Enterprise"};

// AnalyticsOS, AccountOS, CustomOS tier'ları
String[] ANALYTICS_OS_TIERS = {"Standard", "Professional", "Enterprise"};
// veya Advanced varsa: {"Standard", "Advanced", "Enterprise"}

// IntelligenceOS tier'ları (sadece 2 tier)
String[] INTELLIGENCE_OS_TIERS = {"Professional", "Enterprise"};

// FabricOS (Base) - tier yok, herkes aynı özelliklere sahip
String FABRIC_OS_TIER = "Base";
```

**Tier Validation Utility:**

```java
package com.fabricmanagement.common.platform.company.domain;

import java.util.Map;
import java.util.Set;

public class PricingTierValidator {

    private static final Map<String, Set<String>> OS_VALID_TIERS = Map.of(
        "YarnOS", Set.of("Starter", "Professional", "Enterprise"),
        "LoomOS", Set.of("Starter", "Professional", "Enterprise"),
        "KnitOS", Set.of("Starter", "Professional", "Enterprise"),
        "DyeOS", Set.of("Starter", "Professional", "Enterprise"),
        "EdgeOS", Set.of("Starter", "Professional", "Enterprise"),
        "AnalyticsOS", Set.of("Standard", "Advanced", "Enterprise"),
        "AccountOS", Set.of("Standard", "Professional", "Enterprise"),
        "CustomOS", Set.of("Standard", "Professional", "Enterprise"),
        "IntelligenceOS", Set.of("Professional", "Enterprise"),
        "FabricOS", Set.of("Base")
    );

    public static boolean isValidTier(String osCode, String tierName) {
        Set<String> validTiers = OS_VALID_TIERS.get(osCode);
        return validTiers != null && validTiers.contains(tierName);
    }

    public static Set<String> getValidTiers(String osCode) {
        return OS_VALID_TIERS.getOrDefault(osCode, Set.of());
    }

    public static String getDefaultTier(String osCode) {
        return switch (osCode) {
            case "YarnOS", "LoomOS", "KnitOS", "DyeOS", "EdgeOS" -> "Starter";
            case "AnalyticsOS", "AccountOS", "CustomOS" -> "Standard";
            case "IntelligenceOS" -> "Professional";
            case "FabricOS" -> "Base";
            default -> "Starter";
        };
    }
}
```

### **Step 2: Create Quota Entity**

```java
package com.fabricmanagement.common.platform.company.domain;

@Entity
@Table(name = "common_subscription_quota", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionQuota extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "quota_type", nullable = false, length = 50)
    private String quotaType;

    @Column(name = "quota_limit", nullable = false)
    private Long quotaLimit;

    @Column(name = "quota_used", nullable = false)
    @Builder.Default
    private Long quotaUsed = 0L;

    @Column(name = "reset_period", length = 20)
    private String resetPeriod; // "MONTHLY", "DAILY", "NONE"

    @Column(name = "last_reset_at")
    private Instant lastResetAt;

    public boolean isExceeded() {
        return quotaUsed >= quotaLimit;
    }

    public long remaining() {
        return Math.max(0, quotaLimit - quotaUsed);
    }

    public void increment(long amount) {
        this.quotaUsed += amount;
    }

    public void reset() {
        this.quotaUsed = 0L;
        this.lastResetAt = Instant.now();
    }
}
```

### **Step 3: Create Feature Catalog Entity**

```java
package com.fabricmanagement.common.platform.company.domain;

@Entity
@Table(name = "common_feature_catalog", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureCatalog extends BaseEntity {

    @Column(name = "feature_id", unique = true, nullable = false, length = 100)
    private String featureId; // "yarn.fiber.create"

    @Column(name = "os_code", nullable = false, length = 50)
    private String osCode; // "YarnOS"

    @Column(name = "feature_name", nullable = false, length = 255)
    private String featureName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Type(JsonType.class)
    @Column(name = "available_in_tiers", columnDefinition = "jsonb")
    private List<String> availableInTiers; // ["PROFESSIONAL", "ENTERPRISE"]

    public boolean isAvailableInTier(PricingTier tier) {
        return availableInTiers != null &&
               availableInTiers.contains(tier.name());
    }
}
```

### **Step 4: Enhance SubscriptionService**

```java
@Service
@RequiredArgsConstructor
public class EnhancedSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeatureCatalogRepository featureCatalogRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    /**
     * Layer 2: Feature entitlement check.
     *
     * Checks if tenant's subscription includes the requested feature.
     */
    public boolean hasFeature(UUID tenantId, String featureId) {
        // Extract OS code from feature ID (e.g., "yarn.fiber.create" → "YarnOS")
        String osCode = extractOsCode(featureId);

        // Check if tenant has active subscription to that OS
        Optional<Subscription> subscription = subscriptionRepository
            .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

        if (subscription.isEmpty()) {
            return false;
        }

        Subscription sub = subscription.get();

        // Check if feature is in subscription's features map (override)
        if (sub.getFeatures().containsKey(featureId)) {
            return sub.getFeatures().get(featureId);
        }

        // Check if feature is available in subscription's pricing tier
        Optional<FeatureCatalog> feature = featureCatalogRepository
            .findByFeatureId(featureId);

        return feature.isPresent() &&
               feature.get().isAvailableInTier(sub.getPricingTier()); // String comparison
    }

    /**
     * Layer 3: Usage quota check.
     */
    public boolean hasQuota(UUID tenantId, String quotaType) {
        Optional<SubscriptionQuota> quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType);

        return quota.isPresent() && !quota.get().isExceeded();
    }

    /**
     * Combined check: OS + Feature + Quota.
     */
    public void enforceEntitlement(UUID tenantId, String featureId, String quotaType) {
        // Layer 1: OS subscription
        String osCode = extractOsCode(featureId);
        if (!hasActiveSubscription(tenantId, osCode)) {
            throw new SubscriptionRequiredException(osCode + " subscription required");
        }

        // Layer 2: Feature entitlement
        if (!hasFeature(tenantId, featureId)) {
            throw new FeatureNotAvailableException(
                "Feature '" + featureId + "' not available in your plan. Upgrade required."
            );
        }

        // Layer 3: Usage quota
        if (quotaType != null && !hasQuota(tenantId, quotaType)) {
            throw new QuotaExceededException(
                "Quota exceeded for " + quotaType + ". Upgrade or wait for reset."
            );
        }
    }

    private String extractOsCode(String featureId) {
        // "yarn.fiber.create" → "yarn" → "YarnOS"
        String prefix = featureId.split("\\.")[0];
        return switch (prefix) {
            case "yarn" -> "YarnOS";
            case "weaving" -> "LoomOS";
            case "knitting" -> "KnitOS";
            case "finishing" -> "DyeOS";
            case "analytics" -> "AnalyticsOS";
            case "intelligence" -> "IntelligenceOS";
            case "edge" -> "EdgeOS";
            case "account" -> "AccountOS";
            case "custom" -> "CustomOS";
            default -> "FabricOS";
        };
    }
}
```

### **Step 5: Controller Integration**

```java
@RestController
@RequestMapping("/api/v1/yarn/fibers")
@RequiredArgsConstructor
public class FiberController {

    private final FiberService fiberService;
    private final EnhancedSubscriptionService subscriptionService;
    private final QuotaService quotaService;

    @PostMapping
    public ResponseEntity<FiberDto> createFiber(@RequestBody CreateFiberRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // ENTITLEMENT CHECK
        subscriptionService.enforceEntitlement(
            tenantId,
            "yarn.fiber.create",  // Feature ID
            "fiber_entities"      // Quota type
        );

        // Proceed with operation
        FiberDto fiber = fiberService.createFiber(request);

        // Increment quota
        quotaService.incrementQuota(tenantId, "fiber_entities", 1);

        return ResponseEntity.ok(fiber);
    }

    @PostMapping("/{fiberId}/blend")
    public ResponseEntity<FiberBlendDto> createBlend(
        @PathVariable UUID fiberId,
        @RequestBody CreateBlendRequest request
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // Feature gating: blend management requires Professional+
        subscriptionService.enforceEntitlement(
            tenantId,
            "yarn.blend.management",  // This throws if not in PROFESSIONAL/ENTERPRISE
            null
        );

        FiberBlendDto blend = fiberService.createBlend(fiberId, request);
        return ResponseEntity.ok(blend);
    }
}
```

---

## 📡 API REFERENCE

### **Get Active Subscriptions**

```http
GET /api/v1/subscriptions/active

Response:
[
  {
    "id": "uuid",
    "osCode": "YarnOS",
    "osName": "Yarn Production OS",
    "status": "ACTIVE",
    "pricingTier": "PROFESSIONAL",
    "startDate": "2025-01-01T00:00:00Z",
    "expiryDate": "2026-01-01T00:00:00Z",
    "features": {
      "yarn.fiber.create": true,
      "yarn.blend.management": true
    }
  }
]
```

### **Check Feature Availability**

```http
GET /api/v1/subscriptions/features/{featureId}/available

Response:
{
  "featureId": "yarn.blend.management",
  "available": true,
  "reason": "Included in YarnOS Professional"
}
```

### **Get Usage Quotas**

```http
GET /api/v1/subscriptions/quotas

Response:
[
  {
    "quotaType": "users",
    "limit": 20,
    "used": 15,
    "remaining": 5,
    "resetPeriod": "NONE"
  },
  {
    "quotaType": "api_calls",
    "limit": 100000,
    "used": 45230,
    "remaining": 54770,
    "resetPeriod": "MONTHLY",
    "lastResetAt": "2025-10-01T00:00:00Z"
  }
]
```

---

## 🎯 CUSTOMER JOURNEY

### **Scenario 1: Fason Üretici (Sadece FabricOS)**

**Profil:** Üretimi olmayan, fason yaptıran firma  
**Subscriptions:** FabricOS (Base)  
**Price:** $199/mo

**Kullanabilir:**

- ✅ Basit stok takibi (kısıtlı)
- ✅ Temel finans (kısıtlı)
- ✅ Basit üretim planlaması (kısıtlı)
- ✅ Tedarikçi yönetimi (kısıtlı)
- ❌ Fiber/Yarn entity oluşturma (YarnOS gerekli)
- ❌ Dokuma üretimi (LoomOS gerekli)

---

### **Scenario 2: İplik Fabrikası**

**Profil:** İplik üretimi yapan fabrika  
**Subscriptions:** FabricOS + YarnOS Professional  
**Price:** $199/mo + $199/mo = **$398/mo**

**Kullanabilir:**

- ✅ Tüm FabricOS özellikleri
- ✅ Fiber entity oluşturma (FULL)
- ✅ Yarn üretimi (FULL)
- ✅ Karışım (blend) yönetimi
- ✅ API erişimi
- ✅ 20 kullanıcı, 100k API call/ay
- ❌ AI analizler (IntelligenceOS gerekli)
- ❌ IoT sensörler (EdgeOS gerekli)

**Upgrade Path:**

- IntelligenceOS ekle → AI tahminleme ($299/mo daha)
- EdgeOS ekle → Anlık sensör verisi ($199/mo daha)

---

### **Scenario 3: Entegre Tekstil Tesisi**

**Profil:** İplikten kumaşa tam üretim + AI + IoT  
**Subscriptions:** FabricOS + YarnOS Enterprise + LoomOS Enterprise + EdgeOS Professional + IntelligenceOS Enterprise  
**Price:** $199 + $399 + $599 + $499 + $799 = **$2,495/mo**

**Kullanabilir:**

- ✅ Tüm sistem özellikleri
- ✅ Unlimited kullanıcı, API, storage
- ✅ IoT sensör entegrasyonu (500 device)
- ✅ AI tahminleme & optimizasyon
- ✅ Özel AI modelleri
- ✅ Embedded BI

---

## 🎁 BONUS: ADD-ON PACKAGES

Temel OS'lara ek olarak, **capacity add-on** paketleri sunabilirsiniz:

| Add-on                 | Price   | Açıklama              |
| ---------------------- | ------- | --------------------- |
| **+10 Users**          | $29/mo  | 10 ek kullanıcı       |
| **+50,000 API calls**  | $49/mo  | 50k ek API çağrısı    |
| **+50 GB Storage**     | $19/mo  | 50 GB ek depolama     |
| **+100 IoT Devices**   | $99/mo  | 100 ek IoT cihazı     |
| **Priority Support**   | $299/mo | 24/7 priority support |
| **Dedicated Instance** | Custom  | Ayrı sunucu/DB        |

---

## 🚀 NEXT STEPS

1. ✅ **Database Migration:** Quota ve FeatureCatalog tablolarını oluştur
2. ✅ **Seed Data:** Feature catalog'u populate et
3. ✅ **API Implementation:** Feature check endpoint'leri
4. ✅ **Frontend:** Subscription management UI
5. ✅ **Billing Integration:** Stripe/Paddle entegrasyonu
6. ✅ **Analytics:** Usage tracking & insights
7. ✅ **Documentation:** Customer-facing pricing page

---

## 📚 RELATED DOCUMENTS

- [ARCHITECTURE.md](./ARCHITECTURE.md) - Modular Monolith architecture
- [SECURITY_POLICIES.md](./SECURITY_POLICIES.md) - Policy engine integration
- [MODULE_PROTOCOLS.md](./MODULE_PROTOCOLS.md) - Module communication patterns
- [PROJECT_PROGRESS.md](./PROJECT_PROGRESS.md) - Implementation progress

---

**💡 Key Takeaway:**

> **FabricOS'un özellik tabanlı abonelik modeli, kullanıcılara maksimum esneklik sunarken, sistem mimarisine mükemmel şekilde entegre olur. Her tenant, sadece ihtiyaç duyduğu OS'ları satın alır ve sistem, policy engine ile granular erişim kontrolü sağlar.**

---

_Son Güncelleme: 2025-10-25_
