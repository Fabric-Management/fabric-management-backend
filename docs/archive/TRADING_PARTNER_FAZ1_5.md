# Trading Partner — Faz 1.5: Legacy FK Migration (Dual FK Strategy)

Bu doküman, **sipariş / fatura / tedarik** gibi transactional tablolarda `company_id` yerine (veya yanında) `trading_partner_id` kullanımı için kapsamlı rehberdir.

## Mevcut Durum

- **Faz 1** tamamlandı: `TradingPartnerRegistry`, `TradingPartner`, migration (V039, V040, V041), API ve dual-read.
- **Durum:** Transactional tablolar (Order, Invoice, Shipment) henüz kod tabanında yok.
- Bu doküman, bu tablolar eklendiğinde veya mevcut tablolara partner FK'sı eklendiğinde kullanılacak stratejiyi tanımlar.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  BEFORE (Legacy):                                                │
│  orders / invoices / shipments                                   │
│  └── company_id → common_company (partner için)                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓ Migration
┌─────────────────────────────────────────────────────────────────┐
│  DURING (Dual FK - Geçiş Dönemi):                               │
│  orders / invoices / shipments                                   │
│  ├── company_id (mevcut, partner için)                          │
│  ├── trading_partner_id (YENİ, nullable başlangıçta)            │
│  └── Migration: company_id → trading_partner_id                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓ Cutover
┌─────────────────────────────────────────────────────────────────┐
│  AFTER (Clean State):                                            │
│  orders / invoices / shipments                                   │
│  └── trading_partner_id → common_trading_partner                 │
│       (company_id kaldırıldı)                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Roadmap

| #   | Migration                               | Açıklama                                         | Risk     |
| --- | --------------------------------------- | ------------------------------------------------ | -------- |
| 1   | V042\_\_add_trading_partner_fk_to_X.sql | `trading_partner_id` kolonu ekle (nullable)      | Düşük    |
| 2   | V043\_\_populate_trading_partner_fk.sql | `legacy_company_id` üzerinden populate et        | Düşük    |
| 3   | Service layer update                    | Dual-read'i order/invoice service'lerinde uygula | Orta     |
| 4   | feature.legacy-fallback: false          | Tüm FK'lar migrate olunca feature flag'i kapat   | Düşük    |
| 5   | V044\_\_drop_company_id_from_X.sql      | Eski kolonları kaldır (final cleanup)            | Yüksek\* |

> \*Yüksek risk sadece V044 için: Rollback yapılamaz, tüm sistemlerin test edilmesi gerekir.

---

## STEP 1: Add Trading Partner FK (V042)

### SQL Template

```sql
-- ═══════════════════════════════════════════════════════════════════════════
-- V042__add_trading_partner_fk_to_orders.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Adds trading_partner_id FK to orders table for TradingPartner migration.
--
-- Strategy: Dual FK (company_id + trading_partner_id) during transition
-- Risk: LOW - additive change, backward compatible
--
-- Prerequisites:
--   - V039__create_trading_partner_tables.sql (TradingPartner exists)
--   - V040__migrate_company_to_trading_partner.sql (data migrated)
-- ═══════════════════════════════════════════════════════════════════════════

-- Add nullable FK column
ALTER TABLE your_schema.orders
  ADD COLUMN IF NOT EXISTS trading_partner_id UUID;

-- Add FK constraint (RESTRICT prevents orphans)
ALTER TABLE your_schema.orders
  ADD CONSTRAINT fk_orders_trading_partner
  FOREIGN KEY (trading_partner_id)
  REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT;

-- Index for join performance
CREATE INDEX IF NOT EXISTS idx_orders_trading_partner
    ON your_schema.orders(trading_partner_id)
    WHERE trading_partner_id IS NOT NULL;

-- Comment for documentation
COMMENT ON COLUMN your_schema.orders.trading_partner_id IS
'FK to TradingPartner. During transition: nullable, populated from company_id via legacy_company_id mapping.';
```

### Entity Update

