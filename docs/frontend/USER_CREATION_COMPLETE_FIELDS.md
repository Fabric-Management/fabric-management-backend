# User Creation - Complete Field Reference

**Last Updated:** 2025-01-XX  
**Status:** Production-Ready  
**Endpoint:** `POST /api/common/users/internal`

---

## Overview

This document provides a complete reference for all fields in the `CreateInternalUserRequest` DTO. Every field is documented with its type, validation rules, optional/required status, and allowed values.

**Important:** This is a backend API reference only. No UI/design guidance is provided. All information is based on the actual backend code.

---

## Request Structure

### Complete Request Body

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "contactValue": "john.doe@example.com",
  "contactType": "EMAIL",
  "companyId": "123e4567-e89b-12d3-a456-426614174000",
  "department": "Production",
  "departmentId": "123e4567-e89b-12d3-a456-426614174001",
  "departmentCategoryId": "123e4567-e89b-12d3-a456-426614174002",
  "roleId": "123e4567-e89b-12d3-a456-426614174003",
  "positionId": "123e4567-e89b-12d3-a456-426614174004",
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
  "title": "MR",
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

---

## Field Reference

### 1. Basic User Fields (Required)

#### `firstName` (String, Required)

**Validation:**
- `@NotBlank(message = "First name is required")`
- Cannot be null or empty
- Cannot be blank (whitespace only)

**Example:** `"John"`

**Backend Behavior:** Stored in `common_user.first_name` column (max 100 characters)

---

#### `lastName` (String, Required)

**Validation:**
- `@NotBlank(message = "Last name is required")`
- Cannot be null or empty
- Cannot be blank (whitespace only)

**Example:** `"Doe"`

**Backend Behavior:** Stored in `common_user.last_name` column (max 100 characters). Used to generate `displayName` automatically.

---

#### `contactValue` (String, Required)

**Validation:**
- `@NotBlank(message = "Contact value is required")`
- Cannot be null or empty
- Cannot be blank (whitespace only)
- Must be valid email format if `contactType` is `EMAIL`
- Must be valid E.164 phone format if `contactType` is `PHONE`

