# Address Validation API Integration Guide

**Last Updated:** 2025-01-29 14:30 UTC+3  
**Purpose:**  
This document provides a technical guide for frontend developers to correctly integrate with the backend address validation and creation endpoints powered by Google Maps Platform.

---

## 1. Overview

The backend handles address search, validation, and persistence using **Google Maps APIs**.  
All data returned by these endpoints comes directly from Google (no local caching).

**Key Features:**
- Global coverage (no country restriction)
- Postcode or text-based search
- Google Places and Geocoding integration
- Validation and persistence combined in single or separate steps
- Real-time verification with `placeId`

**Base URL:**  
```
/api/common/addresses/validation
```


---

## 2. Available Endpoints

| Endpoint | Method | Description |
|-----------|--------|-------------|
| `/search-by-postcode` | `GET` | Searches address list by postcode (and optional country) |
| `/autocomplete` | `GET` | Returns real-time address suggestions as user types |
| `/validate` | `POST` | Validates address (without saving to database) |
| `/validate-and-create` | `POST` | Validates and persists address in a single operation |
| `/{addressId}/revalidate` | `POST` | Revalidates an existing address and updates with latest Google data |

---

## 3. Integration Flow

### Step 1 — Search by Postcode (Preferred)

**Endpoint:**  


GET /api/common/addresses/validation/search-by-postcode


**Parameters:**
- `postcode` (required)
- `country` (optional — ISO 3166-1 alpha-2 or country name)

**Example Request:**


GET /api/common/addresses/validation/search-by-postcode?postcode=MK5%207GE&country=GB


**Response:**
```json
{
  "success": true,
  "data": [
    {
      "verificationStatus": "VERIFIED",
      "placeId": "ChIJ...",
      "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
      "streetAddress": "123 Main Street",
      "city": "London",
      "state": "England",
      "district": "Westminster",
      "postalCode": "MK5 7GE",
      "country": "United Kingdom",
      "countryCode": "GB",
      "latitude": 52.0406,
      "longitude": -0.7594,
      "errorMessage": null
    }
  ]
}


Usage Notes:

Use this as the first call to fetch address candidates.

Always pass country if known for more accurate results.

If the response array is empty, allow fallback to autocomplete.

Step 2 — (Optional) Autocomplete Search

Endpoint:

GET /api/common/addresses/validation/autocomplete


Parameters:

input (required): free text typed by user

country (optional): ISO code (e.g., TR, GB)

**Response Example:**
```json
{
  "success": true,
  "data": {
    "predictions": [
      {
        "placeId": "ChIJ...",
        "description": "Taksim Square, Beyoglu, Istanbul, Turkey",
        "mainText": "Taksim Square",
        "secondaryText": "Beyoglu, Istanbul, Turkey"
      }
    ]
  }
}
```


Usage Notes:

Use autocomplete when postcode search is insufficient (e.g., Turkey).

Return list items contain placeId, which is required for validation in next steps.

Step 3 — Validate Address (Without Saving)

Endpoint:

POST /api/common/addresses/validation/validate


Body:

{
  "placeId": "ChIJ...",
  "addressType": "WORK"
}


**Response Example:**
```json
{
  "success": true,
  "data": {
    "verificationStatus": "VERIFIED",
    "placeId": "ChIJ...",
    "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
    "streetAddress": "123 Main Street",
    "city": "London",
    "state": "England",
    "district": "Westminster",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "countryCode": "GB",
    "latitude": 52.0406,
    "longitude": -0.7594,
    "errorMessage": null
  },
  "message": "Address validated successfully"
}
```


Usage Notes:

Use when the user wants to confirm an address before saving it.

Response contains standardized and verified address components.

Step 4 — Validate and Create Address

Endpoint:

POST /api/common/addresses/validation/validate-and-create


Body:

{
  "placeId": "ChIJ...",
  "addressType": "HOME",
  "label": "Home Address"
}


**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "tenantId": "uuid...",
    "uid": "ADDR-...",
    "streetAddress": "123 Main Street",
    "city": "London",
    "state": "England",
    "district": "Westminster",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "countryCode": "GB",
    "addressType": "HOME",
    "isPrimary": false,
    "label": "Home Address",
    "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
    "placeId": "ChIJ...",
    "latitude": 52.0406,
    "longitude": -0.7594,
    "isActive": true,
    "createdAt": "2025-01-29T14:30:00Z",
    "updatedAt": "2025-01-29T14:30:00Z"
  },
  "message": "Address validated and created successfully"
}
```

**Note:** `addressType` is returned as enum string: `"HOME"`, `"WORK"`, `"HEADQUARTERS"`, `"BRANCH"`, `"WAREHOUSE"`, `"SHIPPING"`, `"BILLING"`.


Usage Notes:

This is the main endpoint for persisting validated addresses.

Use this endpoint when user finalizes address input.

The backend handles validation via Google Maps before saving.

The returned id must be stored for future revalidation or edits.

Step 5 — Revalidate Existing Address

Endpoint:

POST /api/common/addresses/validation/{addressId}/revalidate


Example:

POST /api/common/addresses/validation/123e4567-e89b-12d3-a456-426614174000/revalidate


