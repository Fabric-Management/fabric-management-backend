# Policy Framework API Reference

This document provides comprehensive API reference for the Policy Framework.

## 🚀 API Overview

The Policy Framework provides RESTful APIs for policy management and evaluation.

### Base URL

```
http://localhost:8080/api/v1/policies
```

### Authentication

All API endpoints require authentication using JWT tokens or internal API keys.

### Response Format

All responses follow the standard API response format:

```json
{
  "success": true,
  "data": {},
  "message": "Operation successful",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 📋 Policy Management APIs

### Create Policy

**POST** `/api/v1/policies`

Creates a new policy.

#### Request Body

```json
{
  "name": "USER_READ_ACCESS",
  "description": "Users can read their own data",
  "type": "ACCESS",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "isActive": true,
  "priority": 50,
  "conditions": {
    "user_id": "{{userId}}",
    "resource_type": "USER"
  },
  "rules": {
    "allow": true
  },
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2024-12-31T23:59:59Z"
}
```

#### Response

```json
{
  "success": true,
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "USER_READ_ACCESS",
    "description": "Users can read their own data",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": true,
    "priority": 50,
    "conditions": {
      "user_id": "{{userId}}",
      "resource_type": "USER"
    },
    "rules": {
      "allow": true
    },
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2024-12-31T23:59:59Z",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  },
  "message": "Policy created successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Get Policy

**GET** `/api/v1/policies/{policyId}`

Retrieves a policy by ID.

#### Path Parameters

- `policyId` (UUID): The policy ID

#### Response

```json
{
  "success": true,
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "USER_READ_ACCESS",
    "description": "Users can read their own data",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": true,
    "priority": 50,
    "conditions": {
      "user_id": "{{userId}}",
      "resource_type": "USER"
    },
    "rules": {
      "allow": true
    },
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2024-12-31T23:59:59Z",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  },
  "message": "Policy retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Update Policy

**PUT** `/api/v1/policies/{policyId}`

Updates an existing policy.

#### Path Parameters

- `policyId` (UUID): The policy ID

#### Request Body

```json
{
  "name": "USER_READ_ACCESS_UPDATED",
  "description": "Updated policy description",
  "priority": 60,
  "conditions": {
    "user_id": "{{userId}}",
    "resource_type": "USER",
    "tenant_id": "{{tenantId}}"
  },
  "rules": {
    "allow": true,
    "audit": true
  }
}
```

#### Response

```json
{
  "success": true,
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "USER_READ_ACCESS_UPDATED",
    "description": "Updated policy description",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": true,
    "priority": 60,
    "conditions": {
      "user_id": "{{userId}}",
      "resource_type": "USER",
      "tenant_id": "{{tenantId}}"
    },
    "rules": {
      "allow": true,
      "audit": true
    },
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2024-12-31T23:59:59Z",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  },
  "message": "Policy updated successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Delete Policy

**DELETE** `/api/v1/policies/{policyId}`

Deletes a policy.

#### Path Parameters

- `policyId` (UUID): The policy ID

#### Response

```json
{
  "success": true,
  "data": null,
  "message": "Policy deleted successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Get Policies by Name

**GET** `/api/v1/policies/by-name/{policyName}`

Retrieves policies by name.

#### Path Parameters

- `policyName` (String): The policy name

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "USER_READ_ACCESS",
      "description": "Users can read their own data",
      "type": "ACCESS",
      "tenantId": "00000000-0000-0000-0000-000000000000",
      "isActive": true,
      "priority": 50,
      "conditions": {
        "user_id": "{{userId}}",
        "resource_type": "USER"
      },
      "rules": {
        "allow": true
      },
      "validFrom": "2024-01-01T00:00:00Z",
      "validUntil": "2024-12-31T23:59:59Z",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "message": "Policies retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Get Tenant Policies

**GET** `/api/v1/policies/tenant/{tenantId}`

Retrieves all policies for a tenant.

#### Path Parameters

- `tenantId` (UUID): The tenant ID

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "USER_READ_ACCESS",
      "description": "Users can read their own data",
      "type": "ACCESS",
      "tenantId": "00000000-0000-0000-0000-000000000000",
      "isActive": true,
      "priority": 50,
      "conditions": {
        "user_id": "{{userId}}",
        "resource_type": "USER"
      },
      "rules": {
        "allow": true
      },
      "validFrom": "2024-01-01T00:00:00Z",
      "validUntil": "2024-12-31T23:59:59Z",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "message": "Tenant policies retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Get Active Tenant Policies

**GET** `/api/v1/policies/tenant/{tenantId}/active`

Retrieves active policies for a tenant.

#### Path Parameters

- `tenantId` (UUID): The tenant ID

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "USER_READ_ACCESS",
      "description": "Users can read their own data",
      "type": "ACCESS",
      "tenantId": "00000000-0000-0000-0000-000000000000",
      "isActive": true,
      "priority": 50,
      "conditions": {
        "user_id": "{{userId}}",
        "resource_type": "USER"
      },
      "rules": {
        "allow": true
      },
      "validFrom": "2024-01-01T00:00:00Z",
      "validUntil": "2024-12-31T23:59:59Z",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "message": "Active tenant policies retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 🔍 Policy Evaluation APIs

### Evaluate Policy

**POST** `/api/v1/policies/evaluate`

Evaluates a policy against a context.

#### Request Body

```json
{
  "policyName": "USER_READ_ACCESS",
  "context": {
    "userId": "22222222-2222-2222-2222-222222222222",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "permission": "READ",
    "resourceType": "USER",
    "resourceId": "33333333-3333-3333-3333-333333333333",
    "roleName": "USER",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0"
  }
}
```

#### Response

```json
{
  "success": true,
  "data": {
    "allowed": true,
    "reason": "Policy evaluation successful",
    "policyName": "USER_READ_ACCESS",
    "evaluationTime": "2024-01-01T00:00:00Z",
    "context": {
      "userId": "22222222-2222-2222-2222-222222222222",
      "tenantId": "00000000-0000-0000-0000-000000000000",
      "permission": "READ",
      "resourceType": "USER",
      "resourceId": "33333333-3333-3333-3333-333333333333",
      "roleName": "USER",
      "ipAddress": "192.168.1.1",
      "userAgent": "Mozilla/5.0"
    }
  },
  "message": "Policy evaluation completed",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Check Permission

**POST** `/api/v1/policies/check-permission`

Checks if a user has a specific permission.

#### Request Body

```json
{
  "userId": "22222222-2222-2222-2222-222222222222",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "permission": "READ",
  "resourceType": "USER",
  "resourceId": "33333333-3333-3333-3333-333333333333"
}
```

#### Response

```json
{
  "success": true,
  "data": true,
  "message": "Permission check completed",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Check Role

**POST** `/api/v1/policies/check-role`

Checks if a user has a specific role.

#### Request Body

```json
{
  "userId": "22222222-2222-2222-2222-222222222222",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "roleName": "ADMIN"
}
```

#### Response

```json
{
  "success": true,
  "data": true,
  "message": "Role check completed",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Get User Effective Policies

**GET** `/api/v1/policies/user/{userId}/tenant/{tenantId}/effective`

Retrieves effective policies for a user.

#### Path Parameters

- `userId` (UUID): The user ID
- `tenantId` (UUID): The tenant ID

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "USER_READ_ACCESS",
      "description": "Users can read their own data",
      "type": "ACCESS",
      "tenantId": "00000000-0000-0000-0000-000000000000",
      "isActive": true,
      "priority": 50,
      "conditions": {
        "user_id": "{{userId}}",
        "resource_type": "USER"
      },
      "rules": {
        "allow": true
      },
      "validFrom": "2024-01-01T00:00:00Z",
      "validUntil": "2024-12-31T23:59:59Z",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "message": "User effective policies retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 🔧 Policy Management APIs

### Activate Policy

**POST** `/api/v1/policies/{policyId}/activate`

Activates a policy.

#### Path Parameters

- `policyId` (UUID): The policy ID

#### Response

```json
{
  "success": true,
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "USER_READ_ACCESS",
    "description": "Users can read their own data",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": true,
    "priority": 50,
    "conditions": {
      "user_id": "{{userId}}",
      "resource_type": "USER"
    },
    "rules": {
      "allow": true
    },
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2024-12-31T23:59:59Z",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  },
  "message": "Policy activated successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### Deactivate Policy

**POST** `/api/v1/policies/{policyId}/deactivate`

Deactivates a policy.

#### Path Parameters

- `policyId` (UUID): The policy ID

#### Response

```json
{
  "success": true,
  "data": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "USER_READ_ACCESS",
    "description": "Users can read their own data",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": false,
    "priority": 50,
    "conditions": {
      "user_id": "{{userId}}",
      "resource_type": "USER"
    },
    "rules": {
      "allow": true
    },
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2024-12-31T23:59:59Z",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  },
  "message": "Policy deactivated successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 📊 Data Models

### Policy

```json
{
  "id": "UUID",
  "name": "String",
  "description": "String",
  "type": "ACCESS | SECURITY | COMPLIANCE | BUSINESS",
  "tenantId": "UUID",
  "isActive": "Boolean",
  "priority": "Integer",
  "conditions": "Map<String, Object>",
  "rules": "Map<String, Object>",
  "validFrom": "LocalDateTime",
  "validUntil": "LocalDateTime",
  "createdAt": "LocalDateTime",
  "updatedAt": "LocalDateTime"
}
```

### PolicyContext

```json
{
  "userId": "UUID",
  "tenantId": "UUID",
  "permission": "String",
  "resourceType": "String",
  "resourceId": "UUID",
  "roleName": "String",
  "ipAddress": "String",
  "userAgent": "String",
  "deviceInfo": "String",
  "attributes": "Map<String, Object>"
}
```

### PolicyDecision

```json
{
  "allowed": "Boolean",
  "reason": "String",
  "policyName": "String",
  "evaluationTime": "LocalDateTime",
  "context": "PolicyContext"
}
```

## 🚨 Error Responses

### 400 Bad Request

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "name",
        "message": "Policy name is required"
      }
    ]
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### 401 Unauthorized

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Authentication required"
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### 403 Forbidden

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions"
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### 404 Not Found

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Policy not found"
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

### 500 Internal Server Error

```json
{
  "success": false,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Internal server error"
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 🔐 Authentication

### JWT Token

Include JWT token in the Authorization header:

```
Authorization: Bearer <jwt-token>
```

### Internal API Key

Include internal API key in the X-API-Key header:

```
X-API-Key: <internal-api-key>
```

## 📈 Rate Limiting

API endpoints are rate limited to prevent abuse:

- **Policy Creation**: 100 requests per minute
- **Policy Evaluation**: 1000 requests per minute
- **Policy Queries**: 500 requests per minute

Rate limit headers are included in responses:

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1640995200
```

## 🔍 Filtering and Pagination

### Query Parameters

- `page`: Page number (default: 0)
- `size`: Page size (default: 20)
- `sort`: Sort field (default: createdAt)
- `direction`: Sort direction (ASC/DESC, default: DESC)
- `active`: Filter by active status
- `type`: Filter by policy type
- `tenantId`: Filter by tenant ID

### Example

```
GET /api/v1/policies?page=0&size=10&sort=name&direction=ASC&active=true&type=ACCESS
```

### Response

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "name": "USER_READ_ACCESS",
        "description": "Users can read their own data",
        "type": "ACCESS",
        "tenantId": "00000000-0000-0000-0000-000000000000",
        "isActive": true,
        "priority": 50,
        "conditions": {
          "user_id": "{{userId}}",
          "resource_type": "USER"
        },
        "rules": {
          "allow": true
        },
        "validFrom": "2024-01-01T00:00:00Z",
        "validUntil": "2024-12-31T23:59:59Z",
        "createdAt": "2024-01-01T00:00:00Z",
        "updatedAt": "2024-01-01T00:00:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10,
      "sort": {
        "sorted": true,
        "unsorted": false,
        "empty": false
      },
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true,
    "numberOfElements": 1,
    "size": 10,
    "number": 0,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "empty": false
  },
  "message": "Policies retrieved successfully",
  "timestamp": "2024-01-01T00:00:00Z",
  "traceId": "uuid"
}
```

## 📚 SDK Examples

### Java SDK

```java
// Create policy
PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
    .name("USER_READ_ACCESS")
    .description("Users can read their own data")
    .type(PolicyRegistry.PolicyType.ACCESS)
    .tenantId(tenantId)
    .isActive(true)
    .priority(50)
    .conditions(Map.of(
        "user_id", "{{userId}}",
        "resource_type", "USER"
    ))
    .rules(Map.of(
        "allow", true
    ))
    .build();

PolicyRegistry.Policy policy = policyService.createPolicy(request);

// Evaluate policy
PolicyContext context = PolicyContext.builder()
    .userId(userId)
    .tenantId(tenantId)
    .permission("READ")
    .resourceType("USER")
    .resourceId(resourceId)
    .build();

PolicyDecision decision = policyService.evaluatePolicy("USER_READ_ACCESS", context);
```

### JavaScript SDK

```javascript
// Create policy
const request = {
  name: "USER_READ_ACCESS",
  description: "Users can read their own data",
  type: "ACCESS",
  tenantId: "00000000-0000-0000-0000-000000000000",
  isActive: true,
  priority: 50,
  conditions: {
    user_id: "{{userId}}",
    resource_type: "USER",
  },
  rules: {
    allow: true,
  },
};

const policy = await policyService.createPolicy(request);

// Evaluate policy
const context = {
  userId: "22222222-2222-2222-2222-222222222222",
  tenantId: "00000000-0000-0000-0000-000000000000",
  permission: "READ",
  resourceType: "USER",
  resourceId: "33333333-3333-3333-3333-333333333333",
};

const decision = await policyService.evaluatePolicy(
  "USER_READ_ACCESS",
  context
);
```

### Python SDK

```python
# Create policy
request = {
    "name": "USER_READ_ACCESS",
    "description": "Users can read their own data",
    "type": "ACCESS",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "isActive": True,
    "priority": 50,
    "conditions": {
        "user_id": "{{userId}}",
        "resource_type": "USER"
    },
    "rules": {
        "allow": True
    }
}

policy = policy_service.create_policy(request)

# Evaluate policy
context = {
    "userId": "22222222-2222-2222-2222-222222222222",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "permission": "READ",
    "resourceType": "USER",
    "resourceId": "33333333-3333-3333-3333-333333333333"
}

decision = policy_service.evaluate_policy("USER_READ_ACCESS", context)
```

---

**Note**: This API reference is for the Policy Framework. Always refer to the latest documentation for the most up-to-date information.
