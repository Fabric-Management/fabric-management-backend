# User Creation - Frontend Endpoint Guide

**Last Updated:** 2025-11-12  
**Status:** Production-Ready

---

## Overview

Backend provides **two separate endpoints** for user creation:
1. **Internal Users** (`/api/common/users/internal`) - Employees with HR data
2. **External Users** (`/api/common/users/external`) - Partners/suppliers/customers without HR data

**⚠️ IMPORTANT:** Frontend must choose the correct endpoint based on user type.

---

## Endpoint Selection Logic

### When to Use `/api/common/users/internal`

Use this endpoint when creating **internal employees** (your own company staff) with HR data:

**Indicators (any of these fields present):**
- `title` (MR, MRS, MS, etc.)
- `gender` (MALE, FEMALE, etc.)
- `birthDate`
- `nationality`
- `employeeNumber`
- `hireDate`
- `emergencyContact`
- `roleId` (organizational role assignment)
- `positionId` (job position assignment)
- `departmentId` (department assignment)

**Example Use Cases:**
- Creating a new employee for your company
- Adding staff member with full HR profile
- Onboarding new team member

---

### When to Use `/api/common/users/external`

Use this endpoint when creating **external users** (partners, suppliers, customers) without HR data:

**Indicators:**
- No HR data fields present
- Only basic user information (name, contact, company)
- Partner/supplier/customer relationship

**Example Use Cases:**
- Creating contact for supplier company
- Adding partner company representative
- Registering customer user

---

## Frontend Implementation

### TypeScript Types

```typescript
// User creation request types
interface CreateInternalUserRequest {
  firstName: string;
  lastName: string;
  contactValue: string;
  contactType: "EMAIL" | "PHONE";
  companyId: string;
  
  // Optional HR fields
  title?: "MR" | "MRS" | "MS" | "MISS" | "DR" | "PROF" | "ENG" | "NONE";
  gender?: "MALE" | "FEMALE" | "OTHER" | "PREFER_NOT_TO_SAY";
  birthDate?: string; // ISO 8601 format: "1990-01-15"
  nationality?: string; // ISO 3166-1 alpha-2: "GB", "TR", "US"
  employeeNumber?: string;
  hireDate?: string; // ISO 8601 format: "2024-01-15"
  emergencyContact?: {
    name?: string;
    phone?: string;
    relationship?: string;
  };
  
  // Organizational fields
  roleId?: string;
  positionId?: string;
  departmentId?: string;
  departmentCategoryId?: string;
  department?: string; // Deprecated, use departmentId
  
  // Additional contacts and addresses
  additionalContacts?: ContactData[];
  addresses?: AddressData[];
}

interface CreateExternalUserRequest {
  firstName: string;
  lastName: string;
  contactValue: string;
  contactType: "EMAIL" | "PHONE";
  companyId: string;
  
  // Optional basic fields
  department?: string;
  additionalContacts?: ContactData[];
  addresses?: AddressData[];
  
  // ❌ NO HR fields (title, gender, birthDate, etc.)
  // ❌ NO organizational fields (roleId, positionId, departmentId)
}

interface ContactData {
  contactValue: string;
  contactType: "EMAIL" | "PHONE";
  phoneType?: "MOBILE" | "LANDLINE"; // Only for PHONE contacts
  label?: string;
  isPersonal?: boolean;
  isWhatsApp?: boolean | null; // Only for MOBILE phones
}

interface AddressData {
  streetAddress: string;
  city: string;
  state?: string;
  postalCode: string;
  country: string;
  placeId?: string;
  addressType: "HOME" | "WORK" | "OFFICE";
  label?: string;
  isPrimary?: boolean;
}
```

---

### Endpoint Selection Function

```typescript
/**
 * Determine which endpoint to use based on request data.
 * 
 * @param request User creation request data
 * @returns Endpoint path: '/api/common/users/internal' or '/api/common/users/external'
 */
function determineUserCreationEndpoint(request: Partial<CreateInternalUserRequest>): string {
  // Check for HR data fields (indicates internal user)
  const hasHrData = 
    request.title !== undefined ||
    request.gender !== undefined ||
    request.birthDate !== undefined ||
    request.nationality !== undefined ||
    request.employeeNumber !== undefined ||
    request.hireDate !== undefined ||
    request.emergencyContact !== undefined ||
    request.roleId !== undefined ||
    request.positionId !== undefined ||
    request.departmentId !== undefined;
  
  return hasHrData 
    ? '/api/common/users/internal'
    : '/api/common/users/external';
}
```

---

### API Service Implementation

```typescript
// user.service.ts

import axios from 'axios';

interface UserCreationResponse {
  success: boolean;
  data: UserDto;
  message: string;
}

/**
 * Create user (internal or external) - automatically selects correct endpoint.
 */
export async function createUser(
  request: CreateInternalUserRequest | CreateExternalUserRequest
): Promise<UserDto> {
  // Determine endpoint based on request data
  const endpoint = determineUserCreationEndpoint(request);
  
  const response = await axios.post<UserCreationResponse>(
    endpoint,
    request,
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );
  
  if (!response.data.success) {
    throw new Error(response.data.message || 'Failed to create user');
  }
  
  return response.data.data;
}

/**
 * Create internal user (explicit endpoint).
 */
export async function createInternalUser(
  request: CreateInternalUserRequest
): Promise<UserDto> {
  const response = await axios.post<UserCreationResponse>(
    '/api/common/users/internal',
    request
  );
  
  if (!response.data.success) {
    throw new Error(response.data.message || 'Failed to create internal user');
  }
  
  return response.data.data;
}

/**
 * Create external user (explicit endpoint).
 */
export async function createExternalUser(
  request: CreateExternalUserRequest
): Promise<UserDto> {
  const response = await axios.post<UserCreationResponse>(
    '/api/common/users/external',
    request
  );
  
  if (!response.data.success) {
    throw new Error(response.data.message || 'Failed to create external user');
  }
  
  return response.data.data;
}
```

