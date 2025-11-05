# Create User API Reference

**Last Updated:** 2025-01-XX  
**Status:** Production-Ready  
**Backend Endpoints:** `/api/common/users/*`

---

## Overview

This document describes the backend API for creating internal users (employees) with all required fields, validation, and optional HR data.

**Key Points:**
- Supports internal users (employees) with HR data
- Supports external users (partners/suppliers/customers) without HR data
- Provides orchestration endpoint for form options (roles, departments, positions)
- Auto-generates employee numbers
- Supports multiple contacts and addresses per user
- All validation happens server-side

---

## Endpoints

### 1. Get User Creation Options

**Endpoint:** `GET /api/common/users/creation-options`

**Purpose:** Get all options needed for user creation form in a single response (orchestration endpoint).

**Benefits:**
- Single HTTP request instead of 4 separate requests
- Reduced network overhead
- Faster page load
- Single database transaction

**Request Example:**

```http
GET /api/common/users/creation-options
```

**Response:**

```json
{
  "success": true,
  "data": {
    "roles": [
      {
        "id": "uuid...",
        "roleName": "Admin",
        "description": "System administrator"
      },
      {
        "id": "uuid...",
        "roleName": "HR Manager",
        "description": "Human resources manager"
      }
    ],
    "departmentCategories": [
      {
        "id": "uuid...",
        "name": "Operations",
        "displayOrder": 1
      },
      {
        "id": "uuid...",
        "name": "Support",
        "displayOrder": 2
      }
    ],
    "departments": [
      {
        "id": "uuid...",
        "name": "Production",
        "categoryId": "uuid...",
        "categoryName": "Operations"
      },
      {
        "id": "uuid...",
        "name": "Sales",
        "categoryId": "uuid...",
        "categoryName": "Support"
      }
    ],
    "positions": [
      {
        "id": "uuid...",
        "name": "Production Manager",
        "departmentId": "uuid...",
        "departmentName": "Production",
        "defaultRoleName": "Manager"
      },
      {
        "id": "uuid...",
        "name": "Sales Representative",
        "departmentId": "uuid...",
        "departmentName": "Sales",
        "defaultRoleName": "User"
      }
    ]
  }
}
```

**When to Call:**
- When user creation modal/form is opened
- To populate all dropdown options before user starts filling form

**Caching:**
- Response is cached for 10 minutes (tenant-scoped)
- Cache invalidated when departments/positions are updated

---

### 2. Generate Employee Number

**Endpoint:** `GET /api/common/users/generate-employee-number`

**Purpose:** Generate a unique employee number (auto-incrementing sequence).

**Format:** `{TENANT_UID}-EMP-{SEQUENCE}`

**Example:** `ACME-001-EMP-00042`

**Request Example:**

```http
GET /api/common/users/generate-employee-number
```

**Response:**

```json
{
  "success": true,
  "data": "ACME-001-EMP-00042"
}
```

**When to Call:**
- When user clicks "Generate Employee Number" button (magic wand icon)
- Automatically called by backend if employee number not provided and HR data exists

**Design:**
- Global sequence (not year-based)
- No sequence reset at year boundary
- Unique numbers (never duplicates)
- Supports unlimited employees

---

### 3. Create Internal User

**Endpoint:** `POST /api/common/users/internal`

**Purpose:** Create an internal employee (own staff with HR data).

