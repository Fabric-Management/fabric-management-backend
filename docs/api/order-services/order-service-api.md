# Order Service API Documentation

## 📋 Overview

Order Service, fabric management sisteminde order management, order processing ve order-related business logic için tasarlanmış specialized microservice'dir. Bu servis, sipariş oluşturma, güncelleme, durum takibi ve sipariş işlemlerini yönetir.

### **Service Information**

- **Service Name**: Order Service
- **Port**: 8094
- **Base URL**: `http://localhost:8094`
- **Context Path**: `/api/v1/orders`

### **Responsibilities**

- Order creation ve management
- Order status tracking
- Order validation ve processing
- Order history ve reporting
- Integration with Inventory, Logistics, ve Production services

## 🔐 Authentication

All API endpoints require authentication using JWT tokens.

### **Headers**

```http
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

### **Authentication Flow**

1. Obtain JWT token from Identity Service
2. Include token in Authorization header
3. Token validation is handled by common-security module

## 📡 API Endpoints

### **Order Management**

#### **Create Order**

```http
POST /api/v1/orders
```

**Request Body:**

```json
{
  "customerId": "uuid",
  "companyId": "uuid",
  "orderItems": [
    {
      "productId": "uuid",
      "quantity": 100,
      "unitPrice": 25.5,
      "notes": "Special requirements"
    }
  ],
  "deliveryAddress": {
    "street": "123 Main St",
    "city": "Istanbul",
    "state": "Istanbul",
    "postalCode": "34000",
    "country": "Turkey"
  },
  "deliveryDate": "2024-02-15",
  "priority": "HIGH",
  "notes": "Urgent delivery required"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderNumber": "ORD-2024-001",
    "customerId": "uuid",
    "companyId": "uuid",
    "status": "PENDING",
    "totalAmount": 2550.00,
    "orderItems": [...],
    "deliveryAddress": {...},
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "notes": "Urgent delivery required",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "message": "Order created successfully"
}
```

#### **Get Order by ID**

```http
GET /api/v1/orders/{orderId}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderNumber": "ORD-2024-001",
    "customerId": "uuid",
    "companyId": "uuid",
    "status": "CONFIRMED",
    "totalAmount": 2550.0,
    "orderItems": [
      {
        "id": "uuid",
        "productId": "uuid",
        "productName": "Cotton Fabric",
        "quantity": 100,
        "unitPrice": 25.5,
        "totalPrice": 2550.0,
        "notes": "Special requirements"
      }
    ],
    "deliveryAddress": {
      "street": "123 Main St",
      "city": "Istanbul",
      "state": "Istanbul",
      "postalCode": "34000",
      "country": "Turkey"
    },
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "notes": "Urgent delivery required",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

#### **Update Order Status**

```http
PUT /api/v1/orders/{orderId}/status
```

**Request Body:**

```json
{
  "status": "CONFIRMED",
  "notes": "Order confirmed by customer"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderNumber": "ORD-2024-001",
    "status": "CONFIRMED",
    "updatedAt": "2024-01-15T11:00:00Z"
  },
  "message": "Order status updated successfully"
}
```

#### **Get Orders by Customer**

```http
GET /api/v1/orders/customer/{customerId}
```

**Query Parameters:**

- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)
- `status` (optional): Filter by order status
- `sort` (optional): Sort field (default: createdAt)

**Response:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "orderNumber": "ORD-2024-001",
        "status": "CONFIRMED",
        "totalAmount": 2550.0,
        "deliveryDate": "2024-02-15",
        "createdAt": "2024-01-15T10:30:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": {
        "sorted": true,
        "unsorted": false
      }
    },
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

#### **Get Orders by Company**

```http
GET /api/v1/orders/company/{companyId}
```

**Query Parameters:**

- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)
- `status` (optional): Filter by order status
- `priority` (optional): Filter by priority
- `sort` (optional): Sort field (default: createdAt)

**Response:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "orderNumber": "ORD-2024-001",
        "customerId": "uuid",
        "customerName": "ABC Textile",
        "status": "CONFIRMED",
        "totalAmount": 2550.0,
        "priority": "HIGH",
        "deliveryDate": "2024-02-15",
        "createdAt": "2024-01-15T10:30:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": {
        "sorted": true,
        "unsorted": false
      }
    },
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

### **Order Items Management**

#### **Add Item to Order**

```http
POST /api/v1/orders/{orderId}/items
```

**Request Body:**

```json
{
  "productId": "uuid",
  "quantity": 50,
  "unitPrice": 30.0,
  "notes": "Additional items"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderId": "uuid",
    "productId": "uuid",
    "productName": "Silk Fabric",
    "quantity": 50,
    "unitPrice": 30.0,
    "totalPrice": 1500.0,
    "notes": "Additional items",
    "createdAt": "2024-01-15T12:00:00Z"
  },
  "message": "Item added to order successfully"
}
```

#### **Update Order Item**

