# Role, Department Category, Department & Position API Reference

**Last Updated:** 2025-01-XX  
**Status:** Production-Ready  
**Backend Endpoints:** `/api/common/roles`, `/api/common/department-categories`, `/api/common/departments`, `/api/common/positions`

---

## Overview

This document describes the backend API for managing roles, department categories, departments, and positions. These entities are used in user creation and organizational structure management.

**Key Points:**
- **Roles:** User roles for authorization and permissions
- **Department Categories:** Organizational categories (e.g., "Operations", "Support")
- **Departments:** Organizational units within a company (e.g., "Production", "Sales")
- **Positions:** Job positions within departments (e.g., "Production Manager", "Sales Representative")

**Note:** Departments and Positions are automatically seeded for new tenants. Manual creation endpoints are not currently available (see below).

---

## 1. Roles

### Get All Roles

**Endpoint:** `GET /api/common/roles`

**Purpose:** Get all roles for current tenant.

**Request Example:**

```http
GET /api/common/roles
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid...",
      "roleName": "Admin",
      "roleCode": "ADMIN",
      "description": "System administrator with full access",
      "isActive": true
    },
    {
      "id": "uuid...",
      "roleName": "HR Manager",
      "roleCode": "HR_MANAGER",
      "description": "Human resources manager",
      "isActive": true
    }
  ]
}
```

**Caching:** Response is cached for 5 minutes (tenant-scoped)

---

### Get Role by ID

**Endpoint:** `GET /api/common/roles/{id}`

**Request Example:**

```http
GET /api/common/roles/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "roleName": "Admin",
    "roleCode": "ADMIN",
    "description": "System administrator with full access",
    "isActive": true
  }
}
```

---

### Get Role by Code

**Endpoint:** `GET /api/common/roles/code/{code}`

**Request Example:**

```http
GET /api/common/roles/code/ADMIN
```

**Response:**

Same as Get Role by ID.

---

### Create Role

**Endpoint:** `POST /api/common/roles`

**Purpose:** Create a new role.

**Request Body:**

```json
{
  "roleName": "Department Manager",
  "roleCode": "DEPT_MANAGER",
  "description": "Department manager with departmental access"
}
```

**Validation:**
- `roleName` (required, max 100 characters)
- `roleCode` (required, max 50 characters, must be unique)
- `description` (optional, max 500 characters)

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "roleName": "Department Manager",
    "roleCode": "DEPT_MANAGER",
    "description": "Department manager with departmental access",
    "isActive": true
  },
  "message": "Role created successfully"
}
```

---

### Update Role

**Endpoint:** `PUT /api/common/roles/{id}`

**Request Body:**

Same as Create Role.

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "roleName": "Department Manager",
    "roleCode": "DEPT_MANAGER",
    "description": "Updated description",
    "isActive": true
  },
  "message": "Role updated successfully"
}
```

---

### Deactivate Role

**Endpoint:** `DELETE /api/common/roles/{id}`

**Purpose:** Soft delete (deactivate) a role.

**Response:**

```json
{
  "success": true,
  "data": null,
  "message": "Role deactivated successfully"
}
```

---

## 2. Department Categories

### Get All Department Categories

**Endpoint:** `GET /api/common/department-categories`

**Purpose:** Get all department categories (system-wide and tenant-specific).

**Request Example:**

```http
GET /api/common/department-categories
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid...",
      "name": "Operations",
      "description": "Operational departments",
      "displayOrder": 1,
      "isActive": true
    },
    {
      "id": "uuid...",
      "name": "Support",
      "description": "Support departments",
      "displayOrder": 2,
      "isActive": true
    }
  ]
}
```

---

### Get Department Category by ID

**Endpoint:** `GET /api/common/department-categories/{id}`

**Request Example:**

```http
GET /api/common/department-categories/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "name": "Operations",
    "description": "Operational departments",
    "displayOrder": 1,
    "isActive": true
  }
}
```

---

### Create Department Category

**Endpoint:** `POST /api/common/department-categories`

**Purpose:** Create a new department category.

**Request Body:**

```json
{
  "categoryName": "Management",
  "description": "Management departments",
  "displayOrder": 3
}
```