**Request Body:**

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "contactValue": "john.doe@example.com",
  "contactType": "EMAIL",
  "companyId": "uuid...",
  "department": "Production",
  "departmentId": "uuid...",
  "departmentCategoryId": "uuid...",
  "roleId": "uuid...",
  "positionId": "uuid...",
  "additionalContacts": [
    {
      "contactValue": "+447553838399",
      "contactType": "PHONE",
      "label": "Work Phone",
      "isPersonal": false,
      "isWhatsApp": true
    }
  ],
  "addresses": [
    {
      "streetAddress": "123 Main Street",
      "city": "London",
      "state": "England",
      "postalCode": "MK5 7GE",
      "country": "United Kingdom",
      "placeId": "ChIJ...",
      "addressType": "WORK",
      "label": "Office Address",
      "isPrimary": true
    }
  ],
  "title": "Mr",
  "gender": "MALE",
  "birthDate": "1990-01-15",
  "nationality": "GB",
  "employeeNumber": "ACME-001-EMP-00042",
  "hireDate": "2024-01-15",
  "emergencyContact": {
    "name": "Jane Doe",
    "phone": "+447553838400",
    "relationship": "Spouse"
  }
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "uid": "TENANT-UID-USR-00001",
    "firstName": "John",
    "lastName": "Doe",
    "displayName": "John Doe",
    "companyId": "uuid...",
    "roleId": "uuid...",
    "role": "Admin",
    "isActive": true,
    "hasCompletedOnboarding": false,
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z"
  },
  "message": "Internal employee created successfully"
}
```

**Required Fields:**
- `firstName` (string, required)
- `lastName` (string, required)
- `contactValue` (string, required) - Email or phone number
- `contactType` (enum, required) - `EMAIL` or `PHONE`
- `companyId` (UUID, required)

**Optional Fields:**
- `department` (string) - Department name
- `departmentId` (UUID) - Department ID (preferred over name)
- `departmentCategoryId` (UUID) - Department category ID
- `roleId` (UUID) - Role ID
- `positionId` (UUID) - Position ID
- `additionalContacts` (array) - Additional email/phone contacts
- `addresses` (array) - User addresses (see AddressData structure below)
- `title` (enum) - `Mr`, `Miss`, `Mrs`, `Ms`, `Dr`, `Prof`, `Eng`, `None`
- `gender` (enum) - `MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY`
- `birthDate` (date, ISO 8601) - Birth date
- `nationality` (string) - ISO 3166-1 alpha-2 country code (e.g., "GB", "TR")
- `employeeNumber` (string) - Auto-generated if not provided and HR data exists
- `hireDate` (date, ISO 8601) - Employment start date
- `emergencyContact` (object) - Emergency contact information

**Auto-Generated Fields:**
- `employeeNumber` - Auto-generated if not provided and any HR data exists
- `uid` - User UID (format: `{TENANT_UID}-USR-{SEQUENCE}`)

**Validation:**
- Email format validated via `@Email` annotation
- Phone format validated via `@PhoneNumber` annotation (E.164 format)
- All required fields validated server-side
- Contact uniqueness checked (cannot duplicate existing contact)

---

### 4. Create External User

**Endpoint:** `POST /api/common/users/external`

**Purpose:** Create an external user (partner/supplier/customer without HR data).

**Request Body:**

```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "contactValue": "jane.smith@partner.com",
  "contactType": "EMAIL",
  "companyId": "uuid...",
  "additionalContacts": [
    {
      "contactValue": "+447553838401",
      "contactType": "PHONE",
      "label": "Mobile",
      "isPersonal": true
    }
  ],
  "addresses": [
    {
      "streetAddress": "456 Partner Street",
      "city": "Manchester",
      "state": "England",
      "postalCode": "M1 1AA",
      "country": "United Kingdom",
      "placeId": "ChIJ...",
      "addressType": "WORK",
      "label": "Partner Office"
    }
  ]
}
```

**Response:**

Same as internal user response (no HR data fields).

**Differences from Internal User:**
- No HR data fields (title, gender, birthDate, nationality, employeeNumber, hireDate, emergencyContact)
- No department/position assignment required
- No role assignment required

---

## Data Structures

### ContactData

```typescript
interface ContactData {
  contactValue: string;               // Required: Email or phone number
  contactType: "EMAIL" | "PHONE";     // Required
  label?: string;                     // Optional (e.g., "Work Email", "Mobile")
  isPersonal?: boolean;               // Optional (default: true)
  isWhatsApp?: boolean;               // Optional (for PHONE only, auto-detected if null)
}
```

**Validation:**
- `contactValue` must be valid email format if `contactType` is `EMAIL`
- `contactValue` must be valid E.164 phone format if `contactType` is `PHONE`
- Email: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`
- Phone: `^\+[1-9]\d{1,14}$` (E.164 format)

---

### AddressData

```typescript
interface AddressData {
  streetAddress: string;               // Required
  city: string;                        // Required
  state?: string;                      // Optional
  postalCode?: string;                 // Optional
  country: string;                     // Required
  placeId?: string;                    // Optional (recommended for validation)
  addressType?: string;                 // Optional: "WORK" | "HOME" (default: "WORK")
  label?: string;                      // Optional (e.g., "Head Office", "Home Address")
  isPrimary?: boolean;                 // Optional (default: false, first address becomes primary)
}
```

