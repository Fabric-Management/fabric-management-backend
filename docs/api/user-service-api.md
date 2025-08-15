# User Service API Documentation

## Overview

The User Service API provides comprehensive user management capabilities for the Fabric Management System. This RESTful API follows OpenAPI 3.0 specifications and implements standard HTTP methods with JSON payloads.

**Version:** 1.0.0  
**Base URL:** `http://localhost:8081/api/v1`  
**Protocol:** HTTP/HTTPS  
**Content Type:** `application/json`

## Table of Contents

- [Authentication](#authentication)
- [Common Headers](#common-headers)
- [Endpoints](#endpoints)
    - [Create User](#create-user)
    - [Get User by ID](#get-user-by-id)
    - [Get All Users](#get-all-users)
    - [Update User](#update-user)
    - [Partial Update User](#partial-update-user)
    - [Delete User](#delete-user)
    - [Search Users](#search-users)
    - [Get User by Username](#get-user-by-username)
    - [Activate User](#activate-user)
    - [Deactivate User](#deactivate-user)
    - [Bulk Operations](#bulk-operations)
- [Error Responses](#error-responses)
- [Status Codes](#status-codes)
- [Rate Limiting](#rate-limiting)
- [Pagination](#pagination)
- [Filtering & Sorting](#filtering--sorting)
- [API Versioning](#api-versioning)
- [Examples](#examples)

## Authentication

All endpoints require tenant identification via the `X-Tenant-ID` header. Future versions will include JWT-based authentication.

### Required Headers

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-Tenant-ID` | UUID | Yes | Unique identifier for the tenant |
| `Authorization` | String | No* | Bearer token (planned for future) |
| `X-Request-ID` | UUID | No | Unique request identifier for tracing |

*Will be required once authentication service is implemented

## Common Headers

### Request Headers

```http
X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000
Content-Type: application/json
Accept: application/json
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000
Accept-Language: en-US
```

### Response Headers

```http
Content-Type: application/json
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000
X-Rate-Limit-Limit: 1000
X-Rate-Limit-Remaining: 999
X-Rate-Limit-Reset: 1642166400
```

## Endpoints

### Create User

Creates a new user in the system.

**Endpoint:** `POST /users`

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body:**

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "username": "johndoe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "dateOfBirth": "1990-01-15",
  "metadata": {
    "department": "Engineering",
    "employeeId": "EMP001"
  }
}
```

**Validation Rules:**
- `firstName`: Required, 2-100 characters, alphabetic
- `lastName`: Required, 2-100 characters, alphabetic
- `username`: Required, 3-50 characters, alphanumeric and underscore
- `email`: Optional, valid email format
- `phoneNumber`: Optional, E.164 format
- `dateOfBirth`: Optional, ISO 8601 date format
- `metadata`: Optional, key-value pairs

**Response (201 Created):**

```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": "a481df11-29dc-4dc6-af9d-467501405458",
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890",
    "dateOfBirth": "1990-01-15",
    "fullName": "John Doe",
    "status": "PENDING",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "metadata": {
      "department": "Engineering",
      "employeeId": "EMP001"
    },
    "createdAt": "2024-01-14T15:08:45.154469Z",
    "updatedAt": "2024-01-14T15:08:45.154469Z",
    "createdBy": "system",
    "updatedBy": "system",
    "version": 1
  },
  "timestamp": "2024-01-14T15:08:45.197689Z",
  "path": "/api/v1/users",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses:**
- `400 Bad Request` - Validation error
- `409 Conflict` - Username already exists

---

### Get User by ID

Retrieves a specific user by their unique identifier.

**Endpoint:** `GET /users/{userId}`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "a481df11-29dc-4dc6-af9d-467501405458",
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890",
    "dateOfBirth": "1990-01-15",
    "fullName": "John Doe",
    "status": "ACTIVE",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "metadata": {
      "department": "Engineering",
      "employeeId": "EMP001",
      "lastLogin": "2024-01-14T14:00:00Z"
    },
    "createdAt": "2024-01-14T15:08:45.154469Z",
    "updatedAt": "2024-01-14T15:15:30.123456Z",
    "createdBy": "system",
    "updatedBy": "admin",
    "version": 2
  },
  "timestamp": "2024-01-14T15:15:59.636114Z",
  "path": "/api/v1/users/a481df11-29dc-4dc6-af9d-467501405458",
  "traceId": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Error Responses:**
- `404 Not Found` - User not found
- `403 Forbidden` - User belongs to different tenant

---

### Get All Users

Retrieves a paginated list of all users for the tenant.

**Endpoint:** `GET /users`

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | 0 | Page number (0-indexed) |
| `size` | Integer | 20 | Page size (max: 100) |
| `sort` | String | "createdAt,desc" | Sort criteria |
| `status` | String | - | Filter by status (ACTIVE, INACTIVE, PENDING) |
| `search` | String | - | Search in firstName, lastName, username |

**Example Request:**
```http
GET /users?page=0&size=10&sort=username,asc&status=ACTIVE&search=john
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "id": "a481df11-29dc-4dc6-af9d-467501405458",
        "firstName": "John",
        "lastName": "Doe",
        "username": "johndoe",
        "email": "john.doe@example.com",
        "status": "ACTIVE",
        "fullName": "John Doe",
        "createdAt": "2024-01-14T15:08:45.154469Z"
      }
    ],
    "pageable": {
      "sort": {
        "sorted": true,
        "ascending": true,
        "descending": false
      },
      "pageNumber": 0,
      "pageSize": 10,
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 150,
    "totalPages": 15,
    "last": false,
    "first": true,
    "numberOfElements": 10,
    "size": 10,
    "number": 0,
    "sort": {
      "sorted": true,
      "ascending": true,
      "descending": false
    },
    "empty": false
  },
  "timestamp": "2024-01-14T15:20:00.123456Z",
  "path": "/api/v1/users",
  "traceId": "770e8400-e29b-41d4-a716-446655440002"
}
```

---

### Update User

Fully updates an existing user (requires all fields).

**Endpoint:** `PUT /users/{userId}`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body:**

```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "username": "janesmith",
  "email": "jane.smith@example.com",
  "phoneNumber": "+9876543210",
  "dateOfBirth": "1992-05-20",
  "metadata": {
    "department": "Marketing",
    "employeeId": "EMP002"
  }
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User updated successfully",
  "data": {
    "id": "a481df11-29dc-4dc6-af9d-467501405458",
    "firstName": "Jane",
    "lastName": "Smith",
    "username": "janesmith",
    "email": "jane.smith@example.com",
    "phoneNumber": "+9876543210",
    "dateOfBirth": "1992-05-20",
    "fullName": "Jane Smith",
    "status": "ACTIVE",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "metadata": {
      "department": "Marketing",
      "employeeId": "EMP002"
    },
    "createdAt": "2024-01-14T15:08:45.154469Z",
    "updatedAt": "2024-01-14T15:16:14.423790Z",
    "createdBy": "system",
    "updatedBy": "admin",
    "version": 3
  },
  "timestamp": "2024-01-14T15:16:14.423790Z",
  "path": "/api/v1/users/a481df11-29dc-4dc6-af9d-467501405458",
  "traceId": "880e8400-e29b-41d4-a716-446655440003"
}
```

**Error Responses:**
- `400 Bad Request` - Validation error
- `404 Not Found` - User not found
- `409 Conflict` - Username already exists

---

### Partial Update User

Partially updates specific fields of an existing user.

**Endpoint:** `PATCH /users/{userId}`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body (only include fields to update):**

```json
{
  "email": "newemail@example.com",
  "metadata": {
    "lastLogin": "2024-01-14T16:00:00Z"
  }
}
```

**Response (200 OK):**
Similar to Update User response

---

### Delete User

Soft deletes a user from the system.

**Endpoint:** `DELETE /users/{userId}`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Response (204 No Content):**
No response body

**Error Responses:**
- `404 Not Found` - User not found
- `409 Conflict` - User has related data that prevents deletion

---

### Search Users

Advanced search with multiple criteria.

**Endpoint:** `POST /users/search`

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body:**

```json
{
  "criteria": {
    "firstName": "John",
    "lastName": "Doe",
    "status": ["ACTIVE", "PENDING"],
    "createdAfter": "2024-01-01T00:00:00Z",
    "createdBefore": "2024-12-31T23:59:59Z",
    "metadata": {
      "department": "Engineering"
    }
  },
  "page": 0,
  "size": 20,
  "sort": ["lastName,asc", "firstName,asc"]
}
```

**Response (200 OK):**
Similar to Get All Users response

---

### Get User by Username

Retrieves a user by their username.

**Endpoint:** `GET /users/username/{username}`

**Path Parameters:**
- `username` (String, required): Username of the user

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Response (200 OK):**
Similar to Get User by ID response

---

### Activate User

Activates a pending or inactive user.

**Endpoint:** `POST /users/{userId}/activate`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User activated successfully",
  "data": {
    "id": "a481df11-29dc-4dc6-af9d-467501405458",
    "status": "ACTIVE",
    "activatedAt": "2024-01-14T15:30:00.123456Z",
    "activatedBy": "admin"
  },
  "timestamp": "2024-01-14T15:30:00.123456Z"
}
```

---

### Deactivate User

Deactivates an active user.

**Endpoint:** `POST /users/{userId}/deactivate`

**Path Parameters:**
- `userId` (UUID, required): Unique identifier of the user

**Headers:**
- `X-Tenant-ID: {tenantId}` (required)

**Request Body (optional):**

```json
{
  "reason": "Account suspension",
  "deactivateUntil": "2024-02-14T00:00:00Z"
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User deactivated successfully",
  "data": {
    "id": "a481df11-29dc-4dc6-af9d-467501405458",
    "status": "INACTIVE",
    "deactivatedAt": "2024-01-14T15:35:00.123456Z",
    "deactivatedBy": "admin",
    "deactivationReason": "Account suspension",
    "deactivateUntil": "2024-02-14T00:00:00Z"
  },
  "timestamp": "2024-01-14T15:35:00.123456Z"
}
```

---

### Bulk Operations

#### Bulk Create Users

**Endpoint:** `POST /users/bulk`

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body:**

```json
{
  "users": [
    {
      "firstName": "Alice",
      "lastName": "Johnson",
      "username": "alicej"
    },
    {
      "firstName": "Bob",
      "lastName": "Williams",
      "username": "bobw"
    }
  ],
  "skipOnError": false
}
```

**Response (207 Multi-Status):**

```json
{
  "success": true,
  "message": "Bulk operation completed",
  "data": {
    "successful": [
      {
        "index": 0,
        "user": {
          "id": "a481df11-29dc-4dc6-af9d-467501405458",
          "firstName": "Alice",
          "lastName": "Johnson",
          "username": "alicej",
          "status": "ACTIVE",
          "createdAt": "2024-01-14T15:40:00.123456Z"
        }
      }
    ],
    "failed": [
      {
        "index": 1,
        "error": {
          "code": "USER_002",
          "message": "Username already exists"
        }
      }
    ],
    "totalProcessed": 2,
    "successCount": 1,
    "failureCount": 1
  },
  "timestamp": "2024-01-14T15:40:00.123456Z"
}
```

#### Bulk Delete Users

**Endpoint:** `DELETE /users/bulk`

**Headers:**
- `Content-Type: application/json` (required)
- `X-Tenant-ID: {tenantId}` (required)

**Request Body:**

```json
{
  "userIds": [
    "a481df11-29dc-4dc6-af9d-467501405458",
    "b582ef22-39ed-5ed7-bg0e-578612506569"
  ]
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Bulk delete completed",
  "data": {
    "deletedCount": 2,
    "deletedIds": [
      "a481df11-29dc-4dc6-af9d-467501405458",
      "b582ef22-39ed-5ed7-bg0e-578612506569"
    ]
  },
  "timestamp": "2024-01-14T15:45:00.123456Z"
}
```

## Error Responses

### Standard Error Format

All error responses follow this structure:

```json
{
  "success": false,
  "message": "Human-readable error message",
  "error": {
    "code": "ERROR_CODE",
    "message": "Detailed error message",
    "details": {
      "field": "Additional context"
    },
    "stackTrace": "Optional stack trace (dev environment only)"
  },
  "timestamp": "2024-01-14T15:16:14.423790Z",
  "path": "/api/v1/users/123",
  "traceId": "990e8400-e29b-41d4-a716-446655440004"
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `USER_001` | 404 | User not found |
| `USER_002` | 409 | Username already exists |
| `USER_003` | 409 | Email already exists |
| `USER_004` | 400 | Invalid user status |
| `USER_005` | 403 | User belongs to different tenant |
| `VALIDATION_ERROR` | 400 | Input validation failed |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Internal server error |

### Validation Error Example

```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input",
    "details": {
      "firstName": "First name is required",
      "email": "Invalid email format",
      "phoneNumber": "Phone number must be in E.164 format"
    }
  },
  "timestamp": "2024-01-14T15:16:14.423790Z"
}
```

## Status Codes

| Status Code | Description | Usage |
|-------------|-------------|-------|
| `200 OK` | Success | GET, PUT, PATCH requests |
| `201 Created` | Resource created | POST requests |
| `204 No Content` | Success, no content | DELETE requests |
| `207 Multi-Status` | Partial success | Bulk operations |
| `400 Bad Request` | Invalid input | Validation errors |
| `401 Unauthorized` | Authentication required | Missing/invalid auth |
| `403 Forbidden` | Access denied | Insufficient permissions |
| `404 Not Found` | Resource not found | Non-existent resource |
| `409 Conflict` | Resource conflict | Duplicate resources |
| `422 Unprocessable Entity` | Business rule violation | Domain errors |
| `429 Too Many Requests` | Rate limit exceeded | Rate limiting |
| `500 Internal Server Error` | Server error | Unexpected errors |
| `502 Bad Gateway` | Gateway error | Upstream service error |
| `503 Service Unavailable` | Service down | Maintenance/overload |

## Rate Limiting

API implements rate limiting to ensure fair usage:

- **Default limit:** 1000 requests per hour per tenant
- **Burst limit:** 100 requests per minute
- **Headers returned:**
    - `X-Rate-Limit-Limit`: Maximum requests allowed
    - `X-Rate-Limit-Remaining`: Remaining requests
    - `X-Rate-Limit-Reset`: Unix timestamp when limit resets

**Rate Limit Exceeded Response (429):**

```json
{
  "success": false,
  "message": "Rate limit exceeded",
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests",
    "details": {
      "limit": 1000,
      "remaining": 0,
      "resetAt": "2024-01-14T16:00:00Z"
    }
  },
  "timestamp": "2024-01-14T15:50:00.123456Z"
}
```

## Pagination

All list endpoints support pagination:

### Request Parameters

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| `page` | Integer | 0 | - | Page number (0-indexed) |
| `size` | Integer | 20 | 100 | Items per page |
| `sort` | String | "createdAt,desc" | - | Sort criteria |

### Pagination Response

```json
{
  "content": [],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "offset": 0,
    "sort": {
      "sorted": true,
      "ascending": false
    }
  },
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false,
  "numberOfElements": 20
}
```

## Filtering & Sorting

### Filtering

Use query parameters for filtering:

```http
GET /users?status=ACTIVE&createdAfter=2024-01-01&department=Engineering
```

### Sorting

Multiple sort criteria supported:

```http
GET /users?sort=lastName,asc&sort=firstName,asc
```

### Search

Full-text search across multiple fields:

```http
GET /users?search=john
```

## API Versioning

API versioning is handled through URL path:

- Current version: `/api/v1`
- Previous versions will be maintained for backward compatibility
- Deprecation notices provided via `Sunset` header
- Version migration guide available in documentation

### Version Header

```http
API-Version: 1.0.0
Sunset: Sat, 31 Dec 2024 23:59:59 GMT
Deprecation: true
Link: <https://api.docs/migration>; rel="deprecation"
```

## Examples

### cURL Examples

#### Create User
```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe",
    "email": "john@example.com"
  }'
```

#### Get User with Authentication (Future)
```bash
curl -X GET http://localhost:8081/api/v1/users/a481df11-29dc-4dc6-af9d-467501405458 \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR..."
```

#### Search Users with Pagination
```bash
curl -X GET "http://localhost:8081/api/v1/users?page=0&size=10&status=ACTIVE&search=john" \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000"
```

### JavaScript/Axios Example

```javascript
const axios = require('axios');

const API_BASE_URL = 'http://localhost:8081/api/v1';
const TENANT_ID = '123e4567-e89b-12d3-a456-426614174000';

// Create axios instance with default headers
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'X-Tenant-ID': TENANT_ID
  }
});

// Create user
async function createUser(userData) {
  try {
    const response = await api.post('/users', userData);
    return response.data;
  } catch (error) {
    console.error('Error creating user:', error.response.data);
    throw error;
  }
}

// Get user
async function getUser(userId) {
  try {
    const response = await api.get(`/users/${userId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching user:', error.response.data);
    throw error;
  }
}

// Usage
const newUser = {
  firstName: 'John',
  lastName: 'Doe',
  username: 'johndoe',
  email: 'john@example.com'
};

createUser(newUser)
  .then(result => console.log('User created:', result))
  .catch(error => console.error('Failed to create user:', error));
```

### Python Example

```python
import requests
import json

API_BASE_URL = "http://localhost:8081/api/v1"
TENANT_ID = "123e4567-e89b-12d3-a456-426614174000"

headers = {
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT_ID
}

def create_user(user_data):
    """Create a new user"""
    response = requests.post(
        f"{API_BASE_URL}/users",
        headers=headers,
        json=user_data
    )
    response.raise_for_status()
    return response.json()

def get_user(user_id):
    """Get user by ID"""
    response = requests.get(
        f"{API_BASE_URL}/users/{user_id}",
        headers=headers
    )
    response.raise_for_status()
    return response.json()

# Usage
new_user = {
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe",
    "email": "john@example.com"
}

try:
    result = create_user(new_user)
    print(f"User created: {result['data']}")
except requests.exceptions.HTTPError as e:
    print(f"Error: {e.response.json()}")
```

### Java/Spring RestTemplate Example

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

public class UserServiceClient {
    private static final String API_BASE_URL = "http://localhost:8081/api/v1";
    private static final String TENANT_ID = "123e4567-e89b-12d3-a456-426614174000";

    private final RestTemplate restTemplate = new RestTemplate();

    public UserResponse createUser(CreateUserRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", TENANT_ID);

        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                API_BASE_URL + "/users",
                HttpMethod.POST,
                entity,
                ApiResponse.class
        );

        return response.getBody().getData();
    }
}
```

## Postman Collection

Import this collection to test the API:

```json
{
  "info": {
    "name": "User Service API",
    "version": "1.0.0",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8081/api/v1"
    },
    {
      "key": "tenantId",
      "value": "123e4567-e89b-12d3-a456-426614174000"
    }
  ],
  "item": [
    {
      "name": "Create User",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          },
          {
            "key": "X-Tenant-ID",
            "value": "{{tenantId}}"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"firstName\": \"John\",\n  \"lastName\": \"Doe\",\n  \"username\": \"johndoe\"\n}"
        },
        "url": "{{baseUrl}}/users"
      }
    }
  ]
}
```

## Health Check & Monitoring

### Health Endpoint

**Endpoint:** `GET /actuator/health`

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 395659386880,
        "threshold": 10485760
      }
    }
  }
}
```

### Metrics Endpoint

**Endpoint:** `GET /actuator/metrics`

Available metrics:
- `http.server.requests`
- `jvm.memory.used`
- `system.cpu.usage`
- `application.users.created`
- `application.users.active`

## Support

For API support and questions:

- **Documentation:** [https://docs.fabricmanagement.com](https://docs.fabricmanagement.com)
- **API Status:** [https://status.fabricmanagement.com](https://status.fabricmanagement.com)
- **Support Email:** api-support@fabricmanagement.com
- **Developer Portal:** [https://developers.fabricmanagement.com](https://developers.fabricmanagement.com)

---

**Last Updated:** January 2025  
**API Version:** 1.0.0  
**OpenAPI Spec:** [Download OpenAPI 3.0 Specification](openapi.yaml)