```java
@Entity
@Table(name = "orders", schema = "your_schema")
public class Order extends BaseEntity {

    // Legacy FK (will be removed after migration)
    @Column(name = "company_id")
    private UUID companyId;

    // New FK (nullable during transition)
    @Column(name = "trading_partner_id")
    private UUID tradingPartnerId;

    // Relationship (optional - use if you need lazy loading)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
    private TradingPartner tradingPartner;

    // Helper method for transition period
    public UUID getEffectivePartnerId() {
        return tradingPartnerId != null ? tradingPartnerId : companyId;
    }
}
```

---

## STEP 2: Populate Trading Partner FK (V043)

### SQL Template

```sql
-- ═══════════════════════════════════════════════════════════════════════════
-- V043__populate_trading_partner_fk.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Populates trading_partner_id from company_id via legacy_company_id mapping.
--
-- Strategy: UPDATE using TradingPartner.legacy_company_id = Order.company_id
-- Risk: LOW - no schema change, only data update
--
-- IMPORTANT: Run in batches for large tables to avoid long locks
-- ═══════════════════════════════════════════════════════════════════════════

-- Single UPDATE for small-medium tables (< 100K rows)
UPDATE your_schema.orders o
SET trading_partner_id = tp.id
FROM common_company.common_trading_partner tp
WHERE tp.tenant_id = o.tenant_id
  AND tp.legacy_company_id = o.company_id
  AND o.trading_partner_id IS NULL;  -- Only unmigrated rows

-- Log migration result
DO $$
DECLARE
    migrated_count INTEGER;
    unmigrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO migrated_count
    FROM your_schema.orders WHERE trading_partner_id IS NOT NULL;

    SELECT COUNT(*) INTO unmigrated_count
    FROM your_schema.orders WHERE trading_partner_id IS NULL AND company_id IS NOT NULL;

    RAISE NOTICE 'Migration result: % rows migrated, % rows unmigrated (no matching TradingPartner)',
        migrated_count, unmigrated_count;
END $$;
```

### Batch Migration (for large tables)

```sql
-- Batch migration for large tables (> 100K rows)
DO $$
DECLARE
    batch_size INTEGER := 10000;
    affected_rows INTEGER;
BEGIN
    LOOP
        UPDATE your_schema.orders o
        SET trading_partner_id = tp.id
        FROM common_company.common_trading_partner tp
        WHERE tp.tenant_id = o.tenant_id
          AND tp.legacy_company_id = o.company_id
          AND o.trading_partner_id IS NULL
          AND o.id IN (
              SELECT id FROM your_schema.orders
              WHERE trading_partner_id IS NULL AND company_id IS NOT NULL
              LIMIT batch_size
          );

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        RAISE NOTICE 'Batch migrated: % rows', affected_rows;

        EXIT WHEN affected_rows = 0;

        -- Small pause to reduce lock contention
        PERFORM pg_sleep(0.1);
    END LOOP;
END $$;
```

---

## STEP 3: Service Layer Dual-Read

### TradingPartnerResolver (Reusable Helper)

```java
/**
 * Helper for resolving partner IDs during migration transition.
 * Use in any service that needs to handle both company_id and trading_partner_id.
 */
@Component
@RequiredArgsConstructor
public class TradingPartnerResolver {

    private final TradingPartnerRepository partnerRepository;

    @Value("${feature.trading-partner.legacy-fallback:true}")
    private boolean legacyFallbackEnabled;

    /**
     * Resolve partner ID - handles both new and legacy IDs.
     *
     * @param tenantId Current tenant
     * @param partnerId Could be TradingPartner.id or legacy Company.id
     * @return TradingPartner ID (never null if partner exists)
     */
    public UUID resolvePartnerId(UUID tenantId, UUID partnerId) {
        // 1. Check if it's already a TradingPartner ID
        if (partnerRepository.existsByTenantIdAndId(tenantId, partnerId)) {
            return partnerId;
        }

        // 2. Check if it's a legacy company ID
        return partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId)
            .map(TradingPartner::getId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Trading partner not found: " + partnerId));
    }

    /**
     * Get effective partner ID from entity during transition.
     * Prefers trading_partner_id, falls back to company_id lookup.
     *
     * @param tradingPartnerId New FK (may be null during transition)
     * @param companyId Legacy FK (for unmigrated records)
     * @param tenantId Current tenant
     * @return Resolved TradingPartner ID
     */
    public UUID getEffectivePartnerId(UUID tradingPartnerId, UUID companyId, UUID tenantId) {
        if (tradingPartnerId != null) {
            return tradingPartnerId;
        }

        if (!legacyFallbackEnabled) {
            throw new IllegalStateException(
                "Legacy fallback disabled but trading_partner_id is null. " +
                "Record needs migration: company_id=" + companyId);
        }

        return partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, companyId)
            .map(TradingPartner::getId)
            .orElse(companyId);  // Return company_id if no mapping (edge case)
    }
}
```

