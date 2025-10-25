# 🚀 SUBSCRIPTION MODEL - QUICK START

**Version:** 1.0  
**Last Updated:** 2025-10-25

---

## 📌 OVERVIEW

FabricOS, **Composable Feature-Based Subscription** modeli kullanır. Her işletme sadece ihtiyacı olan OS'ları satın alır.

---

## 🧩 OS CATALOG (Özet)

### **FabricOS (Base Platform)** — ZORUNLU

- **Fiyat:** $199/mo
- **İçerik:** auth, user, policy, audit, company, kısıtlı logistics/finance/human/procurement
- **Tier:** Tek tier (Base)
- **Hedef:** Tüm kullanıcılar için temel platform

### **Optional OS'lar**

| OS                 | Açıklama           | Tier'lar                             | Başlangıç Fiyatı |
| ------------------ | ------------------ | ------------------------------------ | ---------------- |
| **YarnOS**         | İplik üretimi      | Starter / Professional / Enterprise  | $99/mo           |
| **LoomOS**         | Dokuma üretimi     | Starter / Professional / Enterprise  | $149/mo          |
| **KnitOS**         | Örme üretimi       | Starter / Professional / Enterprise  | $129/mo          |
| **DyeOS**          | Boya & Apre        | Starter / Professional / Enterprise  | $119/mo          |
| **AnalyticsOS**    | BI & Raporlama     | Standard / Advanced / Enterprise     | $149/mo          |
| **IntelligenceOS** | AI & Tahminleme    | Professional / Enterprise            | $299/mo          |
| **EdgeOS**         | IoT & Sensörler    | Starter / Professional / Enterprise  | $199/mo          |
| **AccountOS**      | Resmi Muhasebe     | Standard / Professional / Enterprise | $79/mo           |
| **CustomOS**       | Dış Entegrasyonlar | Standard / Professional / Enterprise | $399/mo          |

---

## 🎯 TIER NAMING CONVENTIONS

Her OS'un kendi tier isimlendirmesi vardır:

```java
// Production OS'lar (Yarn, Loom, Knit, Dye, Edge)
"Starter" → "Professional" → "Enterprise"

// Analytics/Business OS'lar (Analytics, Account, Custom)
"Standard" → "Professional" → "Enterprise"
// veya Advanced: "Standard" → "Advanced" → "Enterprise"

// Intelligence OS (sadece 2 tier)
"Professional" → "Enterprise"

// FabricOS (tek tier)
"Base"
```

---

## 💡 FEATURE ID CONVENTION

```
{os_prefix}.{module}.{feature_name}

Örnekler:
  yarn.fiber.create              → YarnOS - Fiber oluşturma
  yarn.blend.management          → YarnOS - Karışım yönetimi (Professional+)
  weaving.loom.management        → LoomOS - Tezgah yönetimi
  analytics.custom.reports       → AnalyticsOS - Özel raporlar (Advanced+)
  intelligence.demand.forecasting → IntelligenceOS - Talep tahmini
  edge.device.connect            → EdgeOS - IoT cihaz bağlantısı
```

---

## 🔐 POLICY ENGINE LAYERS

```
┌─────────────────────────────────────────┐
│ Layer 1: OS Subscription Check          │
│ ─────────────────────────────────        │
│ "Does tenant have YarnOS?"               │
│                                          │
│ Layer 2: Feature Entitlement Check       │
│ ─────────────────────────────────        │
│ "Does YarnOS include yarn.blend?"        │
│                                          │
│ Layer 3: Usage Quota Check               │
│ ─────────────────────────                │
│ "Has tenant exceeded fiber limit?"       │
│                                          │
│ Layer 4: RBAC/ABAC (existing)            │
│ ─────────────────────────                │
│ "Does user have permission?"             │
└─────────────────────────────────────────┘
```

---

## 🛠️ IMPLEMENTATION EXAMPLES

### **1. Create Subscription**

```java
Subscription yarnSubscription = Subscription.builder()
    .osCode("YarnOS")
    .osName("Yarn Production OS")
    .status(SubscriptionStatus.ACTIVE)
    .pricingTier("Professional")  // String, not ENUM!
    .startDate(Instant.now())
    .expiryDate(Instant.now().plus(365, ChronoUnit.DAYS))
    .features(Map.of(
        "yarn.fiber.create", true,
        "yarn.blend.management", true,
        "yarn.api.access", true
    ))
    .build();

subscriptionRepository.save(yarnSubscription);
```

### **2. Check Feature Availability**

```java
@Service
public class EnhancedSubscriptionService {

    public boolean hasFeature(UUID tenantId, String featureId) {
        // 1. Extract OS from feature ID
        String osCode = extractOsCode(featureId); // "yarn.fiber.create" → "YarnOS"

        // 2. Check active subscription
        Optional<Subscription> sub = subscriptionRepository
            .findActiveSubscriptionByOsCode(tenantId, osCode, Instant.now());

        if (sub.isEmpty()) {
            return false; // No subscription to this OS
        }

        // 3. Check feature in subscription's tier
        Optional<FeatureCatalog> feature = featureCatalogRepository
            .findByFeatureId(featureId);

        return feature.isPresent() &&
               feature.get().isAvailableInTier(sub.get().getPricingTier());
    }
}
```

### **3. Controller with Feature Gating**

