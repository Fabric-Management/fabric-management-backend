# Human — HR Modülü

> Modül: Human (12-human)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Employee, Compliance, Leave, Payroll burada tanımlanır.

---

## Genel Bakış

HR modülü 4 alt alan içerir: çalışan master (core), HR uyumluluk (compliance — ülke bazlı policy pack), izin yönetimi (leave), bordro (payroll — ülke stratejileri). Self-service profil ve maaş bordrosu erişimi sağlar. Tüm tablolar `human` şemasında.

---

## Alt Modüller

### 1. Core — Employee

> Tablo: `human.human_employee`

| Alan | Tip | Açıklama |
|---|---|---|
| `userId` | UUID | FK → User — platform kullanıcısı ile eşleşme |
| `employeeNumber` | String | Tenant içinde benzersiz — otomatik (EmployeeNumberSequence) |
| `title` | Enum | Mr, Mrs, Ms... |
| `gender` | Enum | — |
| `emergencyContact` | Embeddable | Acil durum iletişim |
| `complianceStatus` | Enum | HR uyumluluk durumu |

**Event'ler:** `EmployeeUpdatedEvent`, `EmployeeTerminatedEvent`

### 2. Compliance — Ülke Bazlı Policy Pack

Ülkeye göre HR politikaları. Pack'ler oluşturulur, yayınlanır, emekli edilir.

| Entity | Tablo | Açıklama |
|---|---|---|
| `HrPolicyPack` | `human.human_hr_policy_pack` | Politika paketi — versiyon, status, ülke |
| `HrPolicyBinding` | — | Pack → kural bağlantısı |
| `HrCountryPackMapping` | — | Ülke → pack eşlemesi |
| `HrRuleVersion` | — | Kural versiyonları |
| `HrRuleAuditLog` | — | Kural değişiklik denetimi |

**API:** `/internal/hr/policy-packs` — @InternalEndpoint (hr-admin-ui).

### 3. Leave — İzin Yönetimi

| Entity | Tablo | Açıklama |
|---|---|---|
| `LeaveType` | `human.human_leave_type` | İzin tipleri (yıllık, hastalık vb.) |
| `HolidayCalendar` | `human.human_holiday_calendar` | Tatil takvimi (ülke bazlı) |
| `LeaveBalance` | `human.human_leave_balance` | Çalışan izin bakiyesi |
| `LeaveAccrualLog` | — | İzin hak ediş kaydı |

**Ülke Politikaları:** `LeavePolicy` interface — `EuLeavePolicy`, `TrLeavePolicy`, `UkLeavePolicy`, `GlobalLeavePolicy` implementasyonları. `LeavePolicyRegistry` ile doğru politika seçilir.

### 4. Payroll — Bordro

| Entity | Tablo | Açıklama |
|---|---|---|
| `PayPeriod` | `human.human_pay_period` | Ödeme dönemi (aylık) |
| `PayRun` | `human.human_pay_run` | Bordro çalıştırma |
| `PayRunEntry` | — | Bordro kalemleri |
| `PayRunPayout` | — | Ödeme kayıtları |
| `PayRunAuditLog` | — | Bordro denetim kaydı |

**Ülke Stratejileri:** `PayrollStrategy` interface — `TrPayrollStrategy`, `UkPayrollStrategy`, `DePayrollStrategy`, `EsPayrollStrategy`, `EuPayrollStrategy`, `FrPayrollStrategy`, `ItPayrollStrategy`, `UsPayrollStrategy`, `GlobalPayrollStrategy`.

---

## API ve Güvenlik

| Controller | Base Path | Güvenlik |
|---|---|---|
| EmployeeProfileController | `/api/human/employees` | `isAuthenticated()` |
| PayrollSelfServiceController | `/api/human/payroll/me` | `isAuthenticated()` |
| HrPolicyPackController | `/internal/hr/policy-packs` | `@InternalEndpoint` |

**Self-service:** `/api/human/employees/me` (profil), `/api/human/payroll/me/salary-slips` (bordro). TenantContext.getCurrentUserId() ile kullanıcıya özel.

---

## Diğer Modüllerle İlişki

- `common` (TenantContext, BaseEntity, auth/user) ile entegre.
- Doğrudan order, production, logistics, finance import'u yok — kendi içinde kapalı.
- İleride User ↔ Employee eşlemesi güçlendirilebilir.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan |
