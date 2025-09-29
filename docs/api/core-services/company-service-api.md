# Company Service API Documentation

## 📋 Overview

Company Service API, fabric management sisteminde company management, company settings ve company-related operations için RESTful API endpoints sağlar.

## 🔐 Authentication

Tüm API endpoints JWT token authentication gerektirir:

```http
Authorization: Bearer <jwt_token>
X-Tenant-ID: <tenant_id>
```

## 📊 API Endpoints

### **Company Management**

#### **Create Company**

```http
POST /api/v1/companies
Content-Type: application/json
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Request Body:**

```json
{
  "companyName": "Fabric Corp",
  "legalName": "Fabric Corporation Ltd.",
  "taxNumber": "1234567890",
  "registrationNumber": "REG-2024-001",
  "companyType": "CORPORATION",
  "industry": "Textile Manufacturing",
  "website": "https://fabriccorp.com",
  "description": "Leading textile manufacturer",
  "currency": "USD",
  "timezone": "UTC",
  "language": "en",
  "dateFormat": "MM/dd/yyyy",
  "timeFormat": "12h",
  "fiscalYearStart": "2024-01-01",
  "businessHours": {
    "monday": { "start": "09:00", "end": "17:00" },
    "tuesday": { "start": "09:00", "end": "17:00" },
    "wednesday": { "start": "09:00", "end": "17:00" },
    "thursday": { "start": "09:00", "end": "17:00" },
    "friday": { "start": "09:00", "end": "17:00" }
  },
  "primaryLocation": {
    "locationName": "Headquarters",
    "addressType": "HEADQUARTERS",
    "street": "123 Business St",
    "city": "New York",
    "state": "NY",
    "postalCode": "10001",
    "country": "USA",
    "phone": "+1-555-0123",
    "email": "info@fabriccorp.com",
    "isPrimary": true
  }
}
```

**Response:**

```json
{
  "success": true,
  "message": "Company created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "550e8400-e29b-41d4-a716-446655440001",
    "companyName": "Fabric Corp",
    "legalName": "Fabric Corporation Ltd.",
    "taxNumber": "1234567890",
    "registrationNumber": "REG-2024-001",
    "companyType": "CORPORATION",
    "industry": "Textile Manufacturing",
    "website": "https://fabriccorp.com",
    "description": "Leading textile manufacturer",
    "status": "ACTIVE",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z",
    "createdBy": "user-123",
    "updatedBy": "user-123"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### **Get Company**

```http
GET /api/v1/companies/{companyId}
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "550e8400-e29b-41d4-a716-446655440001",
    "companyName": "Fabric Corp",
    "legalName": "Fabric Corporation Ltd.",
    "taxNumber": "1234567890",
    "registrationNumber": "REG-2024-001",
    "companyType": "CORPORATION",
    "industry": "Textile Manufacturing",
    "website": "https://fabriccorp.com",
    "description": "Leading textile manufacturer",
    "status": "ACTIVE",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z",
    "createdBy": "user-123",
    "updatedBy": "user-123"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### **Update Company**

```http
PUT /api/v1/companies/{companyId}
Content-Type: application/json
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Request Body:**

```json
{
  "companyName": "Fabric Corp Updated",
  "website": "https://fabriccorp-updated.com",
  "description": "Updated description"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Company updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "companyName": "Fabric Corp Updated",
    "website": "https://fabriccorp-updated.com",
    "description": "Updated description",
    "updatedAt": "2024-01-15T11:00:00Z",
    "updatedBy": "user-123"
  },
  "timestamp": "2024-01-15T11:00:00Z"
}
```

#### **Delete Company**

```http
DELETE /api/v1/companies/{companyId}
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Response:**

```json
{
  "success": true,
  "message": "Company deleted successfully",
  "timestamp": "2024-01-15T11:30:00Z"
}
```

#### **Search Companies**

```http
GET /api/v1/companies/search?query=fabric&page=0&size=20
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "companyName": "Fabric Corp",
      "legalName": "Fabric Corporation Ltd.",
      "industry": "Textile Manufacturing",
      "status": "ACTIVE"
    }
  ],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### **Company Locations**

#### **Add Company Location**

