# BaseEntity — Ortak Varlık Yapısı

> Modül: Temel Yapılar (01-foundations)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Bu döküman BaseEntity'nin TEK tanım yeridir.

---

## Genel Bakış

Tüm entity'ler `BaseEntity`'den miras alır. Aşağıdaki 10 alan her tabloda bulunur ve entity tanımlarında tekrar edilmez. Multi-tenancy, soft delete, optimistic locking ve denetim izleri bu katmanda çözülür.

---

## BaseEntity Alanları

| Alan (DB) | Java Alanı | Tip | Açıklama |
|---|---|---|---|
| `id` | `id` | UUID | Birincil anahtar — otomatik üretilir |
| `tenant_id` | `tenantId` | UUID | Multi-tenant izolasyonu — zorunlu |
| `uid` | `uid` | String | İnsan okunabilir benzersiz kod |
| `created_at` | `createdAt` | Instant | Oluşturulma zamanı — otomatik |
| `created_by` | `createdBy` | UUID | FK → User — oluşturan kullanıcı |
| `updated_at` | `updatedAt` | Instant | Son güncelleme zamanı — otomatik |
| `updated_by` | `updatedBy` | UUID | FK → User — son güncelleyen |
| `is_active` | `isActive` | Boolean | Soft delete / pasif durumu — varsayılan true |
| `deleted_at` | `deletedAt` | Instant | Soft delete zamanı — null ise silinmemiş |
| `version` | `version` | Long | Optimistic locking — `@Version` |

---

## Multi-Tenancy

Her entity `tenantId` taşır. Tüm sorgular tenant kapsamında yapılır — cross-tenant veri erişimi mümkün değildir.

**Kural:** `tenantId` entity oluşturulurken atanır, sonradan değiştirilemez.

---

## Soft Delete Politikası

Sistemde **hard delete yapılmaz**. Tüm silme işlemleri soft delete ile gerçekleştirilir:

```
Silme isteği geldiğinde:
  isActive = false
  deletedAt = now()
  Kayıt veritabanında kalır, sorgularda filtrelenir
```

**Gerekçe:** Polimorfik FK kullanan entity'ler (ApprovalRequest, CostCalculation, Task, StockTransaction) kaynak entity silindiğinde orphan kalır. Soft delete bu riski ortadan kaldırır.

> **Detay:** `11-cross-cutting/polymorphic-fk-rules.md`

---

## UID Formatı

Her entity için insan okunabilir benzersiz kod üretilir.

**Format:** `{TENANT_UID}-{ENTITY_PREFIX}-{SEQUENCE}`

**Örnekler:**
```
ACME-001-USER-00042       → Kullanıcı
ACME-001-WO-00001         → WorkOrder
ACME-001-BATCH-00042      → Batch
ACME-001-GR-00001         → GoodsReceipt
```

**Kurallar:**
- `uid` benzersizdir — aynı tenant içinde tekrar edemez.
- Bir kez atandığında değiştirilemez.
- Arama ve filtreleme için indekslenir.

---

## Optimistic Locking

`@Version` anotasyonu ile yönetilir. İki kullanıcı aynı kaydı aynı anda güncellemeye çalışırsa, ikincisi `OptimisticLockException` alır.

```java
@Version
@Column(name = "version")
private Long version;
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | `createdBy`, `updatedBy` → User FK referansı |
| `11-cross-cutting/polymorphic-fk-rules.md` | Soft delete zorunluluğunun detaylı gerekçesi |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek kod yapısından türetildi |