**Email Format:**
- Pattern: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`
- Example: `"john.doe@example.com"`

**Phone Format:**
- Pattern: E.164 format `^\+[1-9]\d{1,14}$`
- Must start with `+` followed by country code
- Example: `"+447553838399"`, `"+905551234567"`, `"+14155551234"`

**Backend Behavior:**
- Creates a `Contact` entity with this value
- Sets as primary contact (`isDefault = true`)
- Sets as authentication contact (`isForAuthentication = true`)
- Must be unique within tenant (cannot duplicate existing contact)

**Validation Errors:**
- `"Contact value is required"` - If null or empty
- `"Invalid email format"` - If email format invalid
- `"Invalid phone number format. Must be E.164 format (e.g., +905551234567)"` - If phone format invalid
- `"Contact already exists: {contactValue}"` - If contact already exists in tenant

---

#### `contactType` (Enum, Required)

**Validation:**
- `@NotNull(message = "Contact type is required")`
- Cannot be null
- Must be one of the enum values

**Allowed Values:**
- `"EMAIL"` - Email address (format: `user@example.com`)
- `"PHONE"` - Phone number (format: E.164, e.g., `+905551234567`)

**Backend Behavior:**
- Determines validation pattern for `contactValue`
- `EMAIL`: Validates email format, sends verification code via email
- `PHONE`: Validates E.164 format, sends verification code via WhatsApp (if available) or SMS

---

#### `companyId` (UUID, Required)

**Validation:**
- `@NotNull(message = "Company ID is required")`
- Cannot be null
- Must be a valid UUID format
- Company must exist in current tenant
- Company must be active

**Example:** `"123e4567-e89b-12d3-a456-426614174000"`

**Backend Behavior:**
- Validates company exists: `companyFacade.exists(tenantId, companyId)`
- Throws `IllegalArgumentException("Company not found")` if company doesn't exist
- Stored in `common_user.company_id` column

**Validation Errors:**
- `"Company ID is required"` - If null
- `"Company not found"` - If company doesn't exist or belongs to different tenant

---

### 2. Organizational Fields (Optional)

#### `department` (String, Optional)

**Validation:**
- No validation constraints
- Can be null or empty
- Case-sensitive string match

**Example:** `"Production"`

**Backend Behavior:**
- Used as fallback if `departmentId` is not provided
- Searches for department by name within tenant
- If department not found, user is created without department assignment
- Not recommended: Use `departmentId` instead for reliability

**Note:** This field is deprecated in favor of `departmentId`. Use `departmentId` for better reliability.

---

#### `departmentId` (UUID, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be a valid UUID format if provided
- Department must exist in current tenant
- Department must be active

**Example:** `"123e4567-e89b-12d3-a456-426614174001"`

**Backend Behavior:**
- Preferred method for department assignment
- Validates department exists: `departmentRepository.findByTenantIdAndId(tenantId, departmentId)`
- Assigns user to department via `UserDepartment` junction entity
- Sets as primary department (`isPrimary = true`)
- Throws `IllegalArgumentException("Department not found")` if department doesn't exist

**Validation Errors:**
- `"Department not found"` - If department doesn't exist or belongs to different tenant

---

#### `departmentCategoryId` (UUID, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be a valid UUID format if provided

**Example:** `"123e4567-e89b-12d3-a456-426614174002"`

**Backend Behavior:**
- Currently not used in user creation logic
- Provided for organizational structure reference
- May be used in future features

---

#### `roleId` (UUID, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be a valid UUID format if provided
- Role must exist in current tenant
- Role must be active

**Example:** `"123e4567-e89b-12d3-a456-426614174003"`

**Backend Behavior:**
- Assigns role to user: `user.setRole(role)`
- Validates role exists: `roleService.findById(roleId)`
- Throws `IllegalArgumentException("Role not found")` if role doesn't exist
- Role is used for authorization and permissions

**Validation Errors:**
- `"Role not found"` - If role doesn't exist or belongs to different tenant

---

#### `positionId` (UUID, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be a valid UUID format if provided
- Position must exist in current tenant
- Position must be active

**Example:** `"123e4567-e89b-12d3-a456-426614174004"`

**Backend Behavior:**
- Assigns position to user via `UserPosition` junction entity
- Validates position exists: `positionRepository.findById(positionId)`
- Throws `IllegalArgumentException("Position not found")` if position doesn't exist
- Position is used for HR records and organizational hierarchy

**Validation Errors:**
- `"Position not found"` - If position doesn't exist or belongs to different tenant

---

### 3. Additional Contacts (Optional)

#### `additionalContacts` (Array<ContactData>, Optional)

**Validation:**
- `@Valid` annotation on list items
- Can be null or empty array
- Each item must be valid `ContactData` object

**Default:** Empty array (`[]`)

**Backend Behavior:**
- Creates additional `Contact` entities beyond primary contact
- Each contact is assigned to user via `UserContact` junction entity
- Sets as non-default (`isDefault = false`)
- Sets as non-authentication (`isForAuthentication = false`)
- Contacts can be email or phone
- WhatsApp detection is automatic if `isWhatsApp` is null

**ContactData Structure:**

```typescript
interface ContactData {
  contactValue: string;        // Required: Email or phone (E.164)
  contactType: "EMAIL" | "PHONE";  // Required: Contact type
  label?: string;              // Optional: Label (e.g., "Work Email", "Mobile")
  isPersonal?: boolean;       // Optional: Personal (true) or work (false), default: true
  isWhatsApp?: boolean;        // Optional: WhatsApp capability (PHONE only), auto-detected if null
}
```

**ContactData Field Details:**

**`contactValue` (String, Required)**
- `@NotBlank(message = "Contact value is required")`
- Must be valid email if `contactType` is `EMAIL`
- Must be valid E.164 phone if `contactType` is `PHONE`
- Example: `"work@example.com"` or `"+447553838399"`

**`contactType` (Enum, Required)**
- `@NotNull(message = "Contact type is required")`
- Must be `"EMAIL"` or `"PHONE"`
- Determines validation pattern for `contactValue`

**`label` (String, Optional)**
- No validation constraints
- Can be null
- Example: `"Work Email"`, `"Personal Phone"`, `"Mobile"`

**`isPersonal` (Boolean, Optional)**
- Default: `true` (personal contact)
- If `false`, marks as work contact
- Example: `true` or `false`

**`isWhatsApp` (Boolean, Optional)**
- Only relevant for `PHONE` contacts
- Can be null (auto-detected via WhatsApp API)
- If `true`, verification codes prioritize WhatsApp
- Example: `true`, `false`, or `null`

**Validation Errors:**
- `"Contact value is required"` - If contactValue is null or empty
- `"Contact type is required"` - If contactType is null
- `"Invalid email format"` - If email format invalid
- `"Invalid phone number format"` - If phone format invalid
- `"Contact already exists: {contactValue}"` - If contact already exists in tenant

---

### 4. Addresses (Optional)

#### `addresses` (Array<AddressData>, Optional)

**Validation:**
- `@Valid` annotation on list items
- Can be null or empty array
- Each item must be valid `AddressData` object

**Default:** Empty array (`[]`)

**Backend Behavior:**
- Creates `Address` entities for user
- Each address is assigned to user via `UserAddress` junction entity
- First address becomes primary if `isPrimary` not specified
- If no addresses provided, auto-creates address from company address (if available)
- If `placeId` provided, validates address via Google Maps API

**AddressData Structure:**

```typescript
interface AddressData {
  streetAddress: string;        // Required: Street address
  city: string;                 // Required: City
  state?: string;               // Optional: State/Province
  postalCode?: string;          // Optional: Postal/ZIP code
  country: string;              // Required: Country name
  placeId?: string;             // Optional: Google Maps Place ID (recommended)
  addressType?: string;         // Optional: Address type, default: "WORK"
  label?: string;               // Optional: Label (e.g., "Head Office", "Home Address")
  isPrimary?: boolean;          // Optional: Primary address, default: false
}
```

**AddressData Field Details:**

**`streetAddress` (String, Required)**
- `@NotBlank(message = "Street address is required")`
- Cannot be null or empty
- Example: `"123 Main Street"`

**`city` (String, Required)**
- `@NotBlank(message = "City is required")`
- Cannot be null or empty
- Example: `"London"`

**`state` (String, Optional)**
- No validation constraints
- Can be null
- Example: `"England"`, `"Istanbul"`

**`postalCode` (String, Optional)**
- No validation constraints
- Can be null
- Example: `"MK5 7GE"`, `"34200"`

**`country` (String, Required)**
- `@NotBlank(message = "Country is required")`
- Cannot be null or empty
- Example: `"United Kingdom"`, `"Turkey"`

**`placeId` (String, Optional)**
- No validation constraints
- Can be null
- Google Maps Place ID for address validation
- If provided, address is validated and normalized via Google Maps API
- Example: `"ChIJ..."`

**`addressType` (String, Optional)**
- Default: `"WORK"`
- Allowed values: `"HOME"`, `"WORK"`, `"HEADQUARTERS"`, `"BRANCH"`, `"WAREHOUSE"`, `"SHIPPING"`, `"BILLING"`
- Example: `"HOME"`, `"WORK"`

**AddressType Enum Values:**
- `"HOME"` - Home address (user's personal residential address)
- `"WORK"` - Work address (user's work/office address, independent from company address)
- `"HEADQUARTERS"` - Company headquarters (company's main headquarters location)
- `"BRANCH"` - Branch office (company's branch office location)
- `"WAREHOUSE"` - Warehouse/storage facility (company's warehouse or storage location)
- `"SHIPPING"` - Shipping address (address for shipping/delivery purposes)
- `"BILLING"` - Billing address (address for billing/invoicing purposes)

**`label` (String, Optional)**
- No validation constraints
- Can be null
- Example: `"Head Office"`, `"Home Address"`

**`isPrimary` (Boolean, Optional)**
- Default: `false`
- First address becomes primary if not specified
- Example: `true` or `false`

**Validation Errors:**
- `"Street address is required"` - If streetAddress is null or empty
- `"City is required"` - If city is null or empty
- `"Country is required"` - If country is null or empty

**Note:** For address input, use the address endpoints from `docs/frontend/ADDRESS_INPUT.md` to get validated address data with `placeId`, then use that data in `AddressData`.

---

### 5. HR/Employee Fields (Optional)

#### `title` (Enum, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be one of the enum values if provided

**Allowed Values:**
- `"MR"` - Mister (Adult male)
- `"MISS"` - Miss (Unmarried female)
- `"MRS"` - Mrs (Married female)
- `"MS"` - Ms (Female, marital status neutral)
- `"DR"` - Doctor (Medical or academic)
- `"PROF"` - Professor (Academic)
- `"ENG"` - Engineer (Professional title)
- `"NONE"` - No title (Default)

**Backend Behavior:**
- Stored in `human_employee.employee` table
- Used in formal communications and display names
- Only saved if at least one HR field is provided

---

#### `gender` (Enum, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be one of the enum values if provided

**Allowed Values:**
- `"MALE"` - Male
- `"FEMALE"` - Female
- `"OTHER"` - Other gender identity
- `"PREFER_NOT_TO_SAY"` - Prefer not to say

**Backend Behavior:**
- Stored in `human_employee.employee` table
- Used for HR records and diversity requirements
- Only saved if at least one HR field is provided

---

#### `birthDate` (Date, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be valid ISO 8601 date format if provided (YYYY-MM-DD)
- Should be in the past (not validated, but expected)

**Format:** `YYYY-MM-DD` (ISO 8601)

**Example:** `"1990-01-15"`

**Backend Behavior:**
- Stored as `LocalDate` in `human_employee.employee.birth_date` column
- Used for age calculations and HR requirements
- Only saved if at least one HR field is provided

---

#### `nationality` (String, Optional)

**Validation:**
- No validation constraints
- Can be null
- Should be ISO 3166-1 alpha-2 country code (not validated, but recommended)

**Format:** ISO 3166-1 alpha-2 country code (2 letters)

**Examples:**
- `"GB"` - United Kingdom
- `"TR"` - Turkey
- `"US"` - United States
- `"DE"` - Germany

**Backend Behavior:**
- Stored in `human_employee.employee.nationality` column
- Used for HR records and compliance
- Only saved if at least one HR field is provided

---

#### `employeeNumber` (String, Optional)

**Validation:**
- No validation constraints
- Can be null or empty
- Must be unique within tenant if provided

**Format:** Custom format (e.g., `"EMP-001"`, `"2024-001"`, `"ACME-001-EMP-00042"`)

**Example:** `"ACME-001-EMP-00042"`

**Backend Behavior:**
- Stored in `human_employee.employee.employee_number` column
- Auto-generated if not provided and any HR data exists
- Auto-generation format: `{TENANT_UID}-EMP-{SEQUENCE}` (e.g., `"ACME-001-EMP-00042"`)
- Auto-generation uses global auto-incrementing sequence (not year-based)
- Unique within tenant scope

**Auto-Generation Logic:**
- If `employeeNumber` is null/empty AND any HR field is provided (title, gender, birthDate, nationality, hireDate, emergencyContact), employee number is auto-generated
- Generation endpoint: `GET /api/common/users/generate-employee-number`
- Format: `{TENANT_UID}-EMP-{SEQUENCE}` where SEQUENCE is zero-padded 5-digit number (e.g., `00042`)

---

#### `hireDate` (Date, Optional)

**Validation:**
- No validation constraints
- Can be null
- Must be valid ISO 8601 date format if provided (YYYY-MM-DD)
- Should be in the past or present (not validated, but expected)

**Format:** `YYYY-MM-DD` (ISO 8601)

**Example:** `"2024-01-15"`

**Backend Behavior:**
- Stored as `LocalDate` in `human_employee.employee.hire_date` column
- Used for employment records and tenure calculations
- Only saved if at least one HR field is provided

---

#### `emergencyContact` (EmergencyContactData, Optional)

**Validation:**
- `@Valid` annotation
- Can be null
- All fields within are optional

**Backend Behavior:**
- Stored in `human_employee.employee` table as `EmergencyContact` embedded object
- Only saved if at least one HR field is provided

**EmergencyContactData Structure:**

```typescript
interface EmergencyContactData {
  name?: string;                // Optional: Emergency contact name
  phone?: string;               // Optional: Emergency contact phone (E.164 format)
  relationship?: string;        // Optional: Relationship (e.g., "Spouse", "Parent")
}
```

**EmergencyContactData Field Details:**

**`name` (String, Optional)**
- No validation constraints
- Can be null
- Example: `"Jane Doe"`

**`phone` (String, Optional)**
- No validation constraints
- Can be null
- Should be E.164 format if provided (not validated, but recommended)
- Example: `"+447553838400"`

**`relationship` (String, Optional)**
- No validation constraints
- Can be null
- Free-text field for relationship description
- Example: `"Spouse"`, `"Parent"`, `"Sibling"`, `"Friend"`

---

## Complete Field Summary

### Required Fields (5)

1. `firstName` (String) - First name
2. `lastName` (String) - Last name
3. `contactValue` (String) - Email or phone (E.164)
4. `contactType` (Enum) - "EMAIL" or "PHONE"
5. `companyId` (UUID) - Company ID

### Optional Fields (13)

1. `department` (String) - Department name (deprecated, use departmentId)
2. `departmentId` (UUID) - Department ID (preferred)
3. `departmentCategoryId` (UUID) - Department category ID
4. `roleId` (UUID) - Role ID
5. `positionId` (UUID) - Position ID
6. `additionalContacts` (Array<ContactData>) - Additional contacts
7. `addresses` (Array<AddressData>) - User addresses
8. `title` (Enum) - Personal title/salutation
9. `gender` (Enum) - Gender identity
10. `birthDate` (Date) - Birth date (ISO 8601)
11. `nationality` (String) - ISO 3166-1 alpha-2 country code
12. `employeeNumber` (String) - Employee number (auto-generated if HR data exists)
13. `hireDate` (Date) - Employment start date (ISO 8601)
14. `emergencyContact` (EmergencyContactData) - Emergency contact information

---

## Validation Rules Summary

### Email Validation
- Pattern: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`
- Example: `"john.doe@example.com"`