```http
PUT /api/v1/orders/{orderId}/items/{itemId}
```

**Request Body:**

```json
{
  "quantity": 75,
  "unitPrice": 28.0,
  "notes": "Updated quantity and price"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "orderId": "uuid",
    "productId": "uuid",
    "productName": "Silk Fabric",
    "quantity": 75,
    "unitPrice": 28.0,
    "totalPrice": 2100.0,
    "notes": "Updated quantity and price",
    "updatedAt": "2024-01-15T12:30:00Z"
  },
  "message": "Order item updated successfully"
}
```

#### **Remove Item from Order**

```http
DELETE /api/v1/orders/{orderId}/items/{itemId}
```

**Response:**

```json
{
  "success": true,
  "message": "Order item removed successfully"
}
```

## 📊 Data Models

### **Order**

```json
{
  "id": "uuid",
  "orderNumber": "string",
  "customerId": "uuid",
  "companyId": "uuid",
  "status": "PENDING | CONFIRMED | PROCESSING | SHIPPED | DELIVERED | CANCELLED",
  "totalAmount": "decimal",
  "orderItems": ["OrderItem"],
  "deliveryAddress": "Address",
  "deliveryDate": "date",
  "priority": "LOW | MEDIUM | HIGH | URGENT",
  "notes": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### **OrderItem**

```json
{
  "id": "uuid",
  "orderId": "uuid",
  "productId": "uuid",
  "productName": "string",
  "quantity": "integer",
  "unitPrice": "decimal",
  "totalPrice": "decimal",
  "notes": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### **Address**

```json
{
  "street": "string",
  "city": "string",
  "state": "string",
  "postalCode": "string",
  "country": "string"
}
```

## ⚠️ Error Handling

### **Error Response Format**

```json
{
  "success": false,
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Order with ID {orderId} not found",
    "details": "Additional error details",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### **Common Error Codes**

| Code                     | HTTP Status | Description                     |
| ------------------------ | ----------- | ------------------------------- |
| `ORDER_NOT_FOUND`        | 404         | Order not found                 |
| `INVALID_ORDER_STATUS`   | 400         | Invalid order status transition |
| `INSUFFICIENT_INVENTORY` | 400         | Not enough inventory for order  |
| `INVALID_ORDER_DATA`     | 400         | Invalid order data provided     |
| `UNAUTHORIZED`           | 401         | Authentication required         |
| `FORBIDDEN`              | 403         | Insufficient permissions        |
| `INTERNAL_ERROR`         | 500         | Internal server error           |

## 🚦 Rate Limiting

- **Rate Limit**: 1000 requests per hour per user
- **Burst Limit**: 100 requests per minute
- **Headers**:
  - `X-RateLimit-Limit`: Request limit per hour
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time in UTC

## 🔄 Event Integration

### **Published Events**

- `OrderCreated`: When a new order is created
- `OrderStatusChanged`: When order status is updated
- `OrderItemAdded`: When item is added to order
- `OrderItemUpdated`: When order item is updated
- `OrderItemRemoved`: When item is removed from order

### **Consumed Events**

- `InventoryReserved`: From Inventory Service
- `InventoryReleased`: From Inventory Service
- `CustomerUpdated`: From Contact Service
- `ProductUpdated`: From Catalog Service

## 📝 Examples

### **Complete Order Creation Flow**

1. **Create Order**

```bash
curl -X POST http://localhost:8094/api/v1/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "orderItems": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440002",
        "quantity": 100,
        "unitPrice": 25.50,
        "notes": "High quality cotton"
      }
    ],
    "deliveryAddress": {
      "street": "123 Main St",
      "city": "Istanbul",
      "state": "Istanbul",
      "postalCode": "34000",
      "country": "Turkey"
    },
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "notes": "Urgent delivery"
  }'
```

2. **Update Order Status**

```bash
curl -X PUT http://localhost:8094/api/v1/orders/{orderId}/status \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "CONFIRMED",
    "notes": "Order confirmed by customer"
  }'
```

3. **Get Order Details**

```bash
curl -X GET http://localhost:8094/api/v1/orders/{orderId} \
  -H "Authorization: Bearer <token>"
```

## 🔧 Configuration

### **Application Properties**

```yaml
server:
  port: 8094

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: order_user
    password: order_pass
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

# Service URLs
services:
  identity:
    url: http://localhost:8081
  contact:
    url: http://localhost:8083
  company:
    url: http://localhost:8084
  inventory:
    url: http://localhost:8089
  catalog:
    url: http://localhost:8090
  pricing:
    url: http://localhost:8091
  logistics:
    url: http://localhost:8095
  production:
    url: http://localhost:8096

# RabbitMQ Configuration
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /

# Redis Configuration
spring:
  redis:
    host: localhost
    port: 6379
    password: ""
    database: 0
```

---

**Last Updated**: 2024-01-XX  
**Version**: 1.0.0  
**Maintainer**: Order Service Team
