# Code Review Raporu: Notification Modülü (N4, N8, N9, C.2)

**Kapsam:** InAppNotificationService, BatchQcEventListener, NotificationBell, NotificationDropdown, notifications page, notification.service, format-relative-time, resolve-notification-link  
**Tarih:** 2026-03-11  
**Skill:** `agents/skills/senior-code-review`

---

## Özet

Notification modülü (backend + frontend) genel olarak temiz ve işlevsel. Kritik bulgu yok. Orta seviyede: DRY ihlali (NOTIFICATION_ICONS 3 yerde tekrarlanıyor), ölü kod (InAppNotificationService.listForRecipient overload), tip güvenliği (non-null assertion, hata sessizce yutuluyor). Düşük: Magic string (role codes), `isSidebarOpen` kullanımı, format-relative-time edge case (gelecek tarih). Performans/güvenlik: Polling ve N bildirim kaydı BACKLOG'da TB-2/TB-3 olarak işaretli; ek bulgu yok.

---

## 1. Ölü ve İşlevsiz Kodlar (Dead Code & Cleanup)

### Orta

- **InAppNotificationService.listForRecipient(UUID, Pageable) — hiçbir yerde çağrılmıyor**
  - **Ne:** `listForRecipient(recipientId, pageable)` overload'u tanımlı; controller sadece `listForRecipient(recipientId, unread, pageable)` kullanıyor.
  - **Neden:** Eski API veya refactor sonrası kalan kod. Kullanılmadığı sürece bakım yükü ve karışıklık yaratır.
  - **Öneri:** Overload'u kaldırın veya `@Deprecated` + "Use listForRecipient(recipientId, unread, pageable) instead" yorumu ekleyin.

### Düşük

- **Gereksiz import:** İncelenen dosyalarda kullanılmayan import yok; temiz.

---

## 2. Kod Kalitesi ve Temiz Kod (Clean Code & Architecture)

### Orta

- **NOTIFICATION_ICONS + getNotificationIcon — 3 yerde tekrarlanıyor (DRY ihlali)**
  - **Ne:** `NotificationDropdown.tsx`, `notifications/page.tsx` ve muhtemelen başka yerlerde aynı `NOTIFICATION_ICONS` map ve `getNotificationIcon` fonksiyonu kopyalanmış.
  - **Neden:** Yeni NotificationType eklendiğinde 3 yerde güncelleme gerekir; tutarsızlık riski.
  - **Öneri:** `features/notifications/utils/notification-icons.ts` oluşturup tek kaynak yapın; dropdown ve page buradan import etsin.

- **BatchQcEventListener — Magic string "ADMIN", "MANAGER"**
  - **Ne:** `Set.of("ADMIN", "MANAGER")` doğrudan listener içinde.
  - **Neden:** Rol kodları değişirse veya başka listener'larda kullanılırsa tekrar eder; merkezi tanım yok.
  - **Öneri:** `InAppNotificationService` veya ortak constants'ta `QUARANTINE_NOTIFY_ROLES` gibi bir sabit tanımlayın; listener oradan kullansın.

### Düşük

- **NotificationBell — isSidebarOpen sadece className için kullanılıyor**
  - **Ne:** `isSidebarOpen` prop'u sadece `!isSidebarOpen && "w-full"` için kullanılıyor; dropdown açıkken sidebar durumu değişse bile davranış aynı.
  - **Neden:** İsim "sidebar open" olsa da aslında "layout compact/expanded" bilgisi. Şu an için sorun yok; ileride dropdown konumlandırma farklılaşırsa anlamlı olur.
  - **Öneri:** Mevcut kullanım kabul edilebilir; değişiklik gerekmez.

---

## 3. Tip Güvenliği ve Hata Yönetimi (Type Safety & Error Handling)

### Orta

- **notification.service.ts — response.data.data! non-null assertion**
  - **Ne:** `return response.data.data!` ve `return response.data.data!.count` — `data` optional olduğu için runtime'da undefined olabilir.
  - **Neden:** API hata döndüğünde (401, 500, error payload) `data` undefined olur; `!` ile zorlanan erişim TypeError fırlatır. Caller catch'te yakalasa da hata mesajı anlamlı olmaz.
  - **Öneri:** `const data = response.data?.data; if (data == null) throw new Error('Notification API returned no data'); return data;` veya benzeri guard. Ya da `response.data?.data ?? (throw new Error(...))` (TS 5.0+).