### Phone Validation
- Pattern: E.164 format `^\+[1-9]\d{1,14}$`
- Must start with `+` followed by country code
- Example: `"+447553838399"`, `"+905551234567"`

### Date Validation
- Format: ISO 8601 `YYYY-MM-DD`
- Example: `"1990-01-15"`, `"2024-01-15"`

### UUID Validation
- Format: Standard UUID format
- Example: `"123e4567-e89b-12d3-a456-426614174000"`

### Nationality Validation
- Format: ISO 3166-1 alpha-2 country code (2 letters)
- Example: `"GB"`, `"TR"`, `"US"`

---

## Backend Behavior Summary

### Auto-Generated Fields

1. **`displayName`** - Auto-generated from `firstName` and `lastName` (format: `"{firstName} {lastName}"`)
2. **`uid`** - Auto-generated user UID (format: `"{TENANT_UID}-USR-{SEQUENCE}"`)
3. **`employeeNumber`** - Auto-generated if not provided and any HR data exists (format: `"{TENANT_UID}-EMP-{SEQUENCE}"`)

### Auto-Created Entities

1. **Primary Contact** - Created from `contactValue` and `contactType`
2. **Additional Contacts** - Created from `additionalContacts` array
3. **Addresses** - Created from `addresses` array
4. **Employee Record** - Created if any HR field provided
5. **UserDepartment** - Created if `departmentId` provided
6. **UserPosition** - Created if `positionId` provided

### Default Values

1. **`additionalContacts`** - Empty array `[]`
2. **`addresses`** - Empty array `[]`
3. **`addressType`** (in AddressData) - `"WORK"`
4. **`isPrimary`** (in AddressData) - `false` (first address becomes primary)
5. **`isPersonal`** (in ContactData) - `true`

---

## Error Responses

### Validation Errors

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "First name is required"
}
```

### Business Logic Errors

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
  "errorCode": "NOT_FOUND",
  "message": "Company not found"
}
```

---

## Related Documentation

- `docs/frontend/CREATE_USER.md` - User creation API reference
- `docs/frontend/ADDRESS_INPUT.md` - Address input API reference
- `docs/frontend/ROLE_DEPARTMENT_POSITION.md` - Roles, departments, positions API reference
- `docs/phone-email-validation.md` - Phone and email validation details

---

**Status:** ✅ Production-Ready  
**Last Updated:** 2025-01-XX

