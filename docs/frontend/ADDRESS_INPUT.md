# Address Input API Reference

**Last Updated:** 2025-01-XX  
**Status:** Production-Ready  
**Backend Endpoints:** `/api/common/addresses/validation/*`

---

## Overview

This document describes the backend API for address input using Google Maps Platform. The backend provides real-time address suggestions, validation, and normalization through Google Maps APIs.

**Key Points:**

- All address data comes from **Google Maps Platform** (not cached data)
- Supports **global** postcode search (not limited to any specific country)
- Country selection is **optional** (improves accuracy and speed when provided)
- Uses Google Places Autocomplete API for suggestions
- Uses Google Geocoding API for postcode search and validation

---

## Endpoints

### 1. Postcode Search (Recommended Flow)

**Endpoint:** `GET /api/common/addresses/validation/search-by-postcode`

**Purpose:** Search addresses by postcode globally or within a specific country.

**Parameters:**

- `postcode` (required): Postal/ZIP code (e.g., "MK5 7GE", "34200", "34000")
- `country` (optional): Country code (ISO 3166-1 alpha-2, e.g., "GB", "TR") or country name (e.g., "United Kingdom", "Turkey")

**Request Examples:**

```http
# Global search (no country filter)
GET /api/common/addresses/validation/search-by-postcode?postcode=MK5%207GE

# Country-specific search (ISO code)
GET /api/common/addresses/validation/search-by-postcode?postcode=34000&country=TR

# Country-specific search (country name)
GET /api/common/addresses/validation/search-by-postcode?postcode=MK5%207GE&country=United%20Kingdom
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "placeId": "ChIJ...",
      "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
      "streetAddress": "123 Main Street",
      "city": "London",
      "state": "England",
      "district": "Milton Keynes",
      "postalCode": "MK5 7GE",
      "country": "United Kingdom",
      "countryCode": "GB",
      "latitude": 52.0406,
      "longitude": -0.7594,
      "verificationStatus": "VERIFIED"
    }
  ]
}
```

**Backend Behavior:**

- If `country` is provided (ISO code): Uses `components=country:XX` and `region=xx` parameters for better accuracy
- If `country` is provided (country name): Uses country name in query string
- If `country` is omitted: Searches globally across all countries

**Country Selection Benefits:**

- Faster results (filters to specific country)
- More accurate (avoids duplicate postcodes across countries)
- Better performance (fewer results to process)

---

### 2. Address Autocomplete

**Endpoint:** `GET /api/common/addresses/validation/autocomplete`

**Purpose:** Get address suggestions as user types (real-time autocomplete).

**Parameters:**