### Service Layer Update Example

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TradingPartnerResolver partnerResolver;
    private final TradingPartnerService tradingPartnerService;

    @Value("${feature.trading-partner.legacy-fallback:true}")
    private boolean legacyFallbackEnabled;

    /**
     * Create order with TradingPartner support.
     */
    @Transactional
    public OrderDto createOrder(CreateOrderRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // Resolve partner ID (handles both new and legacy IDs)
        UUID tradingPartnerId = partnerResolver.resolvePartnerId(
            tenantId, request.getPartnerId());

        Order order = new Order();
        order.setTradingPartnerId(tradingPartnerId);

        // During transition: also set company_id for backward compatibility
        if (legacyFallbackEnabled) {
            tradingPartnerService.findById(tenantId, tradingPartnerId)
                .ifPresent(tp -> order.setCompanyId(tp.getLegacyCompanyId()));
        }

        return OrderDto.from(orderRepository.save(order));
    }

    /**
     * Get order with dual-read for partner.
     */
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Resolve effective partner ID for response
        UUID effectivePartnerId = partnerResolver.getEffectivePartnerId(
            order.getTradingPartnerId(),
            order.getCompanyId(),
            tenantId
        );

        // Get partner details for response
        TradingPartnerDto partner = tradingPartnerService
            .findById(tenantId, effectivePartnerId)
            .orElse(null);

        return OrderDto.from(order, partner);
    }

    /**
     * Find orders by partner (supports both IDs).
     */
    @Transactional(readOnly = true)
    public List<OrderDto> findByPartner(UUID partnerId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // Get TradingPartner to find legacy_company_id
        TradingPartnerDto partner = tradingPartnerService
            .findById(tenantId, partnerId)
            .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerId));

        // Query both new and legacy FK
        List<Order> orders;
        if (partner.getLegacyCompanyId() != null) {
            // Dual query during transition
            orders = orderRepository.findByTenantIdAndPartner(
                tenantId,
                partnerId,                    // trading_partner_id
                partner.getLegacyCompanyId()  // OR company_id
            );
        } else {
            // New partner (no legacy mapping)
            orders = orderRepository.findByTenantIdAndTradingPartnerId(
                tenantId, partnerId);
        }

        return orders.stream().map(OrderDto::from).toList();
    }
}
```

### Repository Query (Dual FK Support)

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Standard queries
    Optional<Order> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Order> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID tradingPartnerId);

    // Dual FK query for transition period
    @Query("""
        SELECT o FROM Order o
        WHERE o.tenantId = :tenantId
        AND (o.tradingPartnerId = :partnerId OR o.companyId = :legacyCompanyId)
        ORDER BY o.createdAt DESC
        """)
    List<Order> findByTenantIdAndPartner(
        @Param("tenantId") UUID tenantId,
        @Param("partnerId") UUID partnerId,
        @Param("legacyCompanyId") UUID legacyCompanyId
    );
}
```

---

## STEP 4: Feature Flag Cutover

### application.yml

```yaml
feature:
  trading-partner:
    # Enable legacy Company fallback for dual-read during migration
    # Set to false after migration is complete
    legacy-fallback: ${FEATURE_TRADING_PARTNER_LEGACY_FALLBACK:true}
```

### Cutover Checklist

- [ ] All transactional tables have `trading_partner_id` populated
- [ ] No new records created with only `company_id`
- [ ] All service queries use `TradingPartnerResolver`
- [ ] Monitoring shows zero legacy fallback hits for 7+ days
- [ ] Staging environment tested with `legacy-fallback: false`
- [ ] Production deployment plan reviewed

### Verification Query

