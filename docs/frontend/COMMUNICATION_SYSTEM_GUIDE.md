# Communication System Guide - Frontend Integration

**Last Updated:** 2025-01-10  
**Status:** Active  
**Purpose:** Guide for frontend developers integrating with the new centralized Communication & Address Management system.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Breaking Changes](#breaking-changes)
3. [New Architecture](#new-architecture)
4. [API Endpoints](#api-endpoints)
5. [Data Models](#data-models)
6. [Google Maps Integration](#google-maps-integration)
7. [Authentication Updates](#authentication-updates)
8. [Migration Checklist](#migration-checklist)
9. [Code Examples](#code-examples)
10. [Common Patterns](#common-patterns)

---

## 🎯 Overview

### What Changed?

The system has been **completely redesigned** for managing contacts and addresses:

- **Before:** Contact/address data embedded in `User` and `Company` entities
- **After:** Centralized `Contact` and `Address` entities with junction tables (`UserContact`, `CompanyContact`, `UserAddress`, `CompanyAddress`)

### Key Benefits

✅ **Multiple contacts/addresses per user/company**  
✅ **Flexible contact types** (Email, Phone, WhatsApp, Extension, etc.)  
✅ **Flexible address types** (Home, Work, Headquarters, Warehouse, etc.)  
✅ **Google Maps validation** for address standardization  
✅ **Extensible** - Easy to add new contact/address types  
✅ **Normalized** - No data duplication

---

## ⚠️ Breaking Changes

### 1. User Entity

**REMOVED FIELDS:**
```typescript
// ❌ These fields no longer exist:
interface UserDto {
  contactValue?: string;      // REMOVED
  contactType?: string;       // REMOVED
}
```

**NEW APPROACH:**
```typescript
// ✅ Fetch contacts via separate endpoint
GET /api/common/users/{userId}/contacts
GET /api/common/users/{userId}/contacts/authentication  // For login
GET /api/common/users/{userId}/contacts/default          // For notifications
```

### 2. Company Entity

**NO BREAKING CHANGES** - Company DTO unchanged, but contacts/addresses accessed via separate endpoints.

### 3. Authentication Flow

**JWT Token** - Still contains `contactValue` (subject), but it's now read from primary authentication contact.

**Login Request** - Unchanged (still uses `contactValue`).

**Registration** - Uses new Contact entity internally, but API unchanged.

---

## 🏗️ New Architecture

### Entity Relationships

```
User/Company ←→ UserContact/CompanyContact ←→ Contact
User/Company ←→ UserAddress/CompanyAddress ←→ Address
```

### Flow Example: Adding Contact to User

1. **Create Contact** (if not exists):
   ```typescript
   POST /api/common/contacts
   Body: { contactValue: "john@example.com", contactType: "EMAIL", ... }
   Response: { id: "contact-uuid", ... }
   ```

2. **Assign to User**:
   ```typescript
   POST /api/common/users/{userId}/contacts
   Body: { contactId: "contact-uuid", isDefault: true, isForAuthentication: true }
   ```

3. **Or use convenience endpoint**:
   ```typescript
   POST /api/common/users/{userId}/contacts/create-and-assign
   Body: { contactValue: "john@example.com", contactType: "EMAIL", ... }
   Query: ?isDefault=true&isForAuthentication=true
   ```

---

## 📡 API Endpoints

### Base URLs

- **Contacts:** `/api/common/contacts`
- **Addresses:** `/api/common/addresses`
- **User Contacts:** `/api/common/users/{userId}/contacts`
- **Company Contacts:** `/api/common/companies/{companyId}/contacts`
- **User Addresses:** `/api/common/users/{userId}/addresses`
- **Company Addresses:** `/api/common/companies/{companyId}/addresses`
- **Address Validation:** `/api/common/addresses/validation`

---

### Contact Endpoints

#### Create Contact
```http
POST /api/common/contacts
Content-Type: application/json

{
  "contactValue": "john@example.com",
  "contactType": "EMAIL",          // EMAIL, PHONE, WHATSAPP, etc.
  "label": "Work Email",
  "isPersonal": false
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "contactValue": "john@example.com",
    "contactType": "EMAIL",
    "isVerified": false,
    "isPrimary": false,
    "label": "Work Email",
    "isPersonal": false
  }
}
```

#### Get Contact
```http
GET /api/common/contacts/{contactId}
```

#### Verify Contact
```http
PUT /api/common/contacts/{contactId}/verify
```

---

### User Contact Endpoints

#### Get User Contacts
```http
GET /api/common/users/{userId}/contacts
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "user-contact-uuid",
      "contactId": "contact-uuid",
      "contact": {
        "contactValue": "john@example.com",
        "contactType": "EMAIL"
      },
      "isDefault": true,
      "isForAuthentication": true
    }
  ]
}
```

#### Get Authentication Contact
```http
GET /api/common/users/{userId}/contacts/authentication
```

#### Get Default Contact
```http
GET /api/common/users/{userId}/contacts/default
```

#### Create and Assign Contact (Recommended)
```http
POST /api/common/users/{userId}/contacts/create-and-assign?isDefault=true&isForAuthentication=true
Content-Type: application/json

{
  "contactValue": "john@example.com",
  "contactType": "EMAIL",
  "label": "Primary Email",
  "isPersonal": false
}
```

#### Assign Existing Contact
```http
POST /api/common/users/{userId}/contacts
Content-Type: application/json

{
  "contactId": "existing-contact-uuid",
  "isDefault": true,
  "isForAuthentication": true
}
```

#### Set as Default
```http
PUT /api/common/users/{userId}/contacts/{contactId}/default
```

#### Enable for Authentication
```http
PUT /api/common/users/{userId}/contacts/{contactId}/enable-auth
```

#### Remove Contact
```http
DELETE /api/common/users/{userId}/contacts/{contactId}
```

---

### Company Contact Endpoints

#### Get Company Contacts
```http
GET /api/common/companies/{companyId}/contacts
```

#### Get Default Contact
```http
GET /api/common/companies/{companyId}/contacts/default
```

#### Get Department Contacts
```http
GET /api/common/companies/{companyId}/contacts/department/{departmentName}
```

#### Create and Assign Contact
```http
POST /api/common/companies/{companyId}/contacts/create-and-assign?isDefault=true&department=Sales
Content-Type: application/json

{
  "contactValue": "sales@company.com",
  "contactType": "EMAIL",
  "label": "Sales Department",
  "isPersonal": false
}
```

---

### Address Endpoints

#### Create Address
```http
POST /api/common/addresses
Content-Type: application/json

{
  "streetAddress": "123 Main St",
  "city": "Istanbul",
  "state": null,
  "postalCode": "34000",
  "country": "Turkey",
  "addressType": "HOME",        // HOME, WORK, HEADQUARTERS, etc.
  "label": "Home Address"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "streetAddress": "123 Main St",
    "city": "Istanbul",
    "country": "Turkey",
    "addressType": "HOME",
    "isPrimary": false,
    "formattedAddress": "123 Main St, Istanbul, Turkey",
    "latitude": 41.0082,
    "longitude": 28.9784,
    "placeId": "ChIJ..."
  }
}
```

---

### User Address Endpoints

#### Get User Addresses
```http
GET /api/common/users/{userId}/addresses
```

#### Get Primary Address
```http
GET /api/common/users/{userId}/addresses/primary
```

#### Get Work Addresses
```http
GET /api/common/users/{userId}/addresses/work
```

#### Create and Assign Address
```http
POST /api/common/users/{userId}/addresses/create-and-assign?isPrimary=true&isWorkAddress=false
Content-Type: application/json

{
  "streetAddress": "123 Main St",
  "city": "Istanbul",
  "country": "Turkey",
  "addressType": "HOME",
  "label": "Home"
}
```

---

### Company Address Endpoints

#### Get Company Addresses
```http
GET /api/common/companies/{companyId}/addresses
```

#### Get Primary Address
```http
GET /api/common/companies/{companyId}/addresses/primary
```

#### Get Headquarters
```http
GET /api/common/companies/{companyId}/addresses/headquarters
```

#### Create and Assign Address
```http
POST /api/common/companies/{companyId}/addresses/create-and-assign?isPrimary=true&isHeadquarters=true
Content-Type: application/json

{
  "streetAddress": "456 Business Ave",
  "city": "Istanbul",
  "country": "Turkey",
  "addressType": "HEADQUARTERS",
  "label": "Main Office"
}
```

---

### Address Validation Endpoints (Google Maps)

#### Autocomplete
```http
POST /api/common/addresses/validation/autocomplete
Content-Type: application/json

{
  "input": "123 Main St, Ista",
  "country": "TR"              // Optional: ISO country code
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "predictions": [
      {
        "placeId": "ChIJ...",
        "description": "123 Main Street, Istanbul, Turkey",
        "structuredFormatting": {
          "mainText": "123 Main Street",
          "secondaryText": "Istanbul, Turkey"
        }
      }
    ]
  }
}
```

#### Validate Address
```http
POST /api/common/addresses/validation/validate
Content-Type: application/json

{
  "placeId": "ChIJ...",
  "addressType": "HOME"         // Optional
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "verificationStatus": "VERIFIED",    // VERIFIED, PARTIAL, FAILED
    "address": {
      "streetAddress": "123 Main Street",
      "city": "Istanbul",
      "country": "Turkey",
      "formattedAddress": "123 Main Street, Istanbul, Turkey",
      "latitude": 41.0082,
      "longitude": 28.9784,
      "placeId": "ChIJ..."
    }
  }
}
```

#### Validate and Create
```http
POST /api/common/addresses/validation/validate-and-create
Content-Type: application/json

{
  "placeId": "ChIJ...",
  "addressType": "HOME",
  "label": "Home Address"
}
```

**Response:** Returns `AddressDto` with normalized address data.

#### Revalidate Existing Address
```http
POST /api/common/addresses/validation/{addressId}/revalidate
```

---

## 📊 Data Models

### ContactType Enum

```typescript
enum ContactType {
  EMAIL = "EMAIL",
  PHONE = "PHONE",
  PHONE_EXTENSION = "PHONE_EXTENSION",  // Extension number
  FAX = "FAX",
  WEBSITE = "WEBSITE",
  WHATSAPP = "WHATSAPP",
  SOCIAL_MEDIA = "SOCIAL_MEDIA"
}
```

### AddressType Enum

```typescript
enum AddressType {
  HOME = "HOME",
  WORK = "WORK",
  HEADQUARTERS = "HEADQUARTERS",
  BRANCH = "BRANCH",
  WAREHOUSE = "WAREHOUSE",
  SHIPPING = "SHIPPING",
  BILLING = "BILLING"
}
```

### ContactDto

```typescript
interface ContactDto {
  id: string;                    // UUID
  tenantId: string;              // UUID
  uid: string;                    // Auto-generated UID
  contactValue: string;           // e.g., "john@example.com", "+905551234567"
  contactType: ContactType;
  isVerified: boolean;
  isPrimary: boolean;              // Global primary flag
  label?: string;                 // User-friendly label
  parentContactId?: string;       // For PHONE_EXTENSION
  isPersonal: boolean;            // true for user contacts, false for company
  isActive: boolean;
  createdAt: string;              // ISO 8601
  updatedAt: string;
}
```

### AddressDto

```typescript
interface AddressDto {
  id: string;                     // UUID
  tenantId: string;               // UUID
  uid: string;                    // Auto-generated UID
  streetAddress: string;
  city: string;
  state?: string;                 // Optional (not used in Turkey)
  district?: string;             // Ilce (Turkey)
  postalCode?: string;
  country: string;
  countryCode?: string;           // ISO 3166-1 alpha-2 (e.g., "TR")
  addressType: AddressType;
  isPrimary: boolean;
  label?: string;
  formattedAddress?: string;      // Google Maps formatted
  placeId?: string;               // Google Places ID
  latitude?: number;               // Google Maps coordinates
  longitude?: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}
```

### UserContactDto

```typescript
interface UserContactDto {
  id: string;                      // Junction table ID
  userId: string;
  contactId: string;
  contact: ContactDto;            // Full contact details
  isDefault: boolean;              // Default for notifications
  isForAuthentication: boolean;    // Can be used for login
}
```

### CompanyContactDto

```typescript
interface CompanyContactDto {
  id: string;                      // Junction table ID
  companyId: string;
  contactId: string;
  contact: ContactDto;            // Full contact details
  isDefault: boolean;              // Default for business communication
  department?: string;             // Department name (optional)
}
```

### UserAddressDto

```typescript
interface UserAddressDto {
  id: string;                      // Junction table ID
  userId: string;
  addressId: string;
  address: AddressDto;             // Full address details
  isPrimary: boolean;              // Primary address
  isWorkAddress: boolean;          // Is work address
}
```

### CompanyAddressDto

```typescript
interface CompanyAddressDto {
  id: string;                      // Junction table ID
  companyId: string;
  addressId: string;
  address: AddressDto;             // Full address details
  isPrimary: boolean;              // Primary address
  isHeadquarters: boolean;         // Is headquarters
}
```

---

## 🗺️ Google Maps Integration

### Overview

The system uses **Google Maps Platform** APIs for address validation and autocomplete:

- **Places API (Autocomplete)** - Real-time address suggestions
- **Geocoding API** - Address validation and normalization

### Configuration

Address validation requires `GOOGLE_MAPS_API_KEY` to be configured on backend (already set).

### Usage Flow

#### 1. Address Input with Autocomplete

```typescript
// Component example
import { useState } from 'react';
import axios from 'axios';

function AddressInput({ onAddressSelected }) {
  const [input, setInput] = useState('');
  const [suggestions, setSuggestions] = useState([]);

  const handleInputChange = async (value: string) => {
    setInput(value);
    
    if (value.length < 3) {
      setSuggestions([]);
      return;
    }

    try {
      const response = await axios.post(
        '/api/common/addresses/validation/autocomplete',
        { input: value, country: 'TR' }
      );
      
      setSuggestions(response.data.data.predictions);
    } catch (error) {
      console.error('Autocomplete error:', error);
    }
  };

  const handleSelectSuggestion = async (placeId: string) => {
    try {
      const response = await axios.post(
        '/api/common/addresses/validation/validate-and-create',
        { placeId, addressType: 'HOME', label: 'Home' }
      );
      
      onAddressSelected(response.data.data);
    } catch (error) {
      console.error('Validation error:', error);
    }
  };

  return (
    <div>
      <input
        value={input}
        onChange={(e) => handleInputChange(e.target.value)}
        placeholder="Start typing address..."
      />
      <ul>
        {suggestions.map((prediction) => (
          <li
            key={prediction.placeId}
            onClick={() => handleSelectSuggestion(prediction.placeId)}
          >
            {prediction.description}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

#### 2. Manual Address Validation

```typescript
async function validateAddress(placeId: string) {
  const response = await axios.post(
    '/api/common/addresses/validation/validate',
    { placeId, addressType: 'HOME' }
  );

  if (response.data.data.verificationStatus === 'VERIFIED') {
    // Address is valid, use response.data.data.address
    return response.data.data.address;
  } else {
    throw new Error('Address validation failed');
  }
}
```

### Response Statuses

- **VERIFIED** - Address is valid and normalized
- **PARTIAL** - Address is partially valid (may need user input)
- **FAILED** - Address validation failed

---

## 🔐 Authentication Updates

### Login (Unchanged)

```typescript
POST /api/auth/login
Body: {
  contactValue: "john@example.com",  // Still uses contactValue
  password: "password123"
}
```

**Note:** Backend internally resolves `contactValue` to `Contact` entity via `UserContact` junction.

### Registration (Unchanged API, New Internals)

```typescript
// Step 1: Check eligibility
POST /api/auth/register/check
Body: {
  contactValue: "john@example.com"
}

// Step 2: Verify and register
POST /api/auth/register/verify
Body: {
  contactValue: "john@example.com",
  code: "123456",
  password: "password123"
}
```

**Note:** Backend automatically creates `Contact` entity and links via `UserContact` with `isForAuthentication: true`.

### JWT Token (Unchanged Structure)

Token still contains `contactValue` in subject, but it's now read from primary authentication contact:

```typescript
{
  sub: "john@example.com",           // contactValue
  user_id: "uuid",
  tenant_id: "uuid",
  firstName: "John",
  lastName: "Doe",
  department: "Engineering",          // From UserDepartment (primary)
  role: "Manager",                    // From Role (display only)
  role_id: "uuid",
  // ... other claims
}
```

---

## 📝 Migration Checklist

### 1. Remove Direct Contact/Address Fields

**Before:**
```typescript
// ❌ Don't use these anymore
interface User {
  contactValue?: string;
  contactType?: string;
}
```

**After:**
```typescript
// ✅ Fetch separately
const userContacts = await getUserContacts(userId);
const authContact = await getAuthenticationContact(userId);
```

### 2. Update User Profile Component

**Before:**
```typescript
function UserProfile({ user }) {
  return (
    <div>
      <p>Email: {user.contactValue}</p>
    </div>
  );
}
```

**After:**
```typescript
function UserProfile({ user }) {
  const [contacts, setContacts] = useState([]);

  useEffect(() => {
    fetchUserContacts(user.id).then(setContacts);
  }, [user.id]);

  const emailContact = contacts.find(
    c => c.contact.contactType === 'EMAIL' && c.isDefault
  );

  return (
    <div>
      <p>Email: {emailContact?.contact.contactValue}</p>
    </div>
  );
}
```

### 3. Update Address Forms

**Before:**
```typescript
// Simple text input
<input name="address" value={user.address} />
```

**After:**
```typescript
// Use Google Maps autocomplete component
<AddressInput 
  onAddressSelected={(address) => {
    assignAddressToUser(userId, address.id);
  }}
/>
```

### 4. Update Contact Management UI

**Before:**
```typescript
// Single contact field
<input name="email" value={user.contactValue} />
```

**After:**
```typescript
// List of contacts with add/remove
{contacts.map(contact => (
  <ContactItem 
    key={contact.id}
    contact={contact}
    onRemove={() => removeContact(userId, contact.contactId)}
  />
))}
<AddContactButton 
  onSubmit={(contact) => createAndAssignContact(userId, contact)}
/>
```

---

## 💻 Code Examples

### Example 1: User Profile with Contacts

```typescript
import { useState, useEffect } from 'react';
import axios from 'axios';

interface UserProfileProps {
  userId: string;
}

function UserProfile({ userId }: UserProfileProps) {
  const [contacts, setContacts] = useState<UserContactDto[]>([]);
  const [addresses, setAddresses] = useState<UserAddressDto[]>([]);

  useEffect(() => {
    // Fetch contacts
    axios.get(`/api/common/users/${userId}/contacts`)
      .then(res => setContacts(res.data.data));

    // Fetch addresses
    axios.get(`/api/common/users/${userId}/addresses`)
      .then(res => setAddresses(res.data.data));
  }, [userId]);

  const defaultContact = contacts.find(c => c.isDefault);
  const authContact = contacts.find(c => c.isForAuthentication);
  const primaryAddress = addresses.find(a => a.isPrimary);

  return (
    <div>
      <h2>Contact Information</h2>
      <p>Default: {defaultContact?.contact.contactValue}</p>
      <p>Auth: {authContact?.contact.contactValue}</p>
      
      <h2>Addresses</h2>
      <p>Primary: {primaryAddress?.address.formattedAddress}</p>
      
      <ContactList contacts={contacts} userId={userId} />
      <AddressList addresses={addresses} userId={userId} />
    </div>
  );
}
```

### Example 2: Add Contact to User

```typescript
async function addEmailContact(userId: string, email: string) {
  try {
    const response = await axios.post(
      `/api/common/users/${userId}/contacts/create-and-assign`,
      {
        contactValue: email,
        contactType: 'EMAIL',
        label: 'Work Email',
        isPersonal: false
      },
      {
        params: {
          isDefault: true,
          isForAuthentication: true
        }
      }
    );

    console.log('Contact added:', response.data.data);
    return response.data.data;
  } catch (error) {
    console.error('Failed to add contact:', error);
    throw error;
  }
}
```

### Example 3: Add Address with Validation

```typescript
async function addValidatedAddress(
  userId: string, 
  placeId: string, 
  addressType: AddressType
) {
  try {
    // Validate and create address
    const validateResponse = await axios.post(
      '/api/common/addresses/validation/validate-and-create',
      {
        placeId,
        addressType,
        label: addressType === 'HOME' ? 'Home' : 'Work'
      }
    );

    const address = validateResponse.data.data;

    // Assign to user
    const assignResponse = await axios.post(
      `/api/common/users/${userId}/addresses`,
      {
        addressId: address.id,
        isPrimary: true,
        isWorkAddress: addressType === 'WORK'
      }
    );

    return assignResponse.data.data;
  } catch (error) {
    console.error('Failed to add address:', error);
    throw error;
  }
}
```

### Example 4: Company Contact Management

```typescript
async function addDepartmentContact(
  companyId: string,
  email: string,
  department: string
) {
  const response = await axios.post(
    `/api/common/companies/${companyId}/contacts/create-and-assign`,
    {
      contactValue: email,
      contactType: 'EMAIL',
      label: `${department} Email`,
      isPersonal: false
    },
    {
      params: {
        isDefault: false,
        department
      }
    }
  );

  return response.data.data;
}

// Get all contacts for a department
async function getDepartmentContacts(
  companyId: string,
  department: string
) {
  const response = await axios.get(
    `/api/common/companies/${companyId}/contacts/department/${department}`
  );

  return response.data.data;
}
```

### Example 5: Address Autocomplete Hook

```typescript
import { useState, useCallback } from 'react';
import axios from 'axios';

interface UseAddressAutocompleteOptions {
  country?: string;
  debounceMs?: number;
}

function useAddressAutocomplete(options: UseAddressAutocompleteOptions = {}) {
  const { country = 'TR', debounceMs = 300 } = options;
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = useCallback(
    debounce(async (input: string) => {
      if (input.length < 3) {
        setSuggestions([]);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const response = await axios.post(
          '/api/common/addresses/validation/autocomplete',
          { input, country }
        );

        setSuggestions(response.data.data.predictions);
      } catch (err: any) {
        setError(err.message || 'Autocomplete failed');
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, debounceMs),
    [country]
  );

  return { suggestions, loading, error, search };
}

// Usage
function AddressInput() {
  const { suggestions, loading, search } = useAddressAutocomplete();

  return (
    <div>
      <input
        onChange={(e) => search(e.target.value)}
        placeholder="Type address..."
      />
      {loading && <div>Loading...</div>}
      <ul>
        {suggestions.map((s) => (
          <li key={s.placeId}>{s.description}</li>
        ))}
      </ul>
    </div>
  );
}
```

---

## 🎨 Common Patterns

### Pattern 1: Fetch Full User with Contacts/Addresses

```typescript
async function getUserWithDetails(userId: string) {
  const [user, contacts, addresses] = await Promise.all([
    axios.get(`/api/common/users/${userId}`),
    axios.get(`/api/common/users/${userId}/contacts`),
    axios.get(`/api/common/users/${userId}/addresses`)
  ]);

  return {
    user: user.data.data,
    contacts: contacts.data.data,
    addresses: addresses.data.data
  };
}
```

### Pattern 2: Primary Contact Helper

```typescript
function getPrimaryContact(contacts: UserContactDto[]): ContactDto | null {
  const primary = contacts.find(c => c.isDefault);
  return primary?.contact || null;
}

function getAuthContact(contacts: UserContactDto[]): ContactDto | null {
  const auth = contacts.find(c => c.isForAuthentication);
  return auth?.contact || null;
}
```

### Pattern 3: Address Formatting

```typescript
function formatAddress(address: AddressDto): string {
  // Prefer Google Maps formatted address if available
  if (address.formattedAddress) {
    return address.formattedAddress;
  }

  // Fallback to manual formatting
  const parts = [
    address.streetAddress,
    address.district,
    address.city,
    address.country
  ].filter(Boolean);

  return parts.join(', ');
}
```

### Pattern 4: Contact Type Icon Mapping

```typescript
const CONTACT_TYPE_ICONS: Record<ContactType, string> = {
  EMAIL: '📧',
  PHONE: '📞',
  PHONE_EXTENSION: '📱',
  FAX: '📠',
  WEBSITE: '🌐',
  WHATSAPP: '💬',
  SOCIAL_MEDIA: '🔗'
};

function getContactIcon(contactType: ContactType): string {
  return CONTACT_TYPE_ICONS[contactType] || '📌';
}
```

### Pattern 5: Address Type Label Mapping

```typescript
const ADDRESS_TYPE_LABELS: Record<AddressType, string> = {
  HOME: 'Home',
  WORK: 'Work',
  HEADQUARTERS: 'Headquarters',
  BRANCH: 'Branch',
  WAREHOUSE: 'Warehouse',
  SHIPPING: 'Shipping Address',
  BILLING: 'Billing Address'
};

function getAddressTypeLabel(addressType: AddressType): string {
  return ADDRESS_TYPE_LABELS[addressType] || addressType;
}
```

---

## ⚡ Best Practices

### 1. Always Use Convenience Endpoints When Possible

**Prefer:**
```typescript
POST /api/common/users/{userId}/contacts/create-and-assign
```

**Instead of:**
```typescript
POST /api/common/contacts
POST /api/common/users/{userId}/contacts
```

### 2. Use Google Maps Validation for User-Entered Addresses

Always validate addresses through Google Maps API to ensure:
- Standardized format
- Accurate coordinates
- Verified existence

### 3. Cache Contact/Address Lists

Contact and address lists don't change frequently. Cache them in your state management solution (Redux, Zustand, etc.).

### 4. Handle Multiple Contacts/Addresses

Users and companies can have multiple contacts/addresses. Always handle lists, not single values.

### 5. Distinguish Between Contact Types

- **Authentication Contact:** Used for login (`isForAuthentication: true`)
- **Default Contact:** Used for notifications (`isDefault: true`)
- **Personal vs Company:** Use `isPersonal` flag appropriately

---

## 🐛 Error Handling

### Common Errors

#### 1. Contact Not Found
```json
{
  "success": false,
  "error": "CONTACT_NOT_FOUND",
  "message": "Contact not found"
}
```

#### 2. Invalid Contact Type
```json
{
  "success": false,
  "error": "VALIDATION_ERROR",
  "message": "Invalid contact type"
}
```

#### 3. Address Validation Failed
```json
{
  "success": false,
  "error": "VALIDATION_FAILED",
  "message": "Address validation failed"
}
```

### Error Handling Example

```typescript
try {
  await addContact(userId, contact);
} catch (error: any) {
  if (error.response?.status === 404) {
    // Handle not found
  } else if (error.response?.data?.error === 'VALIDATION_ERROR') {
    // Handle validation error
  } else {
    // Handle generic error
  }
}
```

---

## 📚 Additional Resources

- **Backend Architecture:** See `docs/analysis/ADDRESS_COMMUNICATION_ANALYSIS.md`
- **API Documentation:** Swagger UI at `/swagger-ui.html`
- **Google Maps API Docs:** https://developers.google.com/maps/documentation

---

## ❓ FAQ

### Q: Can a user have multiple authentication contacts?
**A:** No. Only one contact per user can have `isForAuthentication: true`.

### Q: What happens if I delete a contact that's used for authentication?
**A:** You cannot delete a contact that's used for authentication. You must first disable it or assign another contact as authentication contact.

### Q: Can I reuse a contact for multiple users?
**A:** Yes, but contacts are tenant-scoped. Different users in the same tenant can share contacts, but it's recommended to create separate contacts for better data isolation.

### Q: Do I need to validate all addresses through Google Maps?
**A:** Not required, but **strongly recommended** for accuracy and standardization. Manual addresses without validation will not have `placeId`, `latitude`, `longitude`, or `formattedAddress`.

### Q: How do I handle phone extensions?
**A:** Create a parent `PHONE` contact first, then create a `PHONE_EXTENSION` contact with `parentContactId` pointing to the parent.

```typescript
// Step 1: Create parent phone
const parentPhone = await createContact({
  contactValue: "+905551234567",
  contactType: "PHONE"
});

// Step 2: Create extension
const extension = await createContact({
  contactValue: "123",
  contactType: "PHONE_EXTENSION",
  parentContactId: parentPhone.id
});
```

---

**Questions?** Contact backend team or check Swagger documentation.

