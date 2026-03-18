# Organization & Department

> Modül: Temel Yapılar (01-foundations)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Organization, Department, UserDepartment burada tanımlanır.

---

## Genel Bakış

Organization tenant'ın şirket yapısını, Department ise şirket içi bölümleri temsil eder. Department self-referential hiyerarşi destekler (üst-alt departman). Kullanıcı ↔ Departman ilişkisi `UserDepartment` junction tablosuyla yönetilir.

---

## 1. Department

> Tablo: `common_company.common_department`  
> `BaseEntity`'den miras alır.

| Alan (DB) | Java Alanı | Tip | Zorunlu | Açıklama |
|---|---|---|---|---|
| `organization_id` | `organizationId` | UUID | Evet | FK → Organization |
| `department_name` | `departmentName` | String (100) | Evet | Departman adı |
| `department_code` | `departmentCode` | String (50) | Evet | Benzersiz kod (tenant içinde) |
| `description` | `description` | String (500) | Hayır | Açıklama |
| `manager_id` | `managerId` | UUID | Hayır | FK → User — yönetici |
| `parent_department_id` | `parentDepartment` | Department (ManyToOne) | Hayır | Üst departman (hiyerarşi) |
| `is_system_department` | `isSystemDepartment` | Boolean | Evet | Sistem departmanı mı — varsayılan false |
| `display_order` | `displayOrder` | Integer | Hayır | Sıralama |

### İlişkiler

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_department_id")
private Department parentDepartment;

@OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
private List<UserDepartment> userDepartments = new ArrayList<>();
```

---

## 2. UserDepartment

> Tablo: `common_user.common_user_department`  
> User ↔ Department N:M ilişkisi.

| Alan | Tip | Açıklama |
|---|---|---|
| `userId` | UUID | FK → User |
| `departmentId` | UUID | FK → Department |
| `isPrimary` | Boolean | Kullanıcının ana departmanı mı |
| `assignedAt` | Instant | Atama zamanı |
| `assignedBy` | UUID | FK → User — atamayı yapan |

---

## Seed Verisi — Otomatik Departmanlar

Yeni tenant/organization oluşturulduğunda `TenantSeedService.seedDepartments()` çalışır. 5 ana grup ve alt departmanlar otomatik oluşturulur:

### Hiyerarşi

```
Production (parent)
  ├── R&D / Product Development
  ├── Production Planning
  ├── Fiber & Raw Material
  ├── Yarn Production
  ├── Weaving & Knitting
  ├── Dyeing & Finishing
  └── Quality Control

Administration (parent)
  ├── Human Resources
  ├── Finance & Accounting
  ├── Administration Office
  └── Management & Planning

Logistics (parent)
  ├── Warehouse
  ├── Procurement & Supply
  └── Shipping & Transport

Utility (parent)
  ├── Maintenance
  ├── Energy & Facilities
  └── Kitchen & Catering

Support (parent)
  ├── IT Services
  ├── Security
  └── Cleaning Services
```

---

## Yetkilendirme — ProductionAccessService

Production modülünde erişim `Role + Department` ile belirlenir:

| Role | Department | Yetki |
|---|---|---|
| ADMIN | (herhangi) | Tüm modüllere tam erişim |
| MANAGER | R&D / Product Dev. | FIBER WRITE |
| MANAGER | Warehouse | WAREHOUSE_LOCATION WRITE |
| SUPERVISOR | (departmanına göre) | Kısıtlı WRITE |
| WORKER | (departmanına göre) | Sadece READ |
| VIEWER | (herhangi) | Sadece READ |

---

## API

| Endpoint | Açıklama |
|---|---|
| `GET /api/common/departments` | Tenant'ın tüm departmanları |
| `GET /api/common/departments/organization/{orgId}` | Organizasyona göre |
| `GET /api/common/departments/{id}` | Departman detayı |

**Frontend:**
- `department.service.ts` — User departman atamaları
- `useUserRolesAndDepartments.ts` — Role + department hook

---

## İlişki Özeti

```
Organization
  └──→ Department (hiyerarşik, self-referential)
          ├──→ UserDepartment ──→ User (N:M)
          └──→ managerId ──→ User (yönetici)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | User entity — UserDepartment junction |
| `02-production/work-order.md` | WorkOrderAssignee.departmentId → Department |
| `07-flowboard/board-task.md` | TaskAssignee.departmentId → Department |
| `09-approval/trust-level-policy.md` | ApprovalPolicy.approverRole — department bazlı |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek kod yapısından türetildi |