```sql
-- Check migration completeness
SELECT
    'orders' AS table_name,
    COUNT(*) AS total,
    COUNT(trading_partner_id) AS migrated,
    COUNT(*) FILTER (WHERE trading_partner_id IS NULL AND company_id IS NOT NULL) AS unmigrated,
    ROUND(COUNT(trading_partner_id)::numeric / COUNT(*) * 100, 2) AS migration_pct
FROM your_schema.orders
WHERE company_id IS NOT NULL

UNION ALL

SELECT
    'invoices' AS table_name,
    COUNT(*) AS total,
    COUNT(trading_partner_id) AS migrated,
    COUNT(*) FILTER (WHERE trading_partner_id IS NULL AND company_id IS NOT NULL) AS unmigrated,
    ROUND(COUNT(trading_partner_id)::numeric / COUNT(*) * 100, 2) AS migration_pct
FROM your_schema.invoices
WHERE company_id IS NOT NULL;
```

---

## STEP 5: Drop Legacy Column (V044)

### SQL Template

```sql
-- ═══════════════════════════════════════════════════════════════════════════
-- V044__drop_company_id_from_orders.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- FINAL CLEANUP: Removes legacy company_id column after migration complete.
--
-- ⚠️  WARNING: This is IRREVERSIBLE. Ensure:
--   1. All records have trading_partner_id populated
--   2. feature.trading-partner.legacy-fallback = false tested
--   3. No services reference company_id
--   4. Backup exists
--
-- Risk: HIGH - requires full system test before deployment
-- ═══════════════════════════════════════════════════════════════════════════

-- Pre-flight check
DO $$
DECLARE
    unmigrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO unmigrated_count
    FROM your_schema.orders
    WHERE trading_partner_id IS NULL AND company_id IS NOT NULL;

    IF unmigrated_count > 0 THEN
        RAISE EXCEPTION 'Cannot drop company_id: % unmigrated records exist', unmigrated_count;
    END IF;
END $$;

-- Drop the legacy column
ALTER TABLE your_schema.orders
  DROP COLUMN IF EXISTS company_id;

-- Update comment
COMMENT ON TABLE your_schema.orders IS
'Orders table - migrated to TradingPartner FK (Faz 1.5 complete)';
```

### Entity Update (Final)

```java
@Entity
@Table(name = "orders", schema = "your_schema")
public class Order extends BaseEntity {

    // Legacy FK removed
    // @Column(name = "company_id")
    // private UUID companyId;

    // TradingPartner FK (NOT NULL after cleanup)
    @Column(name = "trading_partner_id", nullable = false)
    private UUID tradingPartnerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
    private TradingPartner tradingPartner;
}
```

---

## Checklist (Faz 1.5)

### Per-Table Checklist

- [ ] V042 migration: `trading_partner_id` column + FK + index added
- [ ] V043 migration: Existing data populated via `legacy_company_id`
- [ ] Entity updated with both fields during transition
- [ ] Service layer uses `TradingPartnerResolver` for dual-read
- [ ] Repository queries support both FK columns
- [ ] Verification query shows 100% migration
- [ ] Feature flag tested with `legacy-fallback: false`
- [ ] V044 migration: Legacy `company_id` dropped (final cleanup)

### Tables to Migrate (When Created)

| Table       | Schema     | Partner FK Purpose                | Priority |
| ----------- | ---------- | --------------------------------- | -------- |
| orders      | sales      | Customer who placed the order     | High     |
| invoices    | finance    | Customer/Supplier for invoice     | High     |
| shipments   | logistics  | Supplier/Customer destination     | Medium   |
| work_orders | production | Fason partner for outsourcing     | Medium   |
| payments    | finance    | Partner receiving/sending payment | Low      |

---

## Risk Assessment

| Migration | Risk   | Mitigation                               |
| --------- | ------ | ---------------------------------------- |
| V042      | Düşük  | Additive change, no data modification    |
| V043      | Düşük  | Data-only update, rollback = set to NULL |
| V044      | Yüksek | Irreversible, full system test required  |

---

## Related Files

- Migration templates: `src/main/resources/db/migration_templates/`
- TradingPartnerService: `common/platform/company/app/TradingPartnerService.java`
- TradingPartnerResolver: `common/platform/company/app/TradingPartnerResolver.java`
- Skill: `.cursor/skills/create-trading-partner/`
- V039-V041: Existing TradingPartner migrations