- **NotificationBell.fetchUnreadCount — catch boş, hata sessizce yutuluyor**
  - **Ne:** `catch { // Silently ignore - user may be logged out }` — hiç log yok.
  - **Neden:** Debug sırasında "neden count 0?" sorusu zorlaşır; logout dışında (network, 500) hatalar da aynı şekilde yutuluyor.
  - **Öneri:** En azından `log.debug` veya `console.debug` ile "getUnreadCount failed" loglayın; production'da log level ile kapatılabilir.

- **NotificationDropdown.handleNotificationClick — markAsRead hatası yakalanmıyor**
  - **Ne:** `await notificationService.markAsRead(n.id)` — hata olursa unhandled rejection; kullanıcı yine de navigate ediyor.
  - **Neden:** API hatası durumunda kullanıcı sayfaya gider ama bildirim okundu olarak işaretlenmemiş olabilir; UX tutarsız.
  - **Öneri:** try/catch ile sarmalayın; hata durumunda toast veya sessiz retry; yine de navigate edin (fail-open).

### Düşük

- **format-relative-time — Gelecek tarih edge case**
  - **Ne:** `differenceInMinutes(now, d)` — `d` gelecekteyse negatif değer döner; `minutes < 1` → "now", `minutes < 60` → negatif "Xm" gibi garip çıktı.
  - **Neden:** Bildirimler genelde geçmişte oluşturulur; pratikte nadir. Ama tip güvenliği açısından sınır kontrolü eklenebilir.
  - **Öneri:** `if (minutes < 0) return "now";` veya `Math.max(0, minutes)` ile negatifi kesin.

---

## 4. Performans ve Güvenlik (Performance & Security)

### Düşük (BACKLOG'da zaten kayıtlı)

- **Polling (TB-2):** 10 sn'de bir getUnreadCount — kullanıcı sayısı arttığında SSE'ye geçilmesi önerilir. Mevcut implementasyon doğru; interval cleanup (`clearInterval`) yapılmış.
- **sendToTenantRoles N×N kayıt (TB-3):** N kullanıcıya N bildirim — BACKLOG'da recipientId=null optimizasyonu olarak işaretli.
- **N+1:** InAppNotificationService.sendToTenantRoles döngüde `send()` çağırıyor; her send ayrı transaction. Şu an için kabul edilebilir; ileride batch insert düşünülebilir.
- **Güvenlik:** Kullanıcı girdisi doğrudan DOM'a verilmiyor; React escape ediyor. referenceId/referenceType backend'den geliyor; path'e interpolate ediliyor — UUID formatında olduğu sürece injection riski düşük.

---

## Özet Tablo

| Önem  | Konu                                      | Dosya / Alan                          |
|-------|-------------------------------------------|---------------------------------------|
| Orta  | Kullanılmayan listForRecipient overload   | InAppNotificationService.java         |
| Orta  | NOTIFICATION_ICONS DRY ihlali             | NotificationDropdown, notifications/page |
| Orta  | Magic string "ADMIN","MANAGER"            | BatchQcEventListener                  |
| Orta  | response.data.data! non-null assertion    | notification.service.ts               |
| Orta  | fetchUnreadCount sessiz catch              | NotificationBell.tsx                  |
| Orta  | handleNotificationClick markAsRead catch  | NotificationDropdown.tsx              |
| Düşük | format-relative-time negatif değer        | format-relative-time.ts               |
| Düşük | isSidebarOpen sadece className            | NotificationBell.tsx                  |

---

## Düzeltilmiş / Refactor Edilmiş Kod

### 1. NOTIFICATION_ICONS tek kaynak (DRY)

**Yeni dosya: `features/notifications/utils/notification-icons.tsx`**

