# Kullanıcı Güven Seviyesi & Onay Politikası

> Modül: Onay Sistemi (09-approval)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: UserTrustLevel ve ApprovalPolicy burada tanımlanır.

---

## Genel Bakış

Bu sistem kullanıcı olgunluk seviyesine göre dinamik denetim sağlar. WorkOrder'a özgü değil — tüm kritik işlemler için geçerlidir. Yeni kullanıcılar PROBATION'dan başlar, belirli sayıda onaylanan işlem sonrası yükseltme önerilir.

---

## UserTrustLevel (Enum)

User entity'sine eklenecek alan (bkz. `01-foundations/user-auth.md`):

| Seviye | Açıklama | Başlangıç |
|---|---|---|
| `PROBATION` | Yeni kullanıcı — tüm kritik işlemler denetimde | Tüm yeni kullanıcılar |
| `STANDARD` | Normal — yalnızca yüksek riskli işlemler | Admin onayı ile yükseltilir |
| `TRUSTED` | Kıdemli — çoğu işlem serbestçe | Admin onayı ile yükseltilir |

---

## ApprovalPolicy

> Tablo: `common_approval.approval_policy`  
> `BaseEntity`'den miras alır.  
> Tenant tarafından yapılandırılır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `entityType` | Enum | Evet | WORK_ORDER / PURCHASE_ORDER / RECIPE_CREATE / BATCH_CLOSE / ... |
| `requiredForLevel` | Enum | Evet | PROBATION / STANDARD / ALL |
| `approverRole` | Enum | Evet | DEPARTMENT_ADMIN / MANAGER / HR |
| `promotionThreshold` | Integer | Evet | Kaç onaylanan işlem sonrası yükseltme önerilsin — varsayılan 10 |
| `isActive` | Boolean | Evet | Politika aktif mi |

### Örnek Politikalar

| entityType | requiredForLevel | Açıklama |
|---|---|---|
| `WORK_ORDER` | `PROBATION` | Yeni kullanıcı WO oluşturursa onay gerekir |
| `PURCHASE_ORDER` | `STANDARD` | Standard kullanıcı PO oluşturursa da onay gerekir |
| `RECIPE_CREATE` | `PROBATION` | Yeni kullanıcı recipe oluşturursa onay gerekir |
| `BATCH_CLOSE` | `ALL` | Batch kapatma herkese onay gerektirir |

---

## Karar Akışı

```
Kullanıcı kritik işlem yapar (ör. WorkOrder oluştur)
        ↓
Sistem kontrol eder:
  user.trustLevel = ?
  ApprovalPolicy.requiredForLevel bu seviyeyi kapsıyor mu?
        ↓
Kapsıyorsa → ApprovalRequest oluşturulur
              Entity status → PENDING_APPROVAL
Kapsamıyorsa → Direkt devam (DRAFT → SENT)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | User.trustLevel alanı |
| `09-approval/approval-request.md` | ApprovalRequest + UserPromotionRequest |
| `02-production/work-order.md` | WorkOrder PENDING_APPROVAL akışı |

---

## Açık Kararlar

- [ ] `promotionThreshold` varsayılan değeri tenant bazında yapılandırılacak (öneri: 10)
- [ ] Performans metrikleri (FlowBoard) yükseltme kararında kullanılacak mı?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — recipe-workorder-batch'ten ayrıldı |
