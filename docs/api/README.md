# 🔌 API Documentation

**Version:** 1.0.0  
**Last Updated:** October 10, 2025  
**API Version:** v1  
**Status:** ✅ Active

---

## 📋 Overview

The Fabric Management System provides comprehensive REST APIs following OpenAPI 3.0 specifications. All APIs are designed with modern best practices including versioning, pagination, filtering, and comprehensive error handling.

---

## 🎯 Base URLs

| Environment    | Base URL                                          | Status         |
| -------------- | ------------------------------------------------- | -------------- |
| **Production** | `https://api.fabricmanagement.com/api/v1`         | 🟢 Live        |
| **Staging**    | `https://staging-api.fabricmanagement.com/api/v1` | 🟡 Testing     |
| **Local**      | `http://localhost:8080/api/v1`                    | 🔵 Development |

---

## 🔐 Authentication

All APIs require JWT-based authentication:

```http
Authorization: Bearer <jwt_token>
X-Tenant-ID: <tenant_uuid>
```

### Authentication Flow

```
1. POST /api/v1/users/auth/login
   → Request: { contactValue, password }
   → Response: { accessToken, refreshToken, userId, tenantId }

2. Use accessToken in subsequent requests
   → Header: Authorization: Bearer <accessToken>

3. Refresh token when expired
   → POST /api/v1/users/auth/refresh
   → Request: { refreshToken }
```

**Note**: Authentication uses `contactValue` (email or phone), NOT username.  
See: [NO USERNAME PRINCIPLE](../development/PRINCIPLES.md#-no-username-principle)

---

## 📦 Response Format

### Success Response

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2025-10-10T10:30:00Z",
  "requestId": "req_123456789"
}
```

### Error Response

```json
{
  "success": false,
  "message": "Error description",
  "errorCode": "ERROR_CODE",
  "errors": ["Detailed error messages"],
  "timestamp": "2025-10-10T10:30:00Z",
  "requestId": "req_123456789"
}
```

---

## 🏗️ Service APIs

### 👤 User Service API

**Base Path:** `/api/v1/users`  
**Port:** 8081  
**Status:** ✅ Production

#### Endpoints

| Endpoint          | Method | Description            | Auth Required |
| ----------------- | ------ | ---------------------- | ------------- |
| `/auth/login`     | POST   | User authentication    | ❌ No         |
| `/auth/logout`    | POST   | User logout            | ✅ Yes        |
| `/auth/refresh`   | POST   | Refresh JWT token      | ✅ Yes        |
| `/auth/me`        | GET    | Get current user info  | ✅ Yes        |
| `/users`          | GET    | List users (paginated) | ✅ Yes        |
| `/users/{userId}` | GET    | Get user by ID         | ✅ Yes        |
| `/users`          | POST   | Create new user        | ✅ Yes        |
| `/users/{userId}` | PUT    | Update user            | ✅ Yes        |
| `/users/{userId}` | DELETE | Delete user            | ✅ Yes        |

#### Example: Login

**Request:**

```http
POST /api/v1/users/auth/login
Content-Type: application/json