```tsx
import React from "react";
import {
  Bell,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  UserPlus,
  FileText,
} from "lucide-react";
import type { NotificationType } from "@/types";

export const NOTIFICATION_ICONS: Record<
  NotificationType,
  { icon: React.ElementType; className: string }
> = {
  FIBER_REQUEST_SUBMITTED: { icon: FileText, className: "text-amber-500" },
  FIBER_REQUEST_APPROVED: { icon: CheckCircle2, className: "text-emerald-500" },
  FIBER_REQUEST_REJECTED: { icon: AlertCircle, className: "text-red-500" },
  BATCH_QC_COMPLETED: { icon: CheckCircle2, className: "text-emerald-500" },
  BATCH_QUARANTINE: { icon: AlertTriangle, className: "text-amber-500" },
  BATCH_OVERRIDE_REQUIRED: { icon: AlertCircle, className: "text-orange-500" },
  NEW_TENANT_ONBOARDED: { icon: UserPlus, className: "text-blue-500" },
};

export function getNotificationIcon(type: NotificationType) {
  return NOTIFICATION_ICONS[type] ?? { icon: Bell, className: "text-zinc-500" };
}
```

**NotificationDropdown.tsx ve notifications/page.tsx:** Bu dosyadan import edin; yerel tanımları kaldırın.

---

### 2. notification.service.ts — Tip güvenliği

```typescript
// getNotifications
const data = response.data?.data;
if (!data) {
  throw new Error("Notification API returned no data");
}
return data;

// getUnreadCount
const data = response.data?.data;
if (data == null || typeof data.count !== "number") {
  throw new Error("Unread count API returned invalid data");
}
return data.count;
```

---

### 3. NotificationBell — fetchUnreadCount log

```typescript
const fetchUnreadCount = useCallback(async () => {
  try {
    const count = await notificationService.getUnreadCount();
    setUnreadCount(count);
  } catch (err) {
    if (process.env.NODE_ENV === "development") {
      console.debug("getUnreadCount failed", err);
    }
  }
}, []);
```

---

### 4. NotificationDropdown — markAsRead try/catch

```typescript
const handleNotificationClick = async (n: NotificationDto) => {
  if (!n.isRead) {
    try {
      await notificationService.markAsRead(n.id);
    } catch {
      // Fail-open: still navigate; user can mark read from list page
    }
  }
  onClose();
  onNavigate(resolveNotificationLink(n));
};
```

---

### 5. format-relative-time — Negatif değer guard

```typescript
export function formatRelativeTimeShort(date: Date | string): string {
  const d = typeof date === "string" ? new Date(date) : date;
  const now = new Date();

  const minutes = Math.max(0, differenceInMinutes(now, d));
  const hours = differenceInHours(now, d);
  const days = differenceInDays(now, d);

  if (minutes < 1) return "now";
  // ... rest unchanged
}
```

---

### 6. InAppNotificationService — Kullanılmayan overload

**Seçenek A — Sil:** `listForRecipient(UUID, Pageable)` overload'unu tamamen kaldırın.

**Seçenek B — Deprecate:** Geçiş süresi için:

```java
/**
   * @deprecated Use {@link #listForRecipient(UUID, Boolean, Pageable)} instead.
   */
  @Deprecated
  @Transactional(readOnly = true)
  public Page<Notification> listForRecipient(UUID recipientId, Pageable pageable) {
    return listForRecipient(recipientId, null, pageable);
  }
```

---

### 7. BatchQcEventListener — Role sabiti

**InAppNotificationService.java:**

```java
/** Roles notified when batch enters quarantine */
public static final Set<String> QUARANTINE_NOTIFY_ROLES = Set.of("ADMIN", "MANAGER");
```

**BatchQcEventListener:**

```java
notificationService.sendToTenantRoles(
    event.getTenantId(),
    InAppNotificationService.QUARANTINE_NOTIFY_ROLES,
    ...
);
```

(Not: `QUARANTINE_NOTIFY_ROLES` public static final olarak InAppNotificationService'te tanımlanırsa BatchQcEventListener oradan kullanır.)

---

## Sonuç

Notification modülü production için uygun; kritik bloker yok. Orta seviye maddeler sürdürülebilirlik ve hata ayıklama açısından iyileştirilmeli. DRY (NOTIFICATION_ICONS) ve tip güvenliği (non-null assertion, catch stratejisi) öncelikli düzeltmeler olarak önerilir.