**Note:** For address input, use the address endpoints from `docs/frontend/ADDRESS_INPUT.md` to get validated address data with `placeId`, then use that data in `AddressData`.

---

### EmergencyContactData

```typescript
interface EmergencyContactData {
  name?: string;                       // Optional
  phone?: string;                      // Optional (E.164 format)
  relationship?: string;                // Optional (e.g., "Spouse", "Parent", "Sibling", "Friend")
}
```

---

## Complete User Creation Flow

**Step 1: Load Form Options**
- Call `GET /api/common/users/creation-options`
- Populate dropdowns: roles, department categories, departments, positions

**Step 2: User Fills Basic Information**
- First name, last name
- Primary contact (email or phone)
- Company selection

**Step 3: Optional - Generate Employee Number**
- User clicks "Generate Employee Number" button
- Call `GET /api/common/users/generate-employee-number`
- Auto-fill employee number field

**Step 4: Optional - Add Additional Contacts**
- User adds additional email/phone contacts
- Each contact validated server-side

**Step 5: Optional - Add Addresses**
- User enters postcode (optionally selects country)
- Call `GET /api/common/addresses/validation/search-by-postcode?postcode={postcode}&country={country}`
- User selects address from list
- Frontend auto-fills address fields from selected address
- Store `placeId` for validation

**Step 6: Optional - Add HR Data**
- Title, gender, birth date, nationality
- Hire date
- Emergency contact

**Step 7: Submit Form**
- Call `POST /api/common/users/internal`
- Backend validates all fields
- Backend creates user, contacts, addresses, employee record (if HR data provided)
- Returns created user data

---

## Validation Rules

### Email Validation
- Format: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`
- Must be unique within tenant (cannot duplicate existing contact)

### Phone Validation
- Format: E.164 (`^\+[1-9]\d{1,14}$`)
- Examples: `+447553838399`, `+905551234567`, `+14155551234`
- Must be unique within tenant (cannot duplicate existing contact)

### Address Validation
- If `placeId` provided: Backend validates via Google Maps API
- If `placeId` not provided: Address saved as-is (no validation)

### Required Fields
- `firstName`, `lastName`, `contactValue`, `contactType`, `companyId` are required
- All other fields are optional

---

## Error Handling

**Common Error Responses:**

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "First name is required"
}
```

```json
{
  "success": false,
  "errorCode": "DUPLICATE_CONTACT",
  "message": "Contact already exists: john.doe@example.com"
}
```

```json
{
  "success": false,
  "errorCode": "INVALID_EMAIL",
  "message": "Invalid email format"
}
```

```json
{
  "success": false,
  "errorCode": "INVALID_PHONE",
  "message": "Invalid phone number format. Must be E.164 format (e.g., +905551234567)"
}
```

---

## Best Practices

1. **Load Options First:** Call `/creation-options` when form opens
2. **Use placeId for Addresses:** Always use `placeId` from address search for validation
3. **Validate Before Submit:** Frontend should validate required fields before API call
4. **Handle Errors Gracefully:** Show user-friendly error messages
5. **Auto-generate Employee Number:** Use `/generate-employee-number` endpoint or let backend auto-generate
6. **Debounce Input:** Debounce postcode input for address search (e.g., 500ms)
7. **Store placeId:** Always store `placeId` from selected address for future validation

---

## Related Documentation

- `docs/frontend/ADDRESS_INPUT.md` - Address input API reference
- `docs/phone-email-validation.md` - Phone and email validation details

---

## Contact Suggestions (Optional)

**Endpoint:** `GET /api/common/users/contact-suggestions?companyId={uuid}&firstName={name}&lastName={name}`

**Purpose:** Get intelligent contact suggestions based on company contacts (email suggestions from company domain).

**Use Case:** Minimize manual data entry during user creation.

**Example:**

```http
GET /api/common/users/contact-suggestions?companyId=uuid...&firstName=John&lastName=Doe
```

**Response:**

```json
{
  "success": true,
  "data": {
    "suggestedEmail": "john.doe@example.com",
    "suggestedPhone": "+447553838399",
    "alternativeEmails": [
      "j.doe@example.com",
      "jdoe@example.com"
    ]
  }
}
```

---

**Status:** ✅ Production-Ready  
**Last Updated:** 2025-01-XX

