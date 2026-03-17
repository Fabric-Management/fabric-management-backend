-- =============================================================================
-- FabricOS — Evrensel Batch Tablosu (production_execution_batch)
-- =============================================================================
-- Bu script, Fiber/Yarn/Fabric vb. tüm fiziksel parti (lot) kayıtları için
-- tek bir evrensel tablo oluşturur. Modüle özel nitelikler JSONB "attributes"
-- kolonunda tutulur.
--
-- Flyway/Liquibase ile kullanım: Durum flyway_schema_history'de tutulduğu için
-- IF NOT EXISTS / DO $$ kullanılmaz; saf CREATE ile "ya hep ya hiç" felsefesi.
-- Bağımlılık: production şeması ve production.prod_material tablosu mevcut olmalıdır.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Status enum tipi (evrensel Batch durumları)
-- -----------------------------------------------------------------------------
CREATE TYPE production.batch_status AS ENUM (
    'AVAILABLE',   -- Stok serbest
    'RESERVED',    -- Üretim emrine rezerve
    'IN_PROGRESS', -- Üretimde kullanılıyor
    'QUARANTINE',  -- Karantina (kalite/tolerans dışı)
    'REJECTED',    -- Reddedildi
    'DEPLETED'     -- Tüm miktar tüketildi (terminal)
);

-- -----------------------------------------------------------------------------
-- 2. Tablo tanımı
-- -----------------------------------------------------------------------------
CREATE TABLE production.production_execution_batch (

    -- Birincil anahtar
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Çok kiracılı izolasyon
    tenant_id           UUID NOT NULL,

    -- İnsan okunur benzersiz kod (örn. BATCH-ACME-001); tenant içinde benzersiz
    uid                 VARCHAR(100) NOT NULL,

    -- Hangi malzemenin partisi (Fiber/Yarn/Fabric master kaydı Material üzerinden)
    material_id         UUID NOT NULL,

    -- Parti / lot kodu (izlenebilirlik); tenant bazında benzersiz olmalı
    batch_code         VARCHAR(100) NOT NULL,

    -- Miktar bilgileri (birim: unit)
    quantity            NUMERIC(15,3) NOT NULL,
    available_quantity  NUMERIC(15,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL,

    -- Parti durumu
    status              production.batch_status NOT NULL,

    -- Audit alanları
    created_at          TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    is_active           BOOLEAN NOT NULL DEFAULT true,

    -- Optimistic locking (JPA @Version karşılığı)
    version             INTEGER NOT NULL DEFAULT 0,

    -- Esnek nitelikler: modüle özel alanlar (fiber_grade, yarn_count vb.)
    attributes          JSONB NOT NULL DEFAULT '{}',

    -- Lokasyon: IWM entegrasyonu sonrası FK eklenecek; şimdilik nullable
    location_id         UUID NULL,

    -- Geçici metin lokasyon (IWM hazır olunca location_id ile eşlenip kaldırılacak)
    warehouse_location  VARCHAR(255) NULL,

    -- Material tablosuna FK
    CONSTRAINT fk_batch_material
        FOREIGN KEY (material_id)
        REFERENCES production.prod_material (id),

    -- Tenant + UID benzersizliği
    CONSTRAINT uk_batch_tenant_uid
        UNIQUE (tenant_id, uid),

    -- Tenant + batch_code iş kuralı benzersizliği
    CONSTRAINT uk_batch_tenant_batch_code
        UNIQUE (tenant_id, batch_code),

    -- Miktar tutarlılığı (available_quantity <= quantity uygulama tarafında garanti edilir; isteğe bağlı CHECK)
    CONSTRAINT chk_batch_quantity_non_negative
        CHECK (quantity >= 0 AND available_quantity >= 0)
);

-- -----------------------------------------------------------------------------
-- 3. Açıklayıcı tablo yorumu
-- -----------------------------------------------------------------------------
COMMENT ON TABLE production.production_execution_batch IS
'Evrensel parti/lot tablosu. Fiber, Yarn, Fabric vb. tüm fiziksel stok kayıtları bu tabloda tutulur. Modüle özel alanlar attributes (JSONB) içindedir.';

COMMENT ON COLUMN production.production_execution_batch.attributes IS
'Modüle özel nitelikler (örn. fiber_grade, yarn_count, width_cm). GIN index ile filtrelenebilir.';
COMMENT ON COLUMN production.production_execution_batch.location_id IS
'IWM lokasyon tablosu hazır olduğunda FK eklenecek; warehouse_location verisi bu alana taşınacak.';
COMMENT ON COLUMN production.production_execution_batch.warehouse_location IS
'Geçici metin lokasyon; Contract aşamasında location_id doldurulup bu kolon kaldırılacak.';
COMMENT ON COLUMN production.production_execution_batch.version IS
'Optimistic locking için; her güncellemede artırılmalı.';

-- -----------------------------------------------------------------------------
-- 4. İndeksler
-- -----------------------------------------------------------------------------

-- GIN: JSONB attributes üzerinde filtreleme/sorgulama performansı
CREATE INDEX idx_batch_attributes_gin
    ON production.production_execution_batch
    USING GIN (attributes);

-- Kiracı ve malzeme bazlı listeleme
CREATE INDEX idx_batch_tenant_id
    ON production.production_execution_batch (tenant_id);

CREATE INDEX idx_batch_material_id
    ON production.production_execution_batch (material_id);

-- Durum bazlı filtreleme (stok listeleri, rezerve/available)
CREATE INDEX idx_batch_status
    ON production.production_execution_batch (status);

-- Sık kullanılan bileşik filtre: tenant + status
CREATE INDEX idx_batch_tenant_status
    ON production.production_execution_batch (tenant_id, status);

-- Aktif kayıtlar (soft delete ile birlikte)
CREATE INDEX idx_batch_tenant_active
    ON production.production_execution_batch (tenant_id, is_active)
    WHERE is_active = true;

-- =============================================================================
-- Son
-- =============================================================================