---

### React Component Example

```typescript
// UserCreateModal.tsx

import React, { useState } from 'react';
import { createUser } from '@/services/user.service';

interface UserFormData {
  firstName: string;
  lastName: string;
  contactValue: string;
  contactType: "EMAIL" | "PHONE";
  companyId: string;
  
  // HR fields (only for internal users)
  title?: string;
  gender?: string;
  birthDate?: string;
  nationality?: string;
  employeeNumber?: string;
  hireDate?: string;
  emergencyContact?: {
    name?: string;
    phone?: string;
    relationship?: string;
  };
  
  // Organizational fields (only for internal users)
  roleId?: string;
  positionId?: string;
  departmentId?: string;
  
  // Common fields
  additionalContacts?: ContactData[];
  addresses?: AddressData[];
}

export function UserCreateModal() {
  const [formData, setFormData] = useState<UserFormData>({
    firstName: '',
    lastName: '',
    contactValue: '',
    contactType: 'EMAIL',
    companyId: '',
  });
  
  const [isInternalUser, setIsInternalUser] = useState(false);
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      // Determine endpoint based on form data
      const endpoint = determineUserCreationEndpoint(formData);
      
      // Create user with correct endpoint
      const createdUser = await createUser(formData as any);
      
      console.log('User created:', createdUser);
      // Handle success...
    } catch (error) {
      console.error('Failed to create user:', error);
      // Handle error...
    }
  };
  
  return (
    <form onSubmit={handleSubmit}>
      {/* Basic fields */}
      <input
        type="text"
        value={formData.firstName}
        onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
        placeholder="First Name"
        required
      />
      
      {/* HR fields (only show if internal user) */}
      {isInternalUser && (
        <>
          <select
            value={formData.title || ''}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
          >
            <option value="">Select Title</option>
            <option value="MR">Mr</option>
            <option value="MRS">Mrs</option>
            {/* ... */}
          </select>
          
          {/* Other HR fields... */}
        </>
      )}
      
      <button type="submit">Create User</button>
    </form>
  );
}
```

---

## Error Handling

### Validation Errors

Both endpoints return validation errors in the same format:

```typescript
interface ValidationError {
  success: false;
  error: {
    code: "VALIDATION_ERROR";
    message: "Validation failed";
    details: {
      [fieldName: string]: string; // Field name -> error message
    };
  };
}

// Example response:
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": {
      "firstName": "First name is required",
      "contactValue": "Invalid email format"
    }
  }
}
```

### Handling in Frontend

```typescript
try {
  const user = await createUser(request);
  // Success
} catch (error) {
  if (axios.isAxiosError(error) && error.response?.status === 400) {
    const validationErrors = error.response.data.error?.details;
    if (validationErrors) {
      // Display field-specific errors
      Object.entries(validationErrors).forEach(([field, message]) => {
        console.error(`${field}: ${message}`);
      });
    }
  } else {
    // Generic error
    console.error('Failed to create user:', error.message);
  }
}
```

---

## Best Practices

### ✅ DO

1. **Always check for HR fields** before selecting endpoint
2. **Use explicit endpoints** (`/internal` or `/external`) when you know the user type
3. **Validate required fields** before submitting
4. **Handle validation errors** field by field
5. **Show clear error messages** to users

### ❌ DON'T

1. **Don't use** `/api/common/users` (convenience endpoint removed)
2. **Don't send HR fields** to `/external` endpoint
3. **Don't skip validation** - backend will reject invalid data
4. **Don't mix** internal and external user fields

---

## Migration Guide

If you were using the convenience endpoint (`POST /api/common/users`):

### Before (Old Code)

```typescript
// ❌ OLD - Convenience endpoint (removed)
const response = await axios.post('/api/common/users', request);
```

### After (New Code)

```typescript
// ✅ NEW - Explicit endpoint selection
const endpoint = determineUserCreationEndpoint(request);
const response = await axios.post(endpoint, request);
```

---

## Summary

- **Internal Users** → `POST /api/common/users/internal` (has HR data)
- **External Users** → `POST /api/common/users/external` (no HR data)
- **Selection Logic** → Check for HR fields (title, gender, birthDate, etc.)
- **Validation** → Both endpoints use `@Valid` annotation (automatic validation)
- **Error Handling** → Standardized error format with field-specific messages

---

## Questions?

If you have questions about endpoint selection or need help with implementation, check:
- Backend API documentation: `docs/frontend/USER_CREATION_COMPLETE_FIELDS.md`
- Backend code: `src/main/java/com/fabricmanagement/common/platform/user/api/controller/UserController.java`