**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "tenantId": "uuid...",
    "uid": "ADDR-...",
    "streetAddress": "123 Main Street",
    "city": "London",
    "state": "England",
    "district": "Westminster",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "countryCode": "GB",
    "addressType": "WORK",
    "isPrimary": false,
    "label": "Home Address",
    "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
    "placeId": "ChIJ...",
    "latitude": 52.0406,
    "longitude": -0.7594,
    "isActive": true,
    "createdAt": "2025-01-29T14:30:00Z",
    "updatedAt": "2025-01-29T14:30:00Z"
  },
  "message": "Address revalidated successfully"
}
```


Usage Notes:

Use this endpoint to refresh existing address data from Google.

Recommended during profile or company address update.

4. Integration Rules

To ensure correct backend usage:

Always store placeId
Required for validation and revalidation.

Prefer postcode-based flow
Use autocomplete only when postal data is incomplete.

Include country if available
Improves accuracy and performance.

Validate before saving
Never persist unverified address data.

Handle empty results gracefully
If no results returned, allow manual entry.

Respect backend error codes:

| Error Code | HTTP Status | Meaning | Recommended Frontend Handling |
|------------|-------------|---------|------------------------------|
| `POSTCODE_REQUIRED` | 400 | Missing postcode parameter | Ask user to enter postcode |
| `INPUT_REQUIRED` | 400 | Missing input parameter (autocomplete) | Prompt user to enter address text |
| `VALIDATION_FAILED` | 400 | Address not found or invalid | Notify user, allow manual entry |
| `GOOGLE_API_ERROR` | 500 | Google Maps API failed | Retry or log error, show fallback message |

Do not cache Google data locally
Always fetch fresh data from backend for accuracy.

5. Example Minimal Flow Summary
1. User provides postcode → call /search-by-postcode
2. Backend returns list → user selects one
3. Capture placeId → call /validate-and-create
4. Backend validates and saves → returns ID
5. Store returned ID for later revalidation

## 6. Error Handling Reference

| Error Code | HTTP Status | Meaning | Recommended Frontend Handling |
|------------|-------------|---------|------------------------------|
| `POSTCODE_REQUIRED` | 400 | Missing postcode parameter | Ask user to enter postcode |
| `INPUT_REQUIRED` | 400 | Missing input parameter (autocomplete) | Prompt user to enter address text |
| `VALIDATION_FAILED` | 400 | Address not found or invalid | Notify user, allow manual entry |
| `GOOGLE_API_ERROR` | 500 | Google Maps API failed | Retry or log error, show fallback message |

**Error Response Format:**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Address validation failed: Address not found"
  }
}
```
## 7. Response Field Reference

### AddressValidationResponse Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `verificationStatus` | Enum | Yes | `VERIFIED`, `PARTIAL`, or `FAILED` |
| `placeId` | String | Yes | Google Places ID (required for validation) |
| `formattedAddress` | String | Yes | Google's canonical formatted address |
| `streetAddress` | String | Yes | Street address component |
| `city` | String | Yes | City name |
| `state` | String | No | State/Province/Region |
| `district` | String | No | District/County/Sub-administrative area |
| `postalCode` | String | No | Postal/ZIP code |
| `country` | String | Yes | Country name (e.g., "United Kingdom") |
| `countryCode` | String | No | ISO 3166-1 alpha-2 code (e.g., "GB", "TR") |
| `latitude` | Double | No | Latitude coordinate |
| `longitude` | Double | No | Longitude coordinate |
| `errorMessage` | String | No | Error message (only if `verificationStatus` is `FAILED`) |

### AddressDto Fields (validate-and-create, revalidate)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | UUID | Yes | Address entity ID |
| `tenantId` | UUID | Yes | Tenant ID (multi-tenant isolation) |
| `uid` | String | Yes | Unique identifier (e.g., "ADDR-...") |
| `streetAddress` | String | Yes | Street address |
| `city` | String | Yes | City name |
| `state` | String | No | State/Province |
| `district` | String | No | District/County |
| `postalCode` | String | No | Postal/ZIP code |
| `country` | String | Yes | Country name |
| `countryCode` | String | No | ISO 3166-1 alpha-2 code |
| `addressType` | Enum String | Yes | `"HOME"`, `"WORK"`, `"HEADQUARTERS"`, `"BRANCH"`, `"WAREHOUSE"`, `"SHIPPING"`, `"BILLING"` |
| `isPrimary` | Boolean | Yes | Whether this is the primary address |
| `label` | String | No | Address label (e.g., "Home Address") |
| `formattedAddress` | String | Yes | Google's formatted address |
| `placeId` | String | Yes | Google Places ID |
| `latitude` | Double | No | Latitude coordinate |
| `longitude` | Double | No | Longitude coordinate |
| `isActive` | Boolean | Yes | Whether address is active |
| `createdAt` | ISO 8601 | Yes | Creation timestamp |
| `updatedAt` | ISO 8601 | Yes | Last update timestamp |

### AutocompleteResponse Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `predictions` | Array | Yes | List of address predictions |

**AutocompletePrediction Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `placeId` | String | Yes | Google Places ID (required for validation) |
| `description` | String | Yes | Full formatted address text |
| `mainText` | String | Yes | Primary address component (first part) |
| `secondaryText` | String | Yes | Secondary address component (city, postal code, etc.) |

## 8. Backend Behavior Summary

- ✅ Backend communicates with Google Places and Geocoding APIs directly
- ✅ Validation ensures standardized address structure and verified geolocation
- ✅ No address is stored unless it passes Google verification (`VERIFIED` or `PARTIAL` status)
- ✅ API key and region bias are configured server-side
- ✅ All addresses are tenant-isolated (multi-tenant architecture)
- ✅ Address entities include full geolocation data (latitude/longitude)

**Status:** ✅ Production-Ready

**Frontend Responsibility:**  
Ensure all address-related requests follow this documented flow and pass required parameters to fully utilize backend validation logic.


---

Bu sürüm, frontend ekibinin:
- **her endpoint’in ne zaman çağrılacağını**,  
- **hangi parametreleri kullanması gerektiğini**,  
- **hangi veriyle devam etmesi gerektiğini**,  
net biçimde anlamasını sağlar.  

UI katmanına hiç girmez, sadece **backend etkileşimini %100 doğru, eksiksiz ve verimli** anlatır.
