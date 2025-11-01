# 🔔 Frontend Team Notification - Role & Department Architecture Update

**Date:** 2025-01-27  
**Priority:** ⚠️ **BREAKING CHANGES** - Requires Frontend Updates  
**Status:** ✅ Backend Ready - Frontend Integration Required

---

## 📋 Summary

Backend'de **Role & Department** yönetimi için dinamik, veritabanı tabanlı bir mimari kuruldu. Bu değişiklik, statik enum/string değerler yerine tam dinamik yönetim sağlıyor.

### 🎯 Key Changes

- ✅ **Role Management:** Artık veritabanından yönetiliyor (enum değil)
- ✅ **Department Categories:** Departmanlar kategorize edilebiliyor
- ✅ **User-Department:** Many-to-Many ilişki (kullanıcı birden fazla departmana atanabilir)
- ✅ **REST API:** Yeni endpoint'ler eklendi
- ⚠️ **User.department:** Deprecated (geçici olarak çalışıyor, yeni yapı kullanılmalı)

---

## 🔌 New API Endpoints

### Role Management

```http
GET    /api/common/roles                    # List all roles
GET    /api/common/roles/{id}               # Get role by ID
GET    /api/common/roles/code/{code}        # Get role by code (e.g., "ADMIN")
POST   /api/common/roles                    # Create new role
PUT    /api/common/roles/{id}               # Update role
DELETE /api/common/roles/{id}               # Deactivate role
```

**Request Body (POST/PUT):**

```json
{
  "roleName": "Manager",
  "roleCode": "MANAGER",
  "description": "Department management access"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "uid": "SYS-ROLE-001",
    "roleName": "Manager",
    "roleCode": "MANAGER",
    "description": "Department management access",
    "isActive": true
  }
}
```

### Department Category Management

```http
GET    /api/common/department-categories            # List all categories
GET    /api/common/department-categories/{id}      # Get category by ID
POST   /api/common/department-categories            # Create category
PUT    /api/common/department-categories/{id}        # Update category
DELETE /api/common/department-categories/{id}      # Deactivate category
```

**Request Body (POST/PUT):**

```json
{
  "categoryName": "Production",
  "description": "Production-related departments",
  "displayOrder": 1
}
```

### User-Department Assignment

```http
GET    /api/common/users/{userId}/departments              # List user's departments
GET    /api/common/users/{userId}/departments/primary      # Get primary department
POST   /api/common/users/{userId}/departments              # Assign department to user
PUT    /api/common/users/{userId}/departments/{deptId}/primary  # Set primary department
DELETE /api/common/users/{userId}/departments/{deptId}    # Remove assignment
```

**Request Body (POST):**

```json
{
  "departmentId": "uuid",
  "isPrimary": true
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "departmentId": "uuid",
    "isPrimary": true,
    "assignedAt": "2025-01-27T10:00:00Z",
    "assignedBy": "uuid"
  }
}
```

---

## ⚠️ Breaking Changes

### 1. User.department Field Deprecated

**❌ OLD (Deprecated):**

```typescript
interface User {
  id: string;
  department: string; // ❌ DEPRECATED - Remove this usage
  // ...
}
```

**✅ NEW (Recommended):**

```typescript
// Use endpoint: GET /api/common/users/{userId}/departments
interface UserDepartment {
  userId: string;
  departmentId: string;
  isPrimary: boolean;
  assignedAt: string;
  assignedBy?: string;
}
```

**Migration Path:**

- Mevcut `user.department` kullanımları çalışmaya devam edecek (geriye dönük uyumluluk)
- **Yeni geliştirmeler için** `/api/common/users/{userId}/departments` endpoint'ini kullanın
- Frontend'de `user.department` kullanımlarını bulup yeni API'ye geçirin

### 2. Role Assignment

**❌ OLD (If you were using enum):**

```typescript
enum Role {
  ADMIN = "ADMIN",
  MANAGER = "MANAGER",
}
```

**✅ NEW (Database-driven):**

```typescript
// Fetch from API: GET /api/common/roles
interface Role {
  id: string;
  roleName: string;
  roleCode: string; // "ADMIN", "MANAGER", etc.
  description: string;
  isActive: boolean;
}
```

**Migration:**

- Role enum'unuz varsa kaldırın
- Uygulama başlangıcında `/api/common/roles` endpoint'inden roller çekin
- Role seçimi için dropdown'da `roleCode` kullanın

---

## 🔄 Migration Checklist

### Immediate Actions

- [ ] **Role Dropdown:** `GET /api/common/roles` endpoint'inden roller çek
- [ ] **Department Dropdown:** `GET /api/common/departments` endpoint'ini kullan (zaten var)
- [ ] **User Profile:** `User.department` yerine `/api/common/users/{userId}/departments` kullan
- [ ] **User Creation:** Yeni kullanıcı oluştururken role ve department assignment yap

### User Interface Updates

