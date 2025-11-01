# Database Schema Visualization

Bu dökümantasyon, Fabric Management sisteminin veritabanı yapısını ve tablo ilişkilerini gösterir.

## Schema Yapısı

### 1. Common Platform Schemas

#### `common_company` - Şirket ve Abonelik Yönetimi

- **common_company**: Şirket bilgileri
- **common_department**: Departmanlar
- **common_os_definition**: OS tanımları (YarnOS, LoomOS, vb.)
- **common_subscription**: Kiracı abonelikleri
- **common_feature_catalog**: Özellik kataloğu
- **common_subscription_quota**: Abonelik kotaları

#### `common_user` - Kullanıcı Yönetimi

- **common_user**: Platform kullanıcıları

#### `common_auth` - Kimlik Doğrulama

- **common_auth_user**: Kimlik doğrulama bilgileri
- **common_refresh_token**: JWT yenileme tokenları
- **common_verification_code**: Doğrulama kodları

#### `common_policy` - Yetkilendirme

- **common_policy**: Politika tanımları (RBAC/ABAC)

#### `common_audit` - Denetim

- Denetim logları

### 2. Production Schemas

#### Fiber Master Data (Reference)

- **prod_fiber_category**: Fiber kategorileri (NATURAL_PLANT, NATURAL_ANIMAL, vb.)
- **prod_fiber_attribute**: Fiber özellikleri (durable, biodegradable, vb.)
- **prod_fiber_certification**: Sertifikalar (GOTS, OEKO-TEX, vb.)
- **prod_fiber_iso_code**: ISO 2076 kodları (CO, PES, PA, vb.)

#### Fiber Business Data

- **prod_material**: Material catalog (base table)
- **prod_fiber**: Fiber örnekleri (pure veya blend)
- **prod_fiber_composition**: Fiber karışım bileşimi (Many-to-Many)
- **prod_fiber_attribute_link**: Fiber-özellik ilişkisi (Many-to-Many)
- **prod_fiber_certification_link**: Fiber-sertifika ilişkisi (Many-to-Many)

#### Production Execution

- **production_execution_fiber_batch**: Fiber lot/parti takibi

## Relationship Diagram

```mermaid
erDiagram
    %% Common Schemas
    COMMON_COMPANY ||--o{ COMMON_USER : "has"
    COMMON_COMPANY ||--o{ COMMON_DEPARTMENT : "has"
    COMMON_COMPANY ||--o{ COMMON_SUBSCRIPTION : "has"
    COMMON_SUBSCRIPTION ||--o{ COMMON_SUBSCRIPTION_QUOTA : "has"
    COMMON_OS_DEFINITION ||--o{ COMMON_FEATURE_CATALOG : "defines"
    COMMON_OS_DEFINITION ||--o{ COMMON_SUBSCRIPTION : "powers"

    COMMON_USER ||--|| COMMON_AUTH_USER : "authenticates"
    COMMON_AUTH_USER ||--o{ COMMON_REFRESH_TOKEN : "has"

    %% Production Schemas - Reference Data
    PROD_FIBER_CATEGORY ||--o{ PROD_FIBER : "categorizes"
    PROD_FIBER_ISO_CODE ||--o{ PROD_FIBER : "codes"

    %% Production Schemas - Business Data
    PROD_MATERIAL ||--|| PROD_FIBER : "is"
    PROD_FIBER ||--o{ PROD_FIBER_COMPOSITION : "blends"
    PROD_FIBER_COMPOSITION }o--|| PROD_FIBER : "contains"

    PROD_FIBER ||--o{ PROD_FIBER_ATTRIBUTE_LINK : "has"
    PROD_FIBER_ATTRIBUTE ||--o{ PROD_FIBER_ATTRIBUTE_LINK : "describes"

    PROD_FIBER ||--o{ PROD_FIBER_CERTIFICATION_LINK : "has"
    PROD_FIBER_CERTIFICATION ||--o{ PROD_FIBER_CERTIFICATION_LINK : "certifies"

    %% Execution
    PROD_FIBER ||--o{ PRODUCTION_EXECUTION_FIBER_BATCH : "tracks"

    COMMON_COMPANY {
        uuid id PK
        uuid tenant_id
        string uid UK
        string company_name
        string tax_id UK
        string company_type
        uuid parent_company_id FK
    }

    COMMON_USER {
        uuid id PK
        uuid tenant_id
        string uid UK
        string first_name
        string last_name
        string contact_value UK
        uuid company_id FK
    }

    COMMON_AUTH_USER {
        uuid id PK
        uuid tenant_id
        string contact_value UK
        string password_hash
        boolean is_verified
    }

    COMMON_SUBSCRIPTION {
        uuid id PK
        uuid tenant_id
        string uid UK
        string os_code
        string status
        timestamp start_date
    }

    PROD_FIBER {
        uuid id PK
        uuid tenant_id
        string uid UK
        uuid material_id FK
        uuid fiber_category_id FK
        uuid fiber_iso_code_id FK
        string fiber_name
        string status
    }

    PROD_FIBER_COMPOSITION {
        uuid id PK
        uuid tenant_id
        uuid blended_fiber_id FK
        uuid base_fiber_id FK
        numeric percentage
    }

    PRODUCTION_EXECUTION_FIBER_BATCH {
        uuid id PK
        uuid tenant_id
        string uid UK
        uuid fiber_id FK
        string batch_code UK
        numeric quantity
        string status
    }

    PROD_FIBER_CATEGORY {
        uuid id PK
        string category_code UK
        string category_name
    }

    PROD_FIBER_ISO_CODE {
        uuid id PK
        string iso_code UK
        string fiber_name
        string fiber_type
    }
```