{
  "contactValue": "john.doe@company.com",
  "password": "securePassword123"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600,
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c"
  }
}
```

---

### 📞 Contact Service API

**Base Path:** `/api/v1/contacts`  
**Port:** 8082  
**Status:** ✅ Production

#### Endpoints

| Endpoint                        | Method | Description               |
| ------------------------------- | ------ | ------------------------- |
| `/contacts`                     | GET    | List contacts (paginated) |
| `/contacts/{id}`                | GET    | Get contact by ID         |
| `/contacts`                     | POST   | Create new contact        |
| `/contacts/{id}`                | PUT    | Update contact            |
| `/contacts/{id}`                | DELETE | Delete contact            |
| `/contacts/user/{userId}`       | GET    | Get user contacts         |
| `/contacts/company/{companyId}` | GET    | Get company contacts      |

---

### 🏢 Company Service API

**Base Path:** `/api/v1/companies`  
**Port:** 8083  
**Status:** ✅ Production

#### Endpoints

| Endpoint                   | Method | Description                |
| -------------------------- | ------ | -------------------------- |
| `/companies`               | GET    | List companies (paginated) |
| `/companies/{id}`          | GET    | Get company by ID          |
| `/companies`               | POST   | Create new company         |
| `/companies/{id}`          | PUT    | Update company             |
| `/companies/{id}`          | DELETE | Delete company             |
| `/companies/{id}/users`    | GET    | Get company users          |
| `/companies/{id}/settings` | GET    | Get company settings       |

---

## 📊 Common API Patterns

### Pagination

All list endpoints support pagination:

```http
GET /api/v1/users?page=0&size=20&sort=firstName,asc
```

**Response:**

```json
{
  "success": true,
  "data": {
    "content": [...],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8,
      "first": true,
      "last": false
    }
  }
}
```

### Filtering

Dynamic filtering support:

```http
GET /api/v1/users?filter=firstName:John,status:ACTIVE
```

### Sorting

Multiple field sorting:

```http
GET /api/v1/users?sort=lastName,asc&sort=firstName,desc
```

### Search

Full-text search:

```http
GET /api/v1/users?search=John&fields=firstName,lastName,email
```

---

## 🔒 Security

### Rate Limiting

| Endpoint Type      | Rate Limit          | Status    |
| ------------------ | ------------------- | --------- |
| **Authentication** | 5 requests/minute   | ✅ Active |
| **General APIs**   | 100 requests/minute | ✅ Active |
| **File Upload**    | 10 requests/minute  | ✅ Active |
| **Reports**        | 20 requests/minute  | ✅ Active |

### Authorization Levels

| Role        | Permissions           |
| ----------- | --------------------- |
| **ADMIN**   | Full system access    |
| **MANAGER** | Department management |
| **USER**    | Basic operations      |
| **VIEWER**  | Read-only access      |

---

## 📈 Performance

### Response Time Targets

| Operation           | Target  | SLA   |
| ------------------- | ------- | ----- |
| **Authentication**  | < 200ms | 99.9% |
| **CRUD Operations** | < 500ms | 99.5% |
| **Search Queries**  | < 1s    | 99%   |
| **Reports**         | < 5s    | 95%   |

### Caching Strategy

- **User Data**: 5 minutes
- **Company Data**: 10 minutes
- **Product Catalog**: 1 hour
- **Static Data**: 24 hours

---

## 🧪 Testing

### API Testing Tools

- **Postman Collection**: Available in `/postman` directory
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

### Test Environment

```bash
# Start test environment
docker-compose -f docker-compose.test.yml up -d

# Run API tests
mvn test -Dtest=*ApiTest

# Generate test report
mvn surefire-report:report
```

---

## 📚 API Standards

For complete API development standards, see:

### Required Reading

| Document                                                                        | Description                                     | Priority     |
| ------------------------------------------------------------------------------- | ----------------------------------------------- | ------------ |
| [MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md) | ⭐ **API Gateway routing, controller patterns** | 🔴 MANDATORY |
| [DATA_TYPES_STANDARDS.md](../development/DATA_TYPES_STANDARDS.md)               | ⭐ **UUID usage standards**                     | 🔴 MANDATORY |
| [PRINCIPLES.md](../development/PRINCIPLES.md)                                   | Core development principles                     | 🔴 HIGH      |

### Key Standards

- ✅ Use full paths: `/api/v1/{resource}` (Service-Aware Pattern)
- ✅ Use UUID for all IDs (not String)
- ✅ Use `ApiResponse<T>` wrapper
- ✅ Use `PagedResponse<T>` for lists
- ✅ Proper HTTP status codes (200, 201, 404, 400, etc.)
- ✅ No username - use `contactValue` (email/phone)

---

## 🔗 Related Documentation

### Internal Links

- [Development Guide](../development/README.md) - Development standards
- [Architecture](../architecture/README.md) - System architecture
- [Security](../SECURITY.md) - Security practices
- [Deployment](../deployment/README.md) - Deployment guide

### External Resources

- [OpenAPI Specification](https://swagger.io/specification/)
- [REST API Best Practices](https://restfulapi.net/)
- [HTTP Status Codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)

---

## 📞 Support

### Getting Help

- **API Questions**: #fabric-api on Slack
- **Bug Reports**: GitHub Issues with `api` label
- **Feature Requests**: Discuss in #fabric-dev
- **Documentation**: Update this file via PR

### API Status

- **Status Page**: `https://status.fabricmanagement.com`
- **Health Check**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Version:** 1.0.0  
**Status:** ✅ Active - All APIs operational