- `input` (required): Address input text (user's typing)
- `country` (optional): Country code (ISO 3166-1 alpha-2)

**Request Example:**

```http
GET /api/common/addresses/validation/autocomplete?input=Istanbul%20Taksim&country=TR
```

**Response:**

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

**Note:** This endpoint uses Google Places Autocomplete API (New) and provides real-time suggestions.

---

### 3. Validate Address

**Endpoint:** `POST /api/common/addresses/validation/validate`

**Purpose:** Validate an address without persisting it to the database.

**Request Body:**

```json
{
  "placeId": "ChIJ...",
  "addressType": "HOME"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "placeId": "ChIJ...",
    "formattedAddress": "123 Main Street, London, MK5 7GE, United Kingdom",
    "streetAddress": "123 Main Street",
    "city": "London",
    "state": "England",
    "district": "Milton Keynes",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "countryCode": "GB",
    "latitude": 52.0406,
    "longitude": -0.7594,
    "verificationStatus": "VERIFIED"
  }
}
```

---

### 4. Validate and Create Address

**Endpoint:** `POST /api/common/addresses/validation/validate-and-create`

**Purpose:** Validate an address and persist it to the database in a single operation.

**Request Body:**

```json
{
  "placeId": "ChIJ...",
  "addressType": "HOME",
  "label": "Home Address"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "uid": "TENANT-UID-ADD-00001",
    "streetAddress": "123 Main Street",
    "city": "London",
    "state": "England",
    "district": "Milton Keynes",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "countryCode": "GB",
    "placeId": "ChIJ...",
    "latitude": 52.0406,
    "longitude": -0.7594,
    "addressType": "HOME",
    "label": "Home Address"
  },
  "message": "Address validated and created successfully"
}
```

---

### 5. Revalidate Address

**Endpoint:** `POST /api/common/addresses/validation/{addressId}/revalidate`

**Purpose:** Revalidate an existing address by its ID and update with latest data from Google Maps.

**Request Example:**

```http
POST /api/common/addresses/validation/123e4567-e89b-12d3-a456-426614174000/revalidate
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid...",
    "streetAddress": "123 Main Street",
    "city": "London",
    "postalCode": "MK5 7GE",
    "country": "United Kingdom",
    "placeId": "ChIJ...",
    "latitude": 52.0406,
    "longitude": -0.7594
  },
  "message": "Address revalidated successfully"
}
```

---

## Recommended Flow: Postcode-First Approach

**Step 1: User enters postcode (and optionally selects country)**

- Frontend calls: `GET /api/common/addresses/validation/search-by-postcode?postcode={postcode}&country={country}`
- Backend queries Google Geocoding API with postcode and optional country filter
- Returns list of addresses matching the postcode

**Step 2: User selects an address from the list**

- Frontend stores the selected `AddressValidationResponse` object (includes `placeId`, `streetAddress`, `city`, `state`, `postalCode`, `country`, `latitude`, `longitude`)

**Step 3: Frontend auto-fills form fields**

- Frontend uses the selected address data to populate form fields
- User can edit fields if needed

**Step 4: Submit address**

- Frontend calls: `POST /api/common/addresses/validation/validate-and-create`
- Request body includes `placeId` from selected address
- Backend validates and creates address entity

---

## Address Data Structure

### AddressValidationResponse

```typescript
interface AddressValidationResponse {
  placeId: string; // Google Places ID (required for validation)
  formattedAddress: string; // Google's canonical format
  streetAddress: string; // Street address
  city: string; // City
  state: string; // State/Province
  district: string; // District/County
  postalCode: string; // Postal/ZIP code
  country: string; // Country name
  countryCode: string; // ISO 3166-1 alpha-2 code
  latitude: number; // Latitude coordinate
  longitude: number; // Longitude coordinate
  verificationStatus: "VERIFIED" | "PARTIAL" | "FAILED";
  errorMessage?: string; // Error message (if failed)
}
```

### AddressData (for User Creation)

When creating a user with addresses, use this structure:

```typescript
interface AddressData {
  streetAddress: string; // Required
  city: string; // Required
  state?: string; // Optional
  postalCode?: string; // Optional
  country: string; // Required
  placeId?: string; // Optional (recommended for validation)
  addressType?: string; // "WORK" | "HOME" (default: "WORK")
  label?: string; // Optional (e.g., "Head Office", "Home Address")
  isPrimary?: boolean; // Optional (default: false, first address becomes primary)
}
```

---

## Google Maps Platform Integration

**Important:** The backend uses **Google Maps Platform APIs** to provide address data. All address suggestions, validation, and normalization come from Google's servers, not from cached or local data.

**APIs Used:**

1. **Google Places Autocomplete API (New)** - For real-time address suggestions
2. **Google Geocoding API** - For postcode search and address validation

**Configuration:**

- API key configured via `GOOGLE_MAPS_API_KEY` environment variable
- Region bias configured via `GOOGLE_MAPS_REGION_BIAS` (comma-separated country codes)
- Timeout configured via `GOOGLE_MAPS_TIMEOUT` (default: 10000ms)

---

## Error Handling

**Common Error Responses:**

```json
{
  "success": false,
  "errorCode": "POSTCODE_REQUIRED",
  "message": "Postcode parameter is required"
}
```

```json
{
  "success": false,
  "errorCode": "VALIDATION_FAILED",
  "message": "Address validation failed: ZERO_RESULTS"
}
```

**Empty Results:**

- If no addresses found, backend returns empty array: `{"success": true, "data": []}`
- Frontend should handle empty results gracefully (show message, allow manual entry)

---

## Best Practices

1. **Use Postcode Search First:** Most efficient for users who know their postcode
2. **Store placeId:** Always store `placeId` from selected address for future validation
3. **Allow Country Selection:** Optional country selection improves accuracy and speed
4. **Handle Empty Results:** Provide fallback to manual address entry if postcode search returns no results
5. **Validate Before Submit:** Use `/validate` endpoint before submitting to ensure address is valid
6. **Debounce Input:** Debounce postcode input (e.g., 500ms) to reduce API calls
7. **Minimum Length:** Require minimum postcode length (e.g., 4 characters) before searching

---

## Related Documentation

- `docs/frontend/CREATE_USER.md` - User creation with addresses
- `docs/phone-email-validation.md` - Phone and email validation

---

**Status:** ✅ Production-Ready  
**Last Updated:** 2025-01-XX
