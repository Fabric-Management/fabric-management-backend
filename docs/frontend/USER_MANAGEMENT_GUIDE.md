# User Management Guide - Frontend Integration

**Last Updated:** 2025-01-10  
**Status:** Active  
**Purpose:** Guide for frontend developers working with User entity, Role, Department, and related features.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Breaking Changes](#breaking-changes)
3. [User Entity Structure](#user-entity-structure)
4. [API Endpoints](#api-endpoints)
5. [Role Management](#role-management)
6. [Department Management](#department-management)
7. [Contacts & Addresses](#contacts--addresses)
8. [JWT Token Structure](#jwt-token-structure)
9. [Code Examples](#code-examples)
10. [Migration Guide](#migration-guide)
11. [Common Patterns](#common-patterns)

---

## 🎯 Overview

### What Changed?

The User management system has been **significantly refactored**:

- **Before:** User had embedded `contactValue`, `contactType`, `department` fields
- **After:** User uses centralized `Contact`, `Address` entities and dynamic `Role`, `Department` management

### Key Features

✅ **No username field** - Authentication via Contact entity  
✅ **Dynamic Roles** - Database-driven role management (not enums)  
✅ **Multiple Departments** - Users can belong to multiple departments  
✅ **Display Name Auto-generated** - From `firstName + lastName`  
✅ **Centralized Contacts/Addresses** - Via Communication module  
✅ **Role-based Authorization** - Backend uses `role_code` for policies

---

## ⚠️ Breaking Changes

### 1. Removed Fields from UserDto

**REMOVED:**

```typescript
// ❌ These fields no longer exist in UserDto:
interface UserDto {
  contactValue?: string; // REMOVED
  contactType?: string; // REMOVED
  department?: string; // REMOVED (deprecated)
}
```

**NEW APPROACH:**

```typescript
// ✅ Fetch separately via Communication module
GET / api / common / users / { userId } / contacts;
GET / api / common / users / { userId } / addresses;
GET / api / common / users / { userId } / departments;
```

### 2. Department Field Deprecated

**OLD (Still works, but deprecated):**

```typescript
const user = await getUser(userId);
const department = user.department; // ❌ Deprecated
```

**NEW (Recommended):**

```typescript
const departments = await getUserDepartments(userId);
const primaryDept = departments.find(d => d.isPrimary);
```

### 3. Contact-Based Authentication

**Before:** User had direct `contactValue` field  
**After:** Authentication contact stored in `UserContact` junction with `isForAuthentication: true`

---

## 📊 User Entity Structure

### UserDto

```typescript
interface UserDto {
  id: string; // UUID
  tenantId: string; // UUID
  uid: string; // Auto-generated (e.g., "ACME-001-USER-00042")
  firstName: string; // Required
  lastName: string; // Required
  displayName: string; // Auto-generated: "firstName lastName"
  companyId: string; // UUID - Required (user belongs to company)
  roleId?: string; // UUID - Optional (references Role entity)
  role?: string; // Role name for display (e.g., "Manager")
  isActive: boolean;
  lastActiveAt?: string; // ISO 8601 timestamp
  onboardingCompletedAt?: string; // ISO 8601 timestamp
  hasCompletedOnboarding: boolean; // Computed: onboardingCompletedAt != null
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
}
```

### Key Points

- **No `contactValue`** - Fetch via `/api/common/users/{userId}/contacts`
- **No `department`** - Fetch via `/api/common/users/{userId}/departments`
- **`displayName`** - Auto-generated, don't set manually
- **`role`** - Display only (from Role entity), not for authorization
- **`roleId`** - Reference to Role entity (for role assignment)

---

## 📡 API Endpoints

### Base URL

All user endpoints: `/api/common/users`

---

### User CRUD Endpoints

#### Get User by ID

```http
GET /api/common/users/{userId}
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "uid": "ACME-001-USER-00042",
    "firstName": "John",
    "lastName": "Doe",
    "displayName": "John Doe",
    "companyId": "uuid",
    "roleId": "uuid",
    "role": "Manager",
    "isActive": true,
    "hasCompletedOnboarding": true,
    "createdAt": "2025-01-10T10:00:00Z",
    "updatedAt": "2025-01-10T10:00:00Z"
  }
}
```

#### Get All Users (Tenant)

```http
GET /api/common/users
Authorization: Bearer {token}
```

**Response:** Array of `UserDto`

#### Get Users by Company

```http
GET /api/common/users/company/{companyId}
Authorization: Bearer {token}
```

**Response:** Array of `UserDto`

#### Create User

```http
POST /api/common/users
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "John",
  "lastName": "Doe",
  "contactValue": "john@example.com",      // ✅ AUTO-CREATES Contact (EMAIL)
  "contactType": "EMAIL",                  // EMAIL or PHONE
  "companyId": "uuid",                     // ✅ AUTO-CREATES UserAddress (WORK) from Company
  "department": "Engineering"             // Optional - deprecated (will be removed)
}
```

**Response:** `UserDto` with created user

**✅ USER-FRIENDLY AUTOMATION:**

- **ContactValue provided** → Contact entity automatically created
- **Contact linked** → UserContact with `isForAuthentication: true` and `isDefault: true`
- **displayName** → Auto-generated from `firstName + lastName`
- **Address copied** → If Company has a primary address, UserAddress (WORK) is automatically created from Company's address

**Benefits:**

- ✅ Single form - no separate Contact/Address creation steps
- ✅ Company address automatically copied to user (WORK address)
- ✅ Reduces user errors
- ✅ Ensures data integrity

#### Update User

```http
PUT /api/common/users/{userId}
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "John",
  "lastName": "Smith",
  "department": "Sales"                   // Optional - deprecated
}
```

**Note:** Only `firstName` and `lastName` can be updated via this endpoint. For contacts, addresses, departments, use respective endpoints.

#### Deactivate User

```http
DELETE /api/common/users/{userId}
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "message": "User deactivated successfully"
}
```

#### Check Contact Exists

```http
GET /api/common/users/contact/{contactValue}
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": true // or false
}
```

---

## 👤 Role Management

### Overview

Roles are **database-driven**, not static enums. Standard roles are seeded, but new roles can be created via API.

### Standard Roles (Pre-seeded)

- **ADMIN** - Administrator (Full system access)
- **DIRECTOR** - Director (Üst yönetim)
- **MANAGER** - Manager (Departman yönetimi)
- **SUPERVISOR** - Supervisor (Vardiya/ekip lideri)
- **USER** - User (Standart çalışan)
- **INTERN** - Intern (Stajyer)
- **VIEWER** - Viewer (Sadece okuma)

### Role Endpoints

#### Get All Roles

```http
GET /api/common/roles
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "tenantId": "uuid",
      "uid": "SYS-ROLE-001",
      "roleName": "Administrator",
      "roleCode": "ADMIN",
      "description": "Full system access",
      "isActive": true
    }
  ]
}
```

#### Get Role by ID

```http
GET /api/common/roles/{roleId}
Authorization: Bearer {token}
```

#### Get Role by Code

```http
GET /api/common/roles/code/{code}
Authorization: Bearer {token}
```

**Example:** `GET /api/common/roles/code/ADMIN`

#### Create Role

```http
POST /api/common/roles
Content-Type: application/json
Authorization: Bearer {token}

{
  "roleName": "Senior Manager",
  "roleCode": "SENIOR_MANAGER",
  "description": "Senior management role with extended permissions"
}
```

#### Update Role

```http
PUT /api/common/roles/{roleId}
Content-Type: application/json
Authorization: Bearer {token}

{
  "roleName": "Updated Role Name",
  "roleCode": "ADMIN",                     // Cannot be changed
  "description": "Updated description"
}
```

#### Deactivate Role

```http
DELETE /api/common/roles/{roleId}
Authorization: Bearer {token}
```

### RoleDto

```typescript
interface RoleDto {
  id: string; // UUID
  tenantId: string; // UUID
  uid: string; // Auto-generated
  roleName: string; // Display name (e.g., "Administrator")
  roleCode: string; // Code for policies (e.g., "ADMIN")
  description?: string; // Optional description
  isActive: boolean;
}
```

### Role Assignment

**Note:** Currently, role assignment is done during user creation or via user update (if endpoint supports `roleId`). Check backend implementation for role assignment endpoint.

---

## 🏢 Department Management

### Overview

- Users can belong to **multiple departments**
- One department is marked as **primary**
- Departments are company-scoped (via `DepartmentCategory`)

### User-Department Endpoints

#### Get User Departments

```http
GET /api/common/users/{userId}/departments
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "userId": "uuid",
      "departmentId": "uuid",
      "isPrimary": true,
      "assignedAt": "2025-01-10T10:00:00Z",
      "assignedBy": "uuid"
    }
  ]
}
```

#### Get Primary Department

```http
GET /api/common/users/{userId}/departments/primary
Authorization: Bearer {token}
```

**Response:** Single `UserDepartmentDto`

#### Assign Department to User

```http
POST /api/common/users/{userId}/departments
Content-Type: application/json
Authorization: Bearer {token}

{
  "departmentId": "uuid",
  "isPrimary": true                    // Optional, defaults to false
}
```

**Response:** `UserDepartmentDto`

#### Set Primary Department

```http
PUT /api/common/users/{userId}/departments/{departmentId}/primary
Authorization: Bearer {token}
```

**Note:** This sets the specified department as primary and unsets other primary flags.

#### Remove Department Assignment

```http
DELETE /api/common/users/{userId}/departments/{departmentId}
Authorization: Bearer {token}
```

### UserDepartmentDto

```typescript
interface UserDepartmentDto {
  userId: string; // UUID
  departmentId: string; // UUID
  isPrimary: boolean; // Is this the primary department?
  assignedAt: string; // ISO 8601 timestamp
  assignedBy?: string; // UUID of user who assigned
}
```

### Important Notes

- **Multiple Departments:** Users can have multiple departments
- **Primary Department:** Only one can be primary (automatically managed)
- **JWT Token:** Contains `department` name (from primary department) for display only
- **Authorization:** Backend uses `UserDepartment` junction table for authorization checks

---

## 📞 Contacts & Addresses

### Overview

User contacts and addresses are managed via the **Communication module**. See `COMMUNICATION_SYSTEM_GUIDE.md` for details.

### Quick Reference

#### Get User Contacts

```http
GET /api/common/users/{userId}/contacts
```

#### Get Authentication Contact

```http
GET /api/common/users/{userId}/contacts/authentication
```

#### Get Default Contact

```http
GET /api/common/users/{userId}/contacts/default
```

#### Get User Addresses

```http
GET /api/common/users/{userId}/addresses
```

#### Get Primary Address

```http
GET /api/common/users/{userId}/addresses/primary
```

**See:** `docs/frontend/COMMUNICATION_SYSTEM_GUIDE.md` for complete details.

---

## 🔐 JWT Token Structure

### Current JWT Payload

```typescript
interface JwtPayload {
  sub: string; // contactValue (from authentication contact)
  user_id: string; // UUID
  tenant_id: string; // UUID
  tenant_uid: string; // e.g., "ACME-001"
  user_uid: string; // e.g., "ACME-001-USER-00042"
  company_id: string; // UUID
  firstName: string; // User's first name
  lastName: string; // User's last name
  department?: string; // ⚠️ Display only (from primary department)
  role_id?: string; // UUID - Role reference
  role?: string; // Display only (role name)
  iat: number; // Issued at (Unix timestamp)
  exp: number; // Expiration (Unix timestamp)
}
```

### Important Notes

- **`department`** - Display only, **not for authorization**
- **`role`** - Display only (user-friendly name)
- **`role_id`** - For role assignment/reference
- **`sub`** - Contact value (email/phone) used for login

### Usage Example

```typescript
// Decode JWT token
const payload = jwtDecode<JwtPayload>(token);

// Display user info
const userName = `${payload.firstName} ${payload.lastName}`;
const userRole = payload.role; // "Manager"
const userDepartment = payload.department; // "Engineering" (display only)

// For authorization, use backend API endpoints
const userDepartments = await getUserDepartments(payload.user_id);
```

---

## 💻 Code Examples

### Example 1: Fetch User with Full Details

```typescript
async function getUserWithDetails(userId: string) {
  const [user, contacts, addresses, departments, role] = await Promise.all([
    api.get(`/api/common/users/${userId}`),
    api.get(`/api/common/users/${userId}/contacts`),
    api.get(`/api/common/users/${userId}/addresses`),
    api.get(`/api/common/users/${userId}/departments`),
    // Fetch role if roleId exists
    user.roleId
      ? api.get(`/api/common/roles/${user.roleId}`)
      : Promise.resolve({ data: { data: null } }),
  ]);

  return {
    user: user.data.data,
    contacts: contacts.data.data,
    addresses: addresses.data.data,
    departments: departments.data.data,
    role: role.data.data,
  };
}
```

### Example 2: Create User with Contact (Modern UX with Address Autocomplete)

**✅ SUPER USER-FRIENDLY APPROACH - Single Form with Google Places API:**

```typescript
import { usePlacesWidget } from "@react-google-places/autocomplete";
import { useGoogleMap } from "@react-google-maps/api";

interface CreateUserFormData {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  companyId: string;
  roleId?: string;
  // Address fields (optional - if provided, will override Company address)
  address?: string;
  city?: string;
  country?: string;
  postalCode?: string;
}

async function createUserWithContact(userData: CreateUserFormData) {
  // ✅ Single API call - Backend handles everything automatically:
  // 1. Contact entity creation (EMAIL + PHONE if provided)
  // 2. UserContact junction with isForAuthentication=true
  // 3. displayName auto-generation
  // 4. UserAddress (WORK) from Company's address OR provided address

  const userResponse = await api.post("/api/common/users", {
    firstName: userData.firstName,
    lastName: userData.lastName,
    contactValue: userData.email, // Required for authentication
    contactType: "EMAIL",
    companyId: userData.companyId,
  });

  const user = userResponse.data.data;

  // ✅ Contact and displayName are automatically created!

  // Optional: Add phone contact if provided
  if (userData.phone) {
    await api.post(`/api/common/users/${user.id}/contacts`, {
      contactValue: userData.phone,
      contactType: "PHONE",
      isDefault: false,
      isForAuthentication: false,
    });
  }

  // Optional: Add custom address if provided (overrides Company address)
  if (userData.address || userData.city || userData.country) {
    // Create Address entity first
    const addressResponse = await api.post("/api/common/addresses", {
      addressLine1: userData.address || "",
      city: userData.city || "",
      country: userData.country || "",
      postalCode: userData.postalCode,
      addressType: "WORK",
    });

    // Link to User
    await api.post(`/api/common/users/${user.id}/addresses`, {
      addressId: addressResponse.data.data.id,
      isPrimary: true,
      addressType: "WORK",
    });
  }

  // Assign role (if provided)
  if (userData.roleId) {
    await api.put(`/api/common/users/${user.id}`, {
      roleId: userData.roleId,
    });
  }

  return user;
}
```

**What Gets Auto-Created:**

- ✅ **Contact (EMAIL)** with `contactValue`
- ✅ **UserContact** junction with `isForAuthentication: true` and `isDefault: true`
- ✅ **displayName**: "John Doe" (firstName + lastName)
- ✅ **UserAddress (WORK)**: Automatically copied from Company's primary address (if exists)
- ✅ **Optional Phone Contact**: If `phone` provided
- ✅ **Optional Custom Address**: If address fields provided (overrides Company address)

### Example 3: User Profile Component

```typescript
import { useState, useEffect } from "react";
import axios from "axios";

interface UserProfileProps {
  userId: string;
}

function UserProfile({ userId }: UserProfileProps) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [contacts, setContacts] = useState<UserContactDto[]>([]);
  const [addresses, setAddresses] = useState<UserAddressDto[]>([]);
  const [departments, setDepartments] = useState<UserDepartmentDto[]>([]);
  const [role, setRole] = useState<RoleDto | null>(null);

  useEffect(() => {
    // Fetch user
    axios.get(`/api/common/users/${userId}`).then(res => {
      setUser(res.data.data);

      // Fetch role if roleId exists
      if (res.data.data.roleId) {
        axios
          .get(`/api/common/roles/${res.data.data.roleId}`)
          .then(roleRes => setRole(roleRes.data.data));
      }
    });

    // Fetch contacts
    axios
      .get(`/api/common/users/${userId}/contacts`)
      .then(res => setContacts(res.data.data));

    // Fetch addresses
    axios
      .get(`/api/common/users/${userId}/addresses`)
      .then(res => setAddresses(res.data.data));

    // Fetch departments
    axios
      .get(`/api/common/users/${userId}/departments`)
      .then(res => setDepartments(res.data.data));
  }, [userId]);

  if (!user) return <div>Loading...</div>;

  const primaryDept = departments.find(d => d.isPrimary);
  const authContact = contacts.find(c => c.isForAuthentication);
  const primaryAddress = addresses.find(a => a.isPrimary);

  return (
    <div>
      <h1>{user.displayName}</h1>
      <p>Role: {role?.roleName || user.role || "N/A"}</p>
      <p>
        Primary Department:{" "}
        {primaryDept ? "Department ID: " + primaryDept.departmentId : "N/A"}
      </p>
      <p>Email: {authContact?.contact.contactValue || "N/A"}</p>
      <p>Address: {primaryAddress?.address.formattedAddress || "N/A"}</p>
    </div>
  );
}
```

### Example 4: User List with Departments

```typescript
async function getUsersWithDepartments() {
  const usersResponse = await axios.get("/api/common/users");
  const users = usersResponse.data.data;

  // Fetch departments for each user
  const usersWithDepts = await Promise.all(
    users.map(async (user: UserDto) => {
      try {
        const deptsResponse = await axios.get(
          `/api/common/users/${user.id}/departments`
        );
        return {
          ...user,
          departments: deptsResponse.data.data,
          primaryDepartment: deptsResponse.data.data.find(
            (d: UserDepartmentDto) => d.isPrimary
          ),
        };
      } catch (error) {
        return {
          ...user,
          departments: [],
          primaryDepartment: null,
        };
      }
    })
  );

  return usersWithDepts;
}
```

### Example 5: Assign Multiple Departments

```typescript
async function assignDepartmentsToUser(
  userId: string,
  departmentIds: string[],
  primaryDepartmentId: string
) {
  // Assign all departments
  for (const deptId of departmentIds) {
    await axios.post(`/api/common/users/${userId}/departments`, {
      departmentId: deptId,
      isPrimary: deptId === primaryDepartmentId,
    });
  }

  // Ensure primary is set correctly
  if (primaryDepartmentId) {
    await axios.put(
      `/api/common/users/${userId}/departments/${primaryDepartmentId}/primary`
    );
  }
}
```

### Example 6: Role Dropdown Component

```typescript
import { useState, useEffect } from "react";
import axios from "axios";

function RoleSelector({
  value,
  onChange,
}: {
  value?: string;
  onChange: (roleId: string) => void;
}) {
  const [roles, setRoles] = useState<RoleDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios
      .get("/api/common/roles")
      .then(res => {
        setRoles(res.data.data.filter((r: RoleDto) => r.isActive));
        setLoading(false);
      })
      .catch(err => {
        console.error("Failed to fetch roles:", err);
        setLoading(false);
      });
  }, []);

  if (loading) return <div>Loading roles...</div>;

  return (
    <select value={value || ""} onChange={e => onChange(e.target.value)}>
      <option value="">Select Role</option>
      {roles.map(role => (
        <option key={role.id} value={role.id}>
          {role.roleName} ({role.roleCode})
        </option>
      ))}
    </select>
  );
}
```

---

## 🔄 Migration Guide

### Step 1: Remove Direct Field Usage

**Before:**

```typescript
// ❌ Don't use these
const user = await getUser(userId);
const email = user.contactValue; // REMOVED
const department = user.department; // DEPRECATED
```

**After:**

```typescript
// ✅ Fetch separately
const user = await getUser(userId);
const contacts = await getUserContacts(userId);
const email = contacts.find(c => c.isForAuthentication)?.contact.contactValue;

const departments = await getUserDepartments(userId);
const department = departments.find(d => d.isPrimary);
```

### Step 2: Update User Forms

**Before:**

```typescript
function UserForm() {
  const [email, setEmail] = useState("");
  const [department, setDepartment] = useState("");

  // ...
}
```

**After:**

```typescript
function UserForm() {
  const [contacts, setContacts] = useState<UserContactDto[]>([]);
  const [departments, setDepartments] = useState<UserDepartmentDto[]>([]);

  // Use Contact component for email/phone
  // Use Department selector for departments
}
```

### Step 3: Update JWT Usage

**Before:**

```typescript
const payload = jwtDecode(token);
const department = payload.department; // Used for authorization
```

**After:**

```typescript
const payload = jwtDecode(token);
const department = payload.department; // Display only

// For authorization, fetch from API
const userDepartments = await getUserDepartments(payload.user_id);
```

### Step 4: Update Role Handling

**Before:**

```typescript
enum Role {
  ADMIN = "ADMIN",
  MANAGER = "MANAGER",
}
```

**After:**

```typescript
// Fetch from API on app startup
const roles = await getRoles();
const adminRole = roles.find(r => r.roleCode === "ADMIN");
```

---

## 🎨 Common Patterns

### Pattern 1: User Display Helper

```typescript
function getUserDisplayInfo(user: UserDto, contacts: UserContactDto[]) {
  const authContact = contacts.find(c => c.isForAuthentication);
  return {
    name: user.displayName,
    email: authContact?.contact.contactValue || "N/A",
    role: user.role || "No Role",
    uid: user.uid,
  };
}
```

### Pattern 2: Primary Department Helper

```typescript
function getPrimaryDepartment(departments: UserDepartmentDto[]) {
  return departments.find(d => d.isPrimary) || null;
}
```

### Pattern 3: User Status Helper

```typescript
function getUserStatus(user: UserDto) {
  if (!user.isActive) return "Inactive";
  if (!user.hasCompletedOnboarding) return "Pending Onboarding";
  return "Active";
}
```

### Pattern 4: Contact Helper

```typescript
function getUserEmail(contacts: UserContactDto[]): string | null {
  const emailContact = contacts.find(
    c => c.contact.contactType === "EMAIL" && c.isDefault
  );
  return emailContact?.contact.contactValue || null;
}

function getUserPhone(contacts: UserContactDto[]): string | null {
  const phoneContact = contacts.find(
    c => c.contact.contactType === "PHONE" && c.isDefault
  );
  return phoneContact?.contact.contactValue || null;
}
```

### Pattern 5: Role Helper

```typescript
function hasRole(user: UserDto, roleCode: string, roles: RoleDto[]): boolean {
  if (!user.roleId) return false;
  const userRole = roles.find(r => r.id === user.roleId);
  return userRole?.roleCode === roleCode;
}

// Usage
const isAdmin = hasRole(user, "ADMIN", allRoles);
```

---

## ⚡ Best Practices

### 1. Always Fetch Related Data Separately

User entity doesn't include contacts/addresses/departments. Fetch them via separate endpoints.

### 2. Cache Roles

Roles don't change frequently. Fetch once on app startup and cache.

### 3. Handle Multiple Departments

Users can have multiple departments. Always handle arrays, not single values.

### 4. Primary Department Logic

When assigning departments, remember only one can be primary. Use the set-primary endpoint to ensure consistency.

### 5. Display Name

Don't manually set `displayName`. It's auto-generated from `firstName + lastName`.

### 6. Contact Management

For user creation, provide `contactValue` and `contactType` in `CreateUserRequest`. Backend handles Contact entity creation automatically.

---

## 🐛 Error Handling

### Common Errors

#### User Not Found

```json
{
  "success": false,
  "error": "USER_NOT_FOUND",
  "message": "User not found"
}
```

#### Contact Already Exists

```json
{
  "success": false,
  "error": "CONTACT_EXISTS",
  "message": "Contact value already registered"
}
```

#### Company Not Found

```json
{
  "success": false,
  "error": "COMPANY_NOT_FOUND",
  "message": "Company not found"
}
```

### Error Handling Example

```typescript
try {
  const user = await createUser(userData);
} catch (error: any) {
  if (error.response?.status === 404) {
    // Handle not found
  } else if (error.response?.data?.error === "CONTACT_EXISTS") {
    // Handle contact exists
  } else {
    // Handle generic error
  }
}
```

---

## 📚 Additional Resources

- **Communication System:** `docs/frontend/COMMUNICATION_SYSTEM_GUIDE.md`
- **Role & Department Architecture:** `docs/Frontend_Notification_Role_Department_Architecture.md`
- **Backend API Docs:** Swagger UI at `/swagger-ui.html`

---

## ❓ FAQ

### Q: How do I get a user's email?

**A:** Fetch user contacts and find the one with `isForAuthentication: true`:

```typescript
const contacts = await getUserContacts(userId);
const email = contacts.find(c => c.isForAuthentication)?.contact.contactValue;
```

### Q: Can a user have multiple roles?

**A:** No. A user can have only one role (via `roleId`). However, they can have multiple departments.

### Q: How do I assign a role to a user?

**A:** Currently, role assignment may be done during user creation or via user update endpoint. Check backend for role assignment endpoint.

### Q: What's the difference between `role` and `roleCode`?

**A:**

- `role` - Display name (e.g., "Manager")
- `roleCode` - Code used in policies (e.g., "MANAGER")

### Q: How do I check if a user is an admin?

**A:** Check their role code:

```typescript
const user = await getUser(userId);
const role = await getRole(user.roleId);
const isAdmin = role?.roleCode === "ADMIN";
```

### Q: Can I set `displayName` manually?

**A:** No. It's auto-generated from `firstName + lastName`. Don't send it in requests.

### Q: How do I update a user's department?

**A:** Use the department assignment endpoints:

```typescript
// Assign new department
await assignDepartment(userId, newDepartmentId, true);

// Remove old department
await removeDepartment(userId, oldDepartmentId);
```

---

## 🎨 Modern UX: Address Autocomplete & Validation

### Overview

For the **best user experience**, implement address autocomplete using **Google Places API**. This provides:

- ✅ **Address Autocomplete** - Users type, system suggests valid addresses
- ✅ **Address Validation** - Ensures addresses are real and formatted correctly
- ✅ **Auto-fill** - Automatically fills city, country, postal code
- ✅ **Geocoding** - Gets coordinates for mapping/location services
- ✅ **Single Form** - No separate address forms needed

### Setup Google Places API

#### 1. Install Dependencies

```bash
npm install @react-google-places/autocomplete
# OR
npm install @googlemaps/js-api-loader
```

#### 2. Get Google API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Places API** and **Geocoding API**
4. Create API Key
5. Add to your `.env`:

```env
VITE_GOOGLE_MAPS_API_KEY=your-api-key-here
```

#### 3. Initialize Google Maps Script

```typescript
// lib/google-maps.ts
import { Loader } from "@googlemaps/js-api-loader";

const loader = new Loader({
  apiKey: import.meta.env.VITE_GOOGLE_MAPS_API_KEY,
  version: "weekly",
  libraries: ["places"],
});

export const initGoogleMaps = () => loader.load();
```

---

## 📝 Complete User Creation Form Example

### React Component with Google Places Autocomplete

```typescript
// components/CreateUserForm.tsx
import { useState, useRef } from "react";
import { usePlacesWidget } from "@react-google-places/autocomplete";
import { initGoogleMaps } from "../lib/google-maps";

interface AddressComponents {
  streetAddress: string;
  city: string;
  state?: string;
  country: string;
  postalCode?: string;
}

export function CreateUserForm() {
  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    email: "",
    phone: "",
    companyId: "",
    roleId: "",
    address: {} as AddressComponents,
  });

  const [isGoogleMapsLoaded, setIsGoogleMapsLoaded] = useState(false);
  const addressInputRef = useRef<HTMLInputElement>(null);

  // Initialize Google Maps on component mount
  useEffect(() => {
    initGoogleMaps()
      .then(() => {
        setIsGoogleMapsLoaded(true);
        console.log("✅ Google Maps API loaded");
      })
      .catch(error => {
        console.error("❌ Failed to load Google Maps API:", error);
      });
  }, []);

  // Google Places Autocomplete hook
  const { ref: placesRef } = usePlacesWidget({
    onPlaceSelected: place => {
      // Parse address components from Google Places result
      const addressComponents: AddressComponents = {
        streetAddress: "",
        city: "",
        country: "",
        state: "",
        postalCode: "",
      };

      // Extract address components
      place.address_components?.forEach(component => {
        const type = component.types[0];

        if (type === "street_number" || type === "route") {
          addressComponents.streetAddress += component.long_name + " ";
        } else if (type === "locality") {
          addressComponents.city = component.long_name;
        } else if (type === "administrative_area_level_1") {
          addressComponents.state = component.long_name;
        } else if (type === "country") {
          addressComponents.country = component.long_name;
        } else if (type === "postal_code") {
          addressComponents.postalCode = component.long_name;
        }
      });

      addressComponents.streetAddress = addressComponents.streetAddress.trim();

      // Also use formatted_address as fallback
      if (place.formatted_address && !addressComponents.streetAddress) {
        addressComponents.streetAddress = place.formatted_address;
      }

      setFormData(prev => ({
        ...prev,
        address: addressComponents,
      }));

      // Fill input with formatted address
      if (addressInputRef.current) {
        addressInputRef.current.value = place.formatted_address || "";
      }

      console.log("✅ Address selected:", addressComponents);
    },
    options: {
      types: ["address"], // Only show addresses, not businesses
      componentRestrictions: { country: ["tr", "us", "de"] }, // Restrict to specific countries
      fields: ["address_components", "formatted_address", "geometry"],
    },
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      // Step 1: Create user (backend auto-creates Contact and Address from Company)
      const userResponse = await api.post("/api/common/users", {
        firstName: formData.firstName,
        lastName: formData.lastName,
        contactValue: formData.email,
        contactType: "EMAIL",
        companyId: formData.companyId,
      });

      const user = userResponse.data.data;

      // Step 2: Add phone contact (if provided)
      if (formData.phone) {
        await api.post(`/api/common/users/${user.id}/contacts`, {
          contactValue: formData.phone,
          contactType: "PHONE",
          isDefault: false,
          isForAuthentication: false,
        });
      }

      // Step 3: Create custom address if provided (overrides Company address)
      if (formData.address.streetAddress || formData.address.city) {
        const addressResponse = await api.post("/api/common/addresses", {
          streetAddress: formData.address.streetAddress || "",
          city: formData.address.city || "",
          state: formData.address.state,
          country: formData.address.country || "",
          postalCode: formData.address.postalCode,
          addressType: "WORK",
        });

        await api.post(`/api/common/users/${user.id}/addresses`, {
          addressId: addressResponse.data.data.id,
          isPrimary: true,
          addressType: "WORK",
        });
      }

      // Step 4: Assign role (if provided)
      if (formData.roleId) {
        await api.put(`/api/common/users/${user.id}`, {
          roleId: formData.roleId,
        });
      }

      // User created successfully - handle success (navigate, show message, etc.)
      console.log("✅ User created:", user);
    } catch (error: any) {
      console.error("❌ Error creating user:", error);
      // Handle error (show error message, etc.)
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <h2>Create New User</h2>

      {/* Basic Info */}
      <div>
        <label>First Name *</label>
        <input
          type="text"
          value={formData.firstName}
          onChange={e =>
            setFormData(prev => ({ ...prev, firstName: e.target.value }))
          }
          required
        />
      </div>

      <div>
        <label>Last Name *</label>
        <input
          type="text"
          value={formData.lastName}
          onChange={e =>
            setFormData(prev => ({ ...prev, lastName: e.target.value }))
          }
          required
        />
      </div>

      {/* Contact Info */}
      <div>
        <label>Email *</label>
        <input
          type="email"
          value={formData.email}
          onChange={e =>
            setFormData(prev => ({ ...prev, email: e.target.value }))
          }
          required
        />
      </div>

      <div>
        <label>Phone (Optional)</label>
        <input
          type="tel"
          value={formData.phone}
          onChange={e =>
            setFormData(prev => ({ ...prev, phone: e.target.value }))
          }
          placeholder="+90 555 123 4567"
        />
      </div>

      {/* Address with Google Places Autocomplete */}
      <div>
        <label>
          Work Address (Optional - Auto-filled from Company if not provided)
        </label>
        {isGoogleMapsLoaded ? (
          <input
            ref={node => {
              placesRef.current = node;
              addressInputRef.current = node;
            }}
            type="text"
            placeholder="Start typing address..."
          />
        ) : (
          <input type="text" placeholder="Loading Google Maps..." disabled />
        )}
      </div>

      {/* Company & Role */}
      <div>
        <label>Company *</label>
        <CompanySelector
          value={formData.companyId}
          onChange={companyId => setFormData(prev => ({ ...prev, companyId }))}
        />
      </div>

      <div>
        <label>Role (Optional)</label>
        <RoleSelector
          value={formData.roleId}
          onChange={roleId => setFormData(prev => ({ ...prev, roleId }))}
        />
      </div>

      <button type="submit">Create User</button>
    </form>
  );
}
```

---

## 🗺️ Address Validation Best Practices

### 1. Validate Address Before Submission

```typescript
async function validateAddress(address: AddressComponents): Promise<boolean> {
  // Use Google Geocoding API to validate address
  const geocoder = new google.maps.Geocoder();

  return new Promise(resolve => {
    geocoder.geocode(
      {
        address: `${address.streetAddress}, ${address.city}, ${address.country}`,
        componentRestrictions: { country: address.country },
      },
      (results, status) => {
        if (status === "OK" && results && results.length > 0) {
          // Address is valid
          resolve(true);
        } else {
          // Address not found or invalid
          resolve(false);
        }
      }
    );
  });
}
```

### 2. Show Address Suggestions

```typescript
const { suggestions } = usePlacesWidget({
  onPlaceSelected: place => {
    // Handle selection
  },
  options: {
    types: ["address"],
    // Show suggestions as user types
    fields: ["address_components", "formatted_address"],
  },
});
```

### 3. Handle Address Formatting

```typescript
function formatAddressForBackend(
  place: google.maps.places.PlaceResult
): AddressComponents {
  const components: AddressComponents = {
    streetAddress: "",
    city: "",
    country: "",
    state: "",
    postalCode: "",
  };

  place.address_components?.forEach(component => {
    const type = component.types[0];

    switch (type) {
      case "street_number":
      case "route":
        components.streetAddress += component.long_name + " ";
        break;
      case "locality":
      case "sublocality":
        components.city = component.long_name;
        break;
      case "administrative_area_level_1":
        components.state = component.long_name;
        break;
      case "country":
        components.country = component.short_name; // Use ISO code
        break;
      case "postal_code":
        components.postalCode = component.long_name;
        break;
    }
  });

  components.streetAddress = components.streetAddress.trim();

  return components;
}
```

---

## 🎯 Smart Defaults & Automation Patterns

### Pattern 1: Pre-fill Company Address

```typescript
// Pre-fill Company address for user
useEffect(() => {
  if (formData.companyId) {
    api
      .get(`/api/common/companies/${formData.companyId}/addresses/primary`)
      .then(res => {
        const companyAddress = res.data.data;
        if (companyAddress) {
          setFormData(prev => ({
            ...prev,
            address: {
              streetAddress: companyAddress.streetAddress,
              city: companyAddress.city,
              country: companyAddress.country,
              postalCode: companyAddress.postalCode,
            },
          }));
        }
      });
  }
}, [formData.companyId]);
```

### Pattern 2: Real-time Contact Validation

```typescript
const [validationErrors, setValidationErrors] = useState<
  Record<string, string>
>({});

const validateEmail = (email: string) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    setValidationErrors(prev => ({ ...prev, email: "Invalid email format" }));
  } else {
    setValidationErrors(prev => {
      const { email, ...rest } = prev;
      return rest;
    });
  }
};

// Check if contact already exists
const checkContactExists = async (email: string) => {
  try {
    const response = await api.get(`/api/common/users/contact/${email}`);
    if (response.data.data === true) {
      setValidationErrors(prev => ({
        ...prev,
        email: "This email is already registered",
      }));
    }
  } catch (error) {
    // Contact doesn't exist - OK
  }
};
```

---

**Questions?** Contact backend team or check Swagger documentation.