```java
@RestController
@RequestMapping("/api/v1/yarn/fibers")
public class FiberController {

    @Autowired
    private EnhancedSubscriptionService subscriptionService;

    @PostMapping("/{fiberId}/blend")
    public ResponseEntity<FiberBlendDto> createBlend(
        @PathVariable UUID fiberId,
        @RequestBody CreateBlendRequest request
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // FEATURE GATING: blend management requires Professional+
        if (!subscriptionService.hasFeature(tenantId, "yarn.blend.management")) {
            throw new FeatureNotAvailableException(
                "Blend management is only available in Professional and Enterprise tiers",
                "yarn.blend.management",
                "Professional"
            );
        }

        // Proceed with operation
        FiberBlendDto blend = fiberService.createBlend(fiberId, request);
        return ResponseEntity.ok(blend);
    }
}
```

### **4. Quota Management**

```java
@Service
public class QuotaService {

    @Transactional
    public void enforceQuota(UUID tenantId, String quotaType) {
        SubscriptionQuota quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType)
            .orElseThrow(() -> new IllegalStateException("Quota not configured"));

        if (quota.isExceeded()) {
            throw new QuotaExceededException(
                String.format("Quota exceeded for %s. Used: %d/%d",
                    quotaType, quota.getQuotaUsed(), quota.getQuotaLimit()),
                quotaType,
                quota.getQuotaLimit(),
                quota.getQuotaUsed()
            );
        }
    }

    @Transactional
    public void incrementQuota(UUID tenantId, String quotaType, long increment) {
        SubscriptionQuota quota = quotaRepository
            .findByTenantIdAndQuotaType(tenantId, quotaType)
            .orElseThrow();

        quota.increment(increment);
        quotaRepository.save(quota);
    }
}
```

---

## 📊 USAGE SCENARIOS

### **Scenario 1: Fason Üretici**

```
Subscriptions: FabricOS (Base)
Price: $199/mo
Can Use: ✅ Basic inventory, finance, procurement (kısıtlı)
Cannot Use: ❌ Fiber/Yarn entities (needs YarnOS)
```

### **Scenario 2: İplik Fabrikası**

```
Subscriptions: FabricOS + YarnOS Professional
Price: $199 + $199 = $398/mo
Can Use:
  ✅ Full fiber & yarn production
  ✅ Blend management
  ✅ API access
  ✅ 20 users, 100k API calls
Cannot Use:
  ❌ AI analytics (needs IntelligenceOS)
  ❌ IoT sensors (needs EdgeOS)
```

### **Scenario 3: Entegre Tekstil Tesisi**

```
Subscriptions: FabricOS + YarnOS Enterprise + LoomOS Enterprise + EdgeOS Pro + IntelligenceOS Enterprise
Price: $199 + $399 + $599 + $499 + $799 = $2,495/mo
Can Use: ✅ Everything (full platform access)
```

---

## 🗂️ DATABASE SCHEMA (Minimal)

```sql
-- Subscriptions
CREATE TABLE common_company.common_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    os_code VARCHAR(50) NOT NULL,           -- "YarnOS", "LoomOS", etc.
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,            -- TRIAL, ACTIVE, EXPIRED, etc.
    pricing_tier VARCHAR(50) NOT NULL,      -- "Starter", "Professional", "Standard", etc.
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    features JSONB,                         -- { "yarn.blend.management": true, ... }
    created_at TIMESTAMP DEFAULT NOW()
);

-- Feature Catalog
CREATE TABLE common_company.common_feature_catalog (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    feature_id VARCHAR(100) UNIQUE NOT NULL,  -- "yarn.blend.management"
    os_code VARCHAR(50) NOT NULL,              -- "YarnOS"
    feature_name VARCHAR(255) NOT NULL,
    available_in_tiers JSONB,                  -- ["Professional", "Enterprise"]
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Usage Quotas
CREATE TABLE common_company.common_subscription_quota (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subscription_id UUID REFERENCES common_subscription(id),
    quota_type VARCHAR(50) NOT NULL,       -- "users", "api_calls", "fiber_entities"
    quota_limit BIGINT NOT NULL,
    quota_used BIGINT DEFAULT 0,
    reset_period VARCHAR(20),              -- "MONTHLY", "DAILY", "NONE"
    last_reset_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## 🎁 ADD-ONS (Capacity Upgrades)

```
+10 Users          → $29/mo
+50,000 API calls  → $49/mo
+50 GB Storage     → $19/mo
+100 IoT Devices   → $99/mo
Priority Support   → $299/mo
```

---

## ✅ KEY TAKEAWAYS

1. **Composable:** Kullanıcılar ihtiyaç duydukları OS'ları seçer
2. **String-based Tiers:** Her OS'un kendi tier isimleri var (enum yok!)
3. **Feature Entitlement:** JSONB ile esnek feature kontrolü
4. **Usage Quotas:** API, storage, entity limitleri
5. **Policy Engine:** 4-layer kontrolle güvenlik
6. **Transparent Pricing:** Her özelliğin fiyatı net

---

## 📚 RELATED DOCS

- [SUBSCRIPTION_MODEL.md](./SUBSCRIPTION_MODEL.md) - Detaylı dokümantasyon
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Modular monolith mimari
- [SECURITY_POLICIES.md](./SECURITY_POLICIES.md) - Policy engine detayları

---

**💡 Remember:**

> **FabricOS = LEGO blocks. Her işletme kendi sistemini kurgular!**

---

_Son Güncelleme: 2025-10-25_