```http
POST /api/v1/companies/{companyId}/locations
Content-Type: application/json
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Request Body:**

```json
{
  "locationName": "Branch Office",
  "addressType": "BRANCH",
  "street": "456 Branch Ave",
  "city": "Los Angeles",
  "state": "CA",
  "postalCode": "90210",
  "country": "USA",
  "phone": "+1-555-0456",
  "email": "branch@fabriccorp.com",
  "isPrimary": false
}
```

**Response:**

```json
{
  "success": true,
  "message": "Location added successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "companyId": "550e8400-e29b-41d4-a716-446655440000",
    "locationName": "Branch Office",
    "addressType": "BRANCH",
    "street": "456 Branch Ave",
    "city": "Los Angeles",
    "state": "CA",
    "postalCode": "90210",
    "country": "USA",
    "phone": "+1-555-0456",
    "email": "branch@fabriccorp.com",
    "isPrimary": false,
    "isActive": true,
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### **Get Company Locations**

```http
GET /api/v1/companies/{companyId}/locations
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "locationName": "Headquarters",
      "addressType": "HEADQUARTERS",
      "street": "123 Business St",
      "city": "New York",
      "state": "NY",
      "postalCode": "10001",
      "country": "USA",
      "isPrimary": true,
      "isActive": true
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "locationName": "Branch Office",
      "addressType": "BRANCH",
      "street": "456 Branch Ave",
      "city": "Los Angeles",
      "state": "CA",
      "postalCode": "90210",
      "country": "USA",
      "isPrimary": false,
      "isActive": true
    }
  ],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### **Company Settings**

#### **Get Company Settings**

```http
GET /api/v1/companies/{companyId}/settings
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "companyId": "550e8400-e29b-41d4-a716-446655440000",
    "currency": "USD",
    "timezone": "UTC",
    "language": "en",
    "dateFormat": "MM/dd/yyyy",
    "timeFormat": "12h",
    "fiscalYearStart": "2024-01-01",
    "businessHours": {
      "monday": { "start": "09:00", "end": "17:00" },
      "tuesday": { "start": "09:00", "end": "17:00" },
      "wednesday": { "start": "09:00", "end": "17:00" },
      "thursday": { "start": "09:00", "end": "17:00" },
      "friday": { "start": "09:00", "end": "17:00" }
    },
    "notificationPreferences": {},
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### **Update Company Settings**

```http
PUT /api/v1/companies/{companyId}/settings
Content-Type: application/json
Authorization: Bearer <token>
X-Tenant-ID: <tenant_id>
```

**Request Body:**

```json
{
  "currency": "EUR",
  "timezone": "Europe/Istanbul",
  "language": "tr",
  "dateFormat": "dd/MM/yyyy",
  "timeFormat": "24h",
  "businessHours": {
    "monday": { "start": "08:00", "end": "18:00" },
    "tuesday": { "start": "08:00", "end": "18:00" },
    "wednesday": { "start": "08:00", "end": "18:00" },
    "thursday": { "start": "08:00", "end": "18:00" },
    "friday": { "start": "08:00", "end": "18:00" }
  },
  "notificationPreferences": {
    "email": true,
    "sms": false,
    "push": true
  }
}
```

**Response:**

```json
{
  "success": true,
  "message": "Settings updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "companyId": "550e8400-e29b-41d4-a716-446655440000",
    "currency": "EUR",
    "timezone": "Europe/Istanbul",
    "language": "tr",
    "dateFormat": "dd/MM/yyyy",
    "timeFormat": "24h",
    "businessHours": {
      "monday": { "start": "08:00", "end": "18:00" },
      "tuesday": { "start": "08:00", "end": "18:00" },
      "wednesday": { "start": "08:00", "end": "18:00" },
      "thursday": { "start": "08:00", "end": "18:00" },
      "friday": { "start": "08:00", "end": "18:00" }
    },
    "notificationPreferences": {
      "email": true,
      "sms": false,
      "push": true
    },
    "updatedAt": "2024-01-15T11:00:00Z"
  },
  "timestamp": "2024-01-15T11:00:00Z"
}
```

## 📊 Data Models

### **Company**

```json
{
  "id": "UUID",
  "tenantId": "UUID",
  "companyName": "string",
  "legalName": "string",
  "taxNumber": "string",
  "registrationNumber": "string",
  "companyType": "CORPORATION | LLC | PARTNERSHIP | SOLE_PROPRIETORSHIP",
  "industry": "string",
  "website": "string",
  "description": "string",
  "logoUrl": "string",
  "status": "ACTIVE | INACTIVE | SUSPENDED",
  "createdAt": "datetime",
  "updatedAt": "datetime",
  "createdBy": "string",
  "updatedBy": "string"
}
```

### **CompanyLocation**

```json
{
  "id": "UUID",
  "companyId": "UUID",
  "locationName": "string",
  "addressType": "HEADQUARTERS | BRANCH | WAREHOUSE | OFFICE",
  "street": "string",
  "city": "string",
  "state": "string",
  "postalCode": "string",
  "country": "string",
  "phone": "string",
  "email": "string",
  "isPrimary": "boolean",
  "isActive": "boolean",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### **CompanySettings**

```json
{
  "id": "UUID",
  "companyId": "UUID",
  "currency": "string",
  "timezone": "string",
  "language": "string",
  "dateFormat": "string",
  "timeFormat": "string",
  "fiscalYearStart": "date",
  "businessHours": "object",
  "notificationPreferences": "object",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

## ⚠️ Error Handling

### **Error Response Format**

```json
{
  "success": false,
  "message": "Error message",
  "errorCode": "ERROR_CODE",
  "errors": ["Detailed error messages"],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### **Common Error Codes**

| Error Code                | HTTP Status | Description             |
| ------------------------- | ----------- | ----------------------- |
| `ENTITY_NOT_FOUND`        | 404         | Company not found       |
| `BUSINESS_RULE_VIOLATION` | 409         | Business rule violation |
| `VALIDATION_ERROR`        | 400         | Validation failed       |
| `AUTHENTICATION_ERROR`    | 401         | Authentication failed   |
| `AUTHORIZATION_ERROR`     | 403         | Authorization failed    |

### **Error Examples**

#### **Company Not Found**

```json
{
  "success": false,
  "message": "Company not found",
  "errorCode": "ENTITY_NOT_FOUND",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### **Validation Error**

```json
{
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "errors": ["Company name is required", "Tax number must be 10 digits"],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 🚀 Rate Limiting

- **Rate Limit**: 100 requests per minute per user
- **Headers**:
  - `X-RateLimit-Limit`: Request limit
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time

## 📈 Performance Considerations

- **Response Time**: < 200ms for simple operations
- **Pagination**: Default 20 items per page, max 100
- **Caching**: Company data cached for 5 minutes
- **Search**: Full-text search with PostgreSQL

## 🔧 Testing

### **Postman Collection**

[Download Company Service Postman Collection](./postman/company-service-collection.json)

### **Test Environment**

- **Base URL**: `https://api-dev.fabricmanagement.com`
- **Test Tenant**: `test-tenant-123`
- **Test User**: `test-user@fabricmanagement.com`

---

**Last Updated**: 2024-01-15  
**Version**: 1.0.0  
**Maintainer**: API Team
