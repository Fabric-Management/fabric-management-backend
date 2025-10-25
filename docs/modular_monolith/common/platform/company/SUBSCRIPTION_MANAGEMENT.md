# ⚠️ DEPRECATED - SUBSCRIPTION MANAGEMENT

**Status:** ❌ Deprecated  
**Deprecated Date:** 2025-10-25  
**Reason:** Replaced by new Composable Feature-Based Subscription Model

---

## 📌 REDIRECT

This document has been **replaced** by the new subscription model documentation.

### **New Documentation:**

1. **[SUBSCRIPTION_INDEX.md](../../SUBSCRIPTION_INDEX.md)** - Start here! Documentation index
2. **[SUBSCRIPTION_MODEL.md](../../SUBSCRIPTION_MODEL.md)** - Kapsamlı subscription model (1167 satır)
3. **[SUBSCRIPTION_QUICK_START.md](../../SUBSCRIPTION_QUICK_START.md)** - Hızlı başlangıç
4. **[SUBSCRIPTION.md](./SUBSCRIPTION.md)** - Implementation guide

---

## 🔄 WHAT CHANGED?

### **Old Model (Deprecated):**

```java
@Enumerated(EnumType.STRING)
private PricingTier pricingTier;  // ENUM: FREE, BASIC, PROFESSIONAL, ENTERPRISE
```

### **New Model:**

```java
@Column(name = "pricing_tier", length = 50)
private String pricingTier;  // String: "Starter", "Professional", "Enterprise", "Standard", "Advanced"
```

**Why?**

- ✅ Flexible tier naming per OS
- ✅ AnalyticsOS uses "Standard/Advanced", not "Starter"
- ✅ IntelligenceOS has only "Professional/Enterprise"
- ✅ FabricOS has single tier "Base"
- ✅ No rigid ENUM constraints

---

## 🆕 NEW FEATURES

1. **Composable OS Model** - Kullanıcılar sadece ihtiyaç duydukları OS'ları satın alır
2. **Feature Entitlement** - Granular feature-level control
3. **Usage Quotas** - API calls, users, storage, entity limits
4. **String-Based Tiers** - Her OS'un kendi tier isimleri
5. **Database-Driven Feature Catalog** - JSONB ile esnek yapı

---

## 📚 MIGRATION GUIDE

**If you were using the old model:**

1. Read [SUBSCRIPTION_INDEX.md](../../SUBSCRIPTION_INDEX.md)
2. Review [SUBSCRIPTION_MODEL.md](../../SUBSCRIPTION_MODEL.md) - Section: "Pricing Tiers"
3. Update code to use `String pricingTier` instead of `PricingTier` enum
4. Use `PricingTierValidator` for tier validation
5. Implement feature catalog seeding

---

**For historical reference, old document archived at:**  
`docs/archive/SUBSCRIPTION_MANAGEMENT_OLD.md`

---

**Last Updated:** 2025-10-25