## Tablo İlişkileri Detayları

### 1. Common Platform Hierarchy

```
common_company (Company)
  ├─ common_user (Users)
  │   └─ common_auth_user (Authentication)
  ├─ common_department (Departments)
  └─ common_subscription (Subscriptions)
      └─ common_subscription_quota (Quotas)
```

### 2. Fiber Domain Hierarchy

```
prod_fiber_category (Reference)
prod_fiber_attribute (Reference)
prod_fiber_certification (Reference)
prod_fiber_iso_code (Reference)
    ↓
prod_material (Base Catalog)
    ↓
prod_fiber (Concrete Fiber Instances)
  ├─ prod_fiber_composition (Blend Composition)
  ├─ prod_fiber_attribute_link (Attributes)
  ├─ prod_fiber_certification_link (Certifications)
  └─ production_execution_fiber_batch (Physical Batches)
```

## Foreign Key Relationships

### Production Execution → Fiber Master Data

```sql
-- Fiber Batch references Fiber Master Data
production_execution_fiber_batch.fiber_id
  → prod_fiber.id
```

### Fiber → Material

```sql
-- Each fiber is a material
prod_fiber.material_id
  → prod_material.id (1:1)
```

### Fiber Composition

```sql
-- Many-to-Many: Blended fibers contain base fibers
prod_fiber_composition.blended_fiber_id
  → prod_fiber.id

prod_fiber_composition.base_fiber_id
  → prod_fiber.id
```

### Fiber Attributes

```sql
-- Many-to-Many: Fibers have attributes
prod_fiber_attribute_link.fiber_id
  → prod_fiber.id

prod_fiber_attribute_link.attribute_id
  → prod_fiber_attribute.id
```

### Fiber Certifications

```sql
-- Many-to-Many: Fibers have certifications
prod_fiber_certification_link.fiber_id
  → prod_fiber.id

prod_fiber_certification_link.certification_id
  → prod_fiber_certification.id
```

## Schema Bazlı Gruplama

### Common Schemas (Platform Layer)

- **common_company**: Şirket yönetimi
- **common_user**: Kullanıcı yönetimi
- **common_auth**: Kimlik doğrulama
- **common_policy**: Yetkilendirme
- **common_audit**: Denetim

### Production Schemas (Business Layer)

- **prod*fiber*\***: Fiber referans tabloları
- **prod_fiber**: Fiber iş verileri
- **production*execution*\***: Üretim yürütme

## Tenant Isolation

Tüm tablolarda `tenant_id` sütunu bulunur:

- Multi-tenant yapı
- Veri izolasyonu
- Özel olarak işaretlenen `SYSTEM_TENANT_ID = 00000000-0000-0000-0000-000000000000` platform seviyesi referans verileri için

## Indexes

Her tabloda yaygın indeksler:

- `idx_{table}_tenant_id`: Tenant bazlı sorgular
- `idx_{table}_tenant_id_active`: Aktif kayıtlar için composite index
- Unique constraints: `uid`, `contact_value`, vb.
- Foreign key indexes: FK sütunları için
