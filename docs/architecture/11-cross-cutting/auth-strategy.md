# Yetkilendirme Stratejisi

> Modül: Cross-Cutting (11-cross-cutting)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Modül bazlı yetkilendirme stratejisi burada tanımlanır.

---

## Genel Bakış

Sistemde yetkilendirme tutarsız: Production detaylı (role+department), diğer modüller sadece `isAuthenticated()` veya hiçbir kontrol yok. Bu döküman mevcut durumu belgeliyor ve hedef mimariyi tanımlıyor.

---

## Mevcut Durum

| Modül | Mevcut Yetkilendirme | Risk |
|---|---|---|
| Production | `ProductionAccessService` — role + department, detaylı | ✓ Güvenli |
| Order | Yok — `@PreAuthorize` yok | ⚠ Herhangi authenticated kullanıcı CRUD yapabilir |
| Logistics | Yok — `@PreAuthorize` yok | ⚠ Aynı risk |
| Finance | Yok — `@PreAuthorize` yok | ⚠ Fatura lifecycle korumasız |
| Human | `isAuthenticated()` + `@InternalEndpoint` | △ Self-service güvenli, admin endpoint'leri tartışılır |

---

## Hedef Mimari — Modül Bazlı Access Service

Her modül kendi AccessService'ini tanımlar. Pattern: Production'daki model referans alınır.

```
production/ → ProductionAccessService    ✓ MEVCUT
order/      → OrderAccessService         ⬜ YENİ
logistics/  → LogisticsAccessService     ⬜ YENİ
finance/    → FinanceAccessService       ⬜ YENİ
human/      → HumanAccessService         ⬜ YENİ
```

### Ortak Pattern

```java
@Service
public class OrderAccessService {
    
    // Production ile aynı pattern
    public boolean hasPermission(Authentication auth, String module, String action) {
        // 1. ADMIN/PLATFORM_ADMIN → her zaman true
        // 2. READ → role + department kontrolü
        // 3. WRITE → role + module-specific department kontrolü
    }
}

// Controller'da:
@PreAuthorize("@orderAccessService.hasPermission(authentication, 'SALES_ORDER', 'WRITE')")
```

---

## Önerilen Yetki Matrisleri

### Order Modülü

| Role | Department | SALES_ORDER | PURCHASE_ORDER |
|---|---|---|---|
| ADMIN | herhangi | R+W | R+W |
| MANAGER | Sales/Marketing | R+W | R |
| MANAGER | Procurement | R | R+W |
| MANAGER | Production Planning | R+W | R+W |
| SUPERVISOR | Sales/Marketing | R+W | R |
| WORKER | herhangi | R | R |

### Logistics Modülü

| Role | Department | SHIPMENT |
|---|---|---|
| ADMIN | herhangi | R+W |
| MANAGER | Shipping & Transport | R+W |
| MANAGER | Warehouse | R+W |
| MANAGER | Sales/Marketing | R (giden sevkiyat izleme) |
| SUPERVISOR | Shipping & Transport | R+W |
| WORKER | Warehouse | R |

### Finance Modülü

| Role | Department | INVOICE |
|---|---|---|
| ADMIN | herhangi | R+W |
| MANAGER | Finance & Accounting | R+W |
| MANAGER | Administration Office | R+W |
| MANAGER | Sales/Marketing | R (AR faturalarını görebilir) |
| MANAGER | Procurement | R (AP faturalarını görebilir) |
| SUPERVISOR | Finance & Accounting | R+W |
| WORKER | herhangi | — (erişim yok) |

### Human Modülü

| Role | Endpoint | Yetki |
|---|---|---|
| herhangi (authenticated) | `/api/human/employees/me` | Kendi profilini görür |
| herhangi (authenticated) | `/api/human/payroll/me` | Kendi bordrosunu görür |
| ADMIN / HR Manager | `/internal/hr/*` | HR admin işlemleri |
| MANAGER | Leave onay | Departmanındaki çalışanlar |

---

## Implementasyon Önceliği

| Modül | Öncelik | Gerekçe |
|---|---|---|
| Finance | Yüksek | Fatura = para, korumasız kalmamalı |
| Order | Yüksek | Sipariş lifecycle koruması |
| Logistics | Orta | Sevkiyat lifecycle koruması |
| Human | Düşük | Self-service zaten kısmen güvenli |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/production-security.md` | Referans implementasyon — ProductionAccessService |
| `01-foundations/user-auth.md` | Role enum, AuthenticatedUserContext |
| `01-foundations/organization-department.md` | Department kodları |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — mevcut durum + hedef mimari |