- [ ] **User Form:**
  - Role dropdown (database-driven)
  - Multiple department selection
  - Primary department selection
- [ ] **User Profile Display:**
  - Show all departments (not just one)
  - Highlight primary department
  - Display role name from database

### Data Fetching

**Before:**

```typescript
// ❌ OLD
const user = await getUser(userId);
const department = user.department; // string
```

**After:**

```typescript
// ✅ NEW
const user = await getUser(userId);
const departments = await getUserDepartments(userId); // UserDepartment[]
const primaryDept = departments.find(d => d.isPrimary);
```

---

## 📝 JWT Token Changes

JWT token payload'ında `department` hala var (geriye dönük uyumluluk için), ancak **artık authorization için kullanılmamalı**.

**JWT Payload (Current):**

```json
{
  "sub": "user@example.com",
  "user_id": "uuid",
  "department": "production", // ⚠️ Display only - not for authorization
  "role_id": "uuid" // NEW - Role reference
}
```

**Important:**

- `department` field'ı sadece görsel amaçlı (display only)
- Authorization kararları backend'de `UserDepartment` junction table'dan yapılıyor
- Frontend'de JWT'den department okumak yerine `/api/common/users/{userId}/departments` endpoint'ini kullanın

---

## 🎨 Frontend Integration Example

### React/TypeScript Example

```typescript
// services/roleService.ts
export const roleService = {
  async getAllRoles(): Promise<Role[]> {
    const response = await api.get("/api/common/roles");
    return response.data.data; // ApiResponse wrapper
  },

  async getUserDepartments(userId: string): Promise<UserDepartment[]> {
    const response = await api.get(`/api/common/users/${userId}/departments`);
    return response.data.data;
  },

  async assignDepartment(
    userId: string,
    departmentId: string,
    isPrimary: boolean
  ): Promise<UserDepartment> {
    const response = await api.post(`/api/common/users/${userId}/departments`, {
      departmentId,
      isPrimary,
    });
    return response.data.data;
  },
};
```

### User Form Component Example

```typescript
// components/UserForm.tsx
const UserForm = () => {
  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedRole, setSelectedRole] = useState<string>("");
  const [selectedDepartments, setSelectedDepartments] = useState<string[]>([]);
  const [primaryDept, setPrimaryDept] = useState<string>("");

  useEffect(() => {
    // Fetch roles on mount
    roleService.getAllRoles().then(setRoles);
  }, []);

  const handleSubmit = async (userData: CreateUserRequest) => {
    // 1. Create user
    const user = await userService.createUser(userData);

    // 2. Assign role (if role assignment endpoint exists)
    if (selectedRole) {
      await userService.assignRole(user.id, selectedRole);
    }

    // 3. Assign departments
    for (const deptId of selectedDepartments) {
      await roleService.assignDepartment(
        user.id,
        deptId,
        deptId === primaryDept
      );
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {/* Role Selection */}
      <select
        value={selectedRole}
        onChange={e => setSelectedRole(e.target.value)}>
        {roles.map(role => (
          <option key={role.id} value={role.id}>
            {role.roleName}
          </option>
        ))}
      </select>

      {/* Department Selection (Multiple) */}
      {/* ... */}
    </form>
  );
};
```

---

## ✅ Backward Compatibility

**Good News:** Mevcut kodunuz çalışmaya devam edecek!

- `User.department` field'ı hala JWT'de ve UserDto'da var
- Eski endpoint'ler çalışıyor
- **Ancak** yeni özellikler için yeni endpoint'leri kullanmanız önerilir

**Timeline:**

- **Phase 1-5:** ✅ Completed (Backend ready)
- **Phase 6:** Policy refactor (Backend internal)
- **Phase 7:** Data migration (User.department → UserDepartment)

---

## 🐛 Known Issues

None - Backend is production-ready.

---

## 📞 Support

Questions or issues? Contact backend team or check:

- `docs/analysis/ROLE_DEPARTMENT_ARCHITECTURE_ANALYSIS.md` - Technical details
- `docs/analysis/Fabric_Management_Role_And_Department_Architecture_Action_Plan.md` - Implementation plan
- `docs/analysis/ROLE_DEPARTMENT_IMPLEMENTATION_STATUS.md` - Current status

---

## 🎯 Priority Actions for Frontend

1. **High Priority:**

   - [ ] Role dropdown'ları database-driven yap
   - [ ] User profile'da multiple departments göster
   - [ ] User form'da department assignment ekle

2. **Medium Priority:**

   - [ ] User.department kullanımlarını yeni API'ye geçir
   - [ ] Primary department selection UI ekle

3. **Low Priority:**
   - [ ] Department category filtering ekle
   - [ ] Role management UI (admin panel)

---

**Last Updated:** 2025-01-27  
**Backend Status:** ✅ Ready for Integration  
**Breaking Changes:** ⚠️ Minimal (backward compatible)