**Validation:**
- `categoryName` (required, max 100 characters)
- `description` (optional, max 500 characters)
- `displayOrder` (optional, integer)

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "name": "Management",
    "description": "Management departments",
    "displayOrder": 3,
    "isActive": true
  },
  "message": "Department category created successfully"
}
```

---

### Update Department Category

**Endpoint:** `PUT /api/common/department-categories/{id}`

**Request Body:**

Same as Create Department Category.

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "name": "Management",
    "description": "Updated description",
    "displayOrder": 3,
    "isActive": true
  },
  "message": "Department category updated successfully"
}
```

---

### Deactivate Department Category

**Endpoint:** `DELETE /api/common/department-categories/{id}`

**Purpose:** Soft delete (deactivate) a department category.

**Response:**

```json
{
  "success": true,
  "data": null,
  "message": "Department category deactivated successfully"
}
```

---

## 3. Departments

### Get All Departments

**Endpoint:** `GET /api/common/departments`

**Purpose:** Get all active departments for current tenant.

**Request Example:**

```http
GET /api/common/departments
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid...",
      "name": "Production",
      "description": "Production Department",
      "categoryId": "uuid...",
      "categoryName": "Operations",
      "companyId": "uuid...",
      "managerId": null,
      "isActive": true
    },
    {
      "id": "uuid...",
      "name": "Sales",
      "description": "Sales Department",
      "categoryId": "uuid...",
      "categoryName": "Support",
      "companyId": "uuid...",
      "managerId": "uuid...",
      "isActive": true
    }
  ]
}
```

**Note:** Returns active departments only, ordered by department name.

---

### Get Departments by Company

**Endpoint:** `GET /api/common/departments/company/{companyId}`

**Purpose:** Get all active departments for a specific company.

**Request Example:**

```http
GET /api/common/departments/company/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

Same as Get All Departments (filtered by company).

---

### Get Department by ID

**Endpoint:** `GET /api/common/departments/{id}`

**Request Example:**

```http
GET /api/common/departments/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "name": "Production",
    "description": "Production Department",
    "categoryId": "uuid...",
    "categoryName": "Operations",
    "companyId": "uuid...",
    "managerId": null,
    "isActive": true
  }
}
```

---

### Create Department

**Status:** ❌ **Not Available**

**Note:** Department creation endpoints are not currently available. Departments are automatically seeded for new tenants via `TenantSeedService`. Manual creation must be done through the backend service layer or database.

**Workaround:** Use the tenant seed endpoint to add departments (if you have admin access):

```http
POST /api/admin/tenant-seed/departments-and-positions
```

---

## 4. Positions

### Get All Positions

**Endpoint:** `GET /api/common/positions`

**Purpose:** Get all positions for current tenant.

**Request Example:**

```http
GET /api/common/positions
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid...",
      "name": "Production Manager",
      "code": "PROD-MGR",
      "description": "Production department manager",
      "departmentId": "uuid...",
      "departmentName": "Production",
      "defaultRoleName": "Manager",
      "hierarchicalParentName": null,
      "displayOrder": 1,
      "isActive": true
    },
    {
      "id": "uuid...",
      "name": "Sales Representative",
      "code": "SALES-REP",
      "description": "Sales department representative",
      "departmentId": "uuid...",
      "departmentName": "Sales",
      "defaultRoleName": "User",
      "hierarchicalParentName": "Sales Manager",
      "displayOrder": 2,
      "isActive": true
    }
  ]
}
```

**Note:** Returns all positions (active and inactive), ordered by display order and position name.

---

### Get Positions by Department

**Endpoint:** `GET /api/common/positions/department/{departmentId}`

**Purpose:** Get all positions for a specific department.

**Request Example:**

```http
GET /api/common/positions/department/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

Same as Get All Positions (filtered by department).

---

### Get Position by ID

**Endpoint:** `GET /api/common/positions/{id}`

**Request Example:**

```http
GET /api/common/positions/123e4567-e89b-12d3-a456-426614174000
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "name": "Production Manager",
    "code": "PROD-MGR",
    "description": "Production department manager",
    "departmentId": "uuid...",
    "departmentName": "Production",
    "defaultRoleName": "Manager",
    "hierarchicalParentName": null,
    "displayOrder": 1,
    "isActive": true
  }
}
```

---

