# Onay Talebi & Kullanıcı Yükseltme

> Modül: Onay Sistemi (09-approval)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: ApprovalRequest ve UserPromotionRequest burada tanımlanır.

---

## Genel Bakış

ApprovalRequest işlem başına otomatik oluşur. `entityType + entityId` kombinasyonu sayesinde tek tablo tüm entity'leri kapsar (polimorfik FK). UserPromotionRequest kullanıcı seviye yükseltme tekliflerini ve red geçmişini tutar.

---

## 1. ApprovalRequest

> Tablo: `common_approval.approval_request`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `entityType` | Enum | Evet | WORK_ORDER / PURCHASE_ORDER / RECIPE_CREATE / BATCH_CLOSE / ... |
| `entityId` | UUID | Evet | İlgili kaydın id'si — polimorfik FK |
| `policyId` | UUID | Evet | FK → ApprovalPolicy |
| `requestedBy` | UUID | Evet | FK → User (işlemi yapan) |
| `approverId` | UUID | Evet | FK → User (onaylayacak kişi) |
| `status` | ApprovalRequestStatus (Enum) | Evet | PENDING / APPROVED / REJECTED / CANCELLED |
| `approvedAt` | Timestamp | Hayır | Onay zamanı |
| `rejectionReason` | String (TEXT) | Hayır | Red gerekçesi |
| `expiresAt` | Timestamp | Hayır | Onay süresi — dolunca CANCELLED |

> **Polimorfik FK kuralları:** `11-cross-cutting/polymorphic-fk-rules.md`

### Event'ler

| Geçiş | Event |
|---|---|
| Oluşturuldu | `ApprovalPending` → FlowBoard (APPROVAL task) + NotificationHub |
| APPROVED | `ApprovalApproved` → NotificationHub |
| REJECTED | `ApprovalRejected` → NotificationHub |

---

## 2. UserPromotionRequest

> Tablo: `common_approval.user_promotion_request`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `userId` | UUID | Evet | FK → User (yükseltilecek kişi) |
| `fromLevel` | Enum | Evet | Mevcut seviye |
| `toLevel` | Enum | Evet | Hedef seviye |
| `status` | Enum | Evet | PENDING / APPROVED / REJECTED |
| `triggeredBy` | Enum | Evet | SYSTEM / MANUAL |
| `approvedBy` | UUID | Hayır | FK → User (kararı veren admin) |
| `adminNote` | String (TEXT) | Hayır | Admin notu — 2. red'den itibaren zorunlu |
| `rejectionCount` | Integer | Evet | Bu kullanıcı için toplam red sayısı |
| `approvedTransactionCount` | Integer | Evet | Tetiklendiğindeki onaylı işlem sayısı |

---

## Eskalasyon Zinciri

| Red Sayısı | Sistem Davranışı | Admin Zorunluluğu |
|---|---|---|
| 1. Red | Sayaç sıfırlanır, kullanıcı PROBATION'da kalır | Not opsiyonel |
| 2. Red | Sayaç sıfırlanır | Not **zorunlu** |
| 3. Red | Hesap otomatik askıya alınır, HR'a bildirim | HR kararı beklenir |

**HR aksiyonları:** Hesabı reaktive edebilir (PROBATION'dan devam) veya kalıcı olarak kapatabilir.

---

## Yükseltme Akışı

```
N onaylanan işlem (promotionThreshold'a ulaşıldı)
       ↓
Sistem → Departman adminine bildirim: "Bu kullanıcı yükseltmeye hazır"
       ↓
Admin ONAYLAR              Admin REDDEDER
       ↓                          ↓
trustLevel yükseltilir     rejectionCount + 1
STANDARD / TRUSTED               ↓
                          1. red → sayaç sıfırla
                          2. red → not zorunlu + sayaç sıfırla
                          3. red → hesap askıya + HR bildirimi
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `09-approval/trust-level-policy.md` | ApprovalPolicy — hangi seviye onay gerektirir |
| `01-foundations/user-auth.md` | User.trustLevel |
| `02-production/work-order.md` | WorkOrder PENDING_APPROVAL akışı |
| `11-cross-cutting/polymorphic-fk-rules.md` | entityType + entityId kuralları |
| `11-cross-cutting/event-catalog.md` | ApprovalPending, ApprovalApproved, ApprovalRejected |

---

## Açık Kararlar

- [ ] `expiresAt` — onay talebi ne kadar süre açık kalacak?
- [ ] HR bildirimi e-posta mı, sistem içi bildirim mi, yoksa ikisi de?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — recipe-workorder-batch'ten ayrıldı |