### Create Position

**Status:** ❌ **Not Available**

**Note:** Position creation endpoints are not currently available. Positions are automatically seeded for new tenants via `TenantSeedService`. Manual creation must be done through the backend service layer or database.

**Workaround:** Use the tenant seed endpoint to add positions (if you have admin access):

```http
POST /api/admin/tenant-seed/departments-and-positions
```

---

## Data Structures

### RoleDto

```typescript
interface RoleDto {
  id: string;                    // UUID
  roleName: string;               // Role name (e.g., "Admin")
  roleCode: string;              // Role code (e.g., "ADMIN")
  description?: string;           // Optional description
  isActive: boolean;              // Active status
}
```

### DepartmentCategoryDto

```typescript
interface DepartmentCategoryDto {
  id: string;                     // UUID
  name: string;                   // Category name (e.g., "Operations")
  description?: string;           // Optional description
  displayOrder?: number;          // Display order (for sorting)
  isActive: boolean;              // Active status
}
```

### DepartmentDto

```typescript
interface DepartmentDto {
  id: string;                     // UUID
  name: string;                   // Department name (e.g., "Production")
  description?: string;          // Optional description
  categoryId?: string;           // Department category ID
  categoryName?: string;         // Department category name
  companyId: string;             // Company ID
  managerId?: string;            // Manager user ID (optional)
  isActive: boolean;             // Active status
}
```

### PositionDto

```typescript
interface PositionDto {
  id: string;                     // UUID
  name: string;                   // Position name (e.g., "Production Manager")
  code: string;                   // Position code (e.g., "PROD-MGR")
  description?: string;          // Optional description
  departmentId: string;          // Department ID
  departmentName: string;         // Department name
  defaultRoleName?: string;       // Default role name for this position
  hierarchicalParentName?: string; // Parent position name (for hierarchy)
  displayOrder?: number;         // Display order (for sorting)
  isActive: boolean;             // Active status
}
```

---

## Automatic Seeding

**New Tenant Setup:**
- When a new tenant is created, departments and positions are automatically seeded via `TenantSeedService`
- This ensures every tenant has a complete organizational structure from the start

**Seeded Departments (Example):**
- Production
- Planning
- Finance
- Quality Control
- Logistics
- Sales
- Warehouse & Logistics
- Maintenance
- Energy & Facilities
- Kitchen & Catering
- Management

**Seeded Positions (Example):**
Each department has multiple positions (e.g., Manager, Supervisor, Operator, Specialist, etc.)

**Manual Seeding:**
If a tenant was created before seeding was implemented, you can manually trigger seeding:

```http
POST /api/admin/tenant-seed/departments-and-positions
```

**Note:** This endpoint requires admin privileges.

---

## Usage in User Creation

These entities are used when creating users:

1. **Role:** Assigned to user for authorization
2. **Department Category:** Used to organize departments
3. **Department:** User's organizational unit
4. **Position:** User's job position within department

**Example User Creation Flow:**
1. Load all options: `GET /api/common/users/creation-options`
2. User selects: Role, Department Category → Department → Position
3. Submit user creation with selected IDs

---

## Error Handling

**Common Error Responses:**

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Role name is required"
}
```

```json
{
  "success": false,
  "errorCode": "NOT_FOUND",
  "message": "Role not found"
}
```

```json
{
  "success": false,
  "errorCode": "DUPLICATE",
  "message": "Role code already exists"
}
```

---

## Best Practices

1. **Load Options First:** Call `/creation-options` when form opens (returns roles, categories, departments, positions in one request)
2. **Use Cached Data:** Roles are cached for 5 minutes, so frequent calls are efficient
3. **Filter by Tenant:** All queries are tenant-scoped automatically
4. **Handle Inactive Records:** Departments and positions can be inactive, filter as needed
5. **Validate Before Submit:** Ensure selected IDs exist and are active before user creation

---

## Related Documentation

- `docs/frontend/CREATE_USER.md` - User creation with roles, departments, positions
- `docs/frontend/ADDRESS_INPUT.md` - Address input API reference

---

**Status:** ✅ Production-Ready (Roles & Categories)  
**Status:** ⚠️ Partial (Departments & Positions - Read-only)  
**Last Updated:** 2025-01-XX

