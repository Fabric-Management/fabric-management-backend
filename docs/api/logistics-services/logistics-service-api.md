# Logistics Service API Documentation

## 📋 Overview

Logistics Service, fabric management sisteminde logistics management, shipping, delivery ve route optimization için tasarlanmış specialized microservice'dir. Bu servis, kargo takibi, teslimat planlaması ve lojistik operasyonlarını yönetir.

### **Service Information**

- **Service Name**: Logistics Service
- **Port**: 8095
- **Base URL**: `http://localhost:8095`
- **Context Path**: `/api/v1/logistics`

### **Responsibilities**

- Shipment creation ve management
- Delivery route planning ve optimization
- Package tracking ve status updates
- Delivery scheduling ve coordination
- Integration with Order, Production, ve Customer services

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

### **Shipment Management**

#### **Create Shipment**

```http
POST /api/v1/logistics/shipments
```

**Request Body:**

```json
{
  "orderId": "uuid",
  "customerId": "uuid",
  "companyId": "uuid",
  "pickupAddress": {
    "street": "456 Factory St",
    "city": "Bursa",
    "state": "Bursa",
    "postalCode": "16000",
    "country": "Turkey"
  },
  "deliveryAddress": {
    "street": "123 Main St",
    "city": "Istanbul",
    "state": "Istanbul",
    "postalCode": "34000",
    "country": "Turkey"
  },
  "packages": [
    {
      "productId": "uuid",
      "productName": "Cotton Fabric",
      "quantity": 100,
      "weight": 50.5,
      "dimensions": {
        "length": 100,
        "width": 80,
        "height": 20
      },
      "fragile": false,
      "specialInstructions": "Handle with care"
    }
  ],
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
    "shipmentNumber": "SHIP-2024-001",
    "orderId": "uuid",
    "customerId": "uuid",
    "companyId": "uuid",
    "status": "PENDING",
    "pickupAddress": {...},
    "deliveryAddress": {...},
    "packages": [...],
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "estimatedDeliveryTime": "2024-02-15T14:00:00Z",
    "notes": "Urgent delivery required",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "message": "Shipment created successfully"
}
```

#### **Get Shipment by ID**

```http
GET /api/v1/logistics/shipments/{shipmentId}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "shipmentNumber": "SHIP-2024-001",
    "orderId": "uuid",
    "customerId": "uuid",
    "companyId": "uuid",
    "status": "IN_TRANSIT",
    "pickupAddress": {
      "street": "456 Factory St",
      "city": "Bursa",
      "state": "Bursa",
      "postalCode": "16000",
      "country": "Turkey"
    },
    "deliveryAddress": {
      "street": "123 Main St",
      "city": "Istanbul",
      "state": "Istanbul",
      "postalCode": "34000",
      "country": "Turkey"
    },
    "packages": [
      {
        "id": "uuid",
        "productId": "uuid",
        "productName": "Cotton Fabric",
        "quantity": 100,
        "weight": 50.5,
        "dimensions": {
          "length": 100,
          "width": 80,
          "height": 20
        },
        "fragile": false,
        "specialInstructions": "Handle with care"
      }
    ],
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "estimatedDeliveryTime": "2024-02-15T14:00:00Z",
    "trackingNumber": "TRK123456789",
    "notes": "Urgent delivery required",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T12:00:00Z"
  }
}
```

#### **Update Shipment Status**

```http
PUT /api/v1/logistics/shipments/{shipmentId}/status
```

**Request Body:**

```json
{
  "status": "IN_TRANSIT",
  "location": "Bursa Distribution Center",
  "notes": "Package picked up and in transit"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "shipmentNumber": "SHIP-2024-001",
    "status": "IN_TRANSIT",
    "location": "Bursa Distribution Center",
    "updatedAt": "2024-01-15T12:00:00Z"
  },
  "message": "Shipment status updated successfully"
}
```

#### **Get Shipments by Order**

```http
GET /api/v1/logistics/shipments/order/{orderId}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "shipmentNumber": "SHIP-2024-001",
      "status": "IN_TRANSIT",
      "deliveryDate": "2024-02-15",
      "priority": "HIGH",
      "trackingNumber": "TRK123456789",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### **Tracking Management**

#### **Get Tracking Information**

```http
GET /api/v1/logistics/tracking/{trackingNumber}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "trackingNumber": "TRK123456789",
    "shipmentId": "uuid",
    "status": "IN_TRANSIT",
    "currentLocation": "Bursa Distribution Center",
    "estimatedDeliveryTime": "2024-02-15T14:00:00Z",
    "trackingHistory": [
      {
        "status": "PENDING",
        "location": "Warehouse",
        "timestamp": "2024-01-15T10:30:00Z",
        "notes": "Shipment created"
      },
      {
        "status": "PICKED_UP",
        "location": "Bursa Distribution Center",
        "timestamp": "2024-01-15T12:00:00Z",
        "notes": "Package picked up"
      },
      {
        "status": "IN_TRANSIT",
        "location": "Bursa Distribution Center",
        "timestamp": "2024-01-15T12:30:00Z",
        "notes": "Package in transit to Istanbul"
      }
    ]
  }
}
```

#### **Add Tracking Update**

```http
POST /api/v1/logistics/tracking/{trackingNumber}/updates
```

**Request Body:**

```json
{
  "status": "DELIVERED",
  "location": "Istanbul Delivery Center",
  "notes": "Package delivered successfully",
  "deliveredBy": "John Doe",
  "deliveryTime": "2024-01-15T14:30:00Z"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "trackingNumber": "TRK123456789",
    "status": "DELIVERED",
    "location": "Istanbul Delivery Center",
    "timestamp": "2024-01-15T14:30:00Z",
    "notes": "Package delivered successfully",
    "deliveredBy": "John Doe"
  },
  "message": "Tracking update added successfully"
}
```

### **Route Planning**

#### **Plan Delivery Route**

```http
POST /api/v1/logistics/routes/plan
```

**Request Body:**

```json
{
  "pickupLocation": {
    "latitude": 40.1826,
    "longitude": 29.0665,
    "address": "Bursa Distribution Center"
  },
  "deliveryLocations": [
    {
      "latitude": 41.0082,
      "longitude": 28.9784,
      "address": "Istanbul Delivery Center",
      "priority": "HIGH",
      "timeWindow": {
        "start": "09:00",
        "end": "17:00"
      }
    }
  ],
  "vehicleType": "TRUCK",
  "maxWeight": 1000,
  "maxVolume": 50
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "routeId": "uuid",
    "totalDistance": 150.5,
    "estimatedDuration": "2h 30m",
    "totalCost": 250.0,
    "waypoints": [
      {
        "sequence": 1,
        "location": {
          "latitude": 40.1826,
          "longitude": 29.0665,
          "address": "Bursa Distribution Center"
        },
        "type": "PICKUP",
        "estimatedArrival": "2024-01-15T10:00:00Z"
      },
      {
        "sequence": 2,
        "location": {
          "latitude": 41.0082,
          "longitude": 28.9784,
          "address": "Istanbul Delivery Center"
        },
        "type": "DELIVERY",
        "estimatedArrival": "2024-01-15T12:30:00Z"
      }
    ],
    "optimizationScore": 0.95
  }
}
```

#### **Get Route Optimization**

```http
GET /api/v1/logistics/routes/{routeId}/optimize
```

**Response:**

```json
{
  "success": true,
  "data": {
    "routeId": "uuid",
    "originalDistance": 150.5,
    "optimizedDistance": 135.2,
    "distanceSavings": 15.3,
    "originalDuration": "2h 30m",
    "optimizedDuration": "2h 15m",
    "timeSavings": "15m",
    "costSavings": 25.00,
    "optimizedWaypoints": [...]
  }
}
```

### **Delivery Management**

#### **Schedule Delivery**

```http
POST /api/v1/logistics/deliveries/schedule
```

**Request Body:**

```json
{
  "shipmentId": "uuid",
  "deliveryDate": "2024-02-15",
  "timeWindow": {
    "start": "09:00",
    "end": "17:00"
  },
  "deliveryInstructions": "Call before delivery",
  "contactPhone": "+90 555 123 4567"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "shipmentId": "uuid",
    "deliveryDate": "2024-02-15",
    "timeWindow": {
      "start": "09:00",
      "end": "17:00"
    },
    "status": "SCHEDULED",
    "deliveryInstructions": "Call before delivery",
    "contactPhone": "+90 555 123 4567",
    "assignedDriver": "John Doe",
    "vehicleId": "TRK-001",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "message": "Delivery scheduled successfully"
}
```

#### **Get Delivery Schedule**

```http
GET /api/v1/logistics/deliveries/schedule
```

**Query Parameters:**

- `date` (optional): Filter by delivery date
- `driver` (optional): Filter by driver
- `status` (optional): Filter by delivery status

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "shipmentId": "uuid",
      "shipmentNumber": "SHIP-2024-001",
      "customerName": "ABC Textile",
      "deliveryAddress": "123 Main St, Istanbul",
      "deliveryDate": "2024-02-15",
      "timeWindow": {
        "start": "09:00",
        "end": "17:00"
      },
      "status": "SCHEDULED",
      "assignedDriver": "John Doe",
      "vehicleId": "TRK-001"
    }
  ]
}
```

## 📊 Data Models

### **Shipment**

```json
{
  "id": "uuid",
  "shipmentNumber": "string",
  "orderId": "uuid",
  "customerId": "uuid",
  "companyId": "uuid",
  "status": "PENDING | PICKED_UP | IN_TRANSIT | OUT_FOR_DELIVERY | DELIVERED | FAILED",
  "pickupAddress": "Address",
  "deliveryAddress": "Address",
  "packages": ["Package"],
  "deliveryDate": "date",
  "priority": "LOW | MEDIUM | HIGH | URGENT",
  "estimatedDeliveryTime": "datetime",
  "trackingNumber": "string",
  "notes": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### **Package**

```json
{
  "id": "uuid",
  "productId": "uuid",
  "productName": "string",
  "quantity": "integer",
  "weight": "decimal",
  "dimensions": "Dimensions",
  "fragile": "boolean",
  "specialInstructions": "string"
}
```

### **Dimensions**

```json
{
  "length": "decimal",
  "width": "decimal",
  "height": "decimal",
  "unit": "CM | INCH"
}
```

### **TrackingUpdate**

```json
{
  "id": "uuid",
  "trackingNumber": "string",
  "status": "string",
  "location": "string",
  "timestamp": "datetime",
  "notes": "string",
  "deliveredBy": "string",
  "deliveryTime": "datetime"
}
```

### **Route**

```json
{
  "id": "uuid",
  "totalDistance": "decimal",
  "estimatedDuration": "string",
  "totalCost": "decimal",
  "waypoints": ["Waypoint"],
  "optimizationScore": "decimal"
}
```

### **Waypoint**

```json
{
  "sequence": "integer",
  "location": "Location",
  "type": "PICKUP | DELIVERY | WAYPOINT",
  "estimatedArrival": "datetime"
}
```

### **Location**

```json
{
  "latitude": "decimal",
  "longitude": "decimal",
  "address": "string"
}
```

## ⚠️ Error Handling

### **Error Response Format**

```json
{
  "success": false,
  "error": {
    "code": "SHIPMENT_NOT_FOUND",
    "message": "Shipment with ID {shipmentId} not found",
    "details": "Additional error details",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### **Common Error Codes**

| Code                         | HTTP Status | Description                        |
| ---------------------------- | ----------- | ---------------------------------- |
| `SHIPMENT_NOT_FOUND`         | 404         | Shipment not found                 |
| `INVALID_SHIPMENT_STATUS`    | 400         | Invalid shipment status transition |
| `TRACKING_NUMBER_NOT_FOUND`  | 404         | Tracking number not found          |
| `INVALID_ROUTE_DATA`         | 400         | Invalid route data provided        |
| `DELIVERY_SCHEDULE_CONFLICT` | 409         | Delivery schedule conflict         |
| `UNAUTHORIZED`               | 401         | Authentication required            |
| `FORBIDDEN`                  | 403         | Insufficient permissions           |
| `INTERNAL_ERROR`             | 500         | Internal server error              |

## 🚦 Rate Limiting

- **Rate Limit**: 1000 requests per hour per user
- **Burst Limit**: 100 requests per minute
- **Headers**:
  - `X-RateLimit-Limit`: Request limit per hour
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time in UTC

## 🔄 Event Integration

### **Published Events**

- `ShipmentCreated`: When a new shipment is created
- `ShipmentStatusChanged`: When shipment status is updated
- `TrackingUpdateAdded`: When tracking update is added
- `DeliveryScheduled`: When delivery is scheduled
- `DeliveryCompleted`: When delivery is completed

### **Consumed Events**

- `OrderConfirmed`: From Order Service
- `OrderShipped`: From Order Service
- `CustomerUpdated`: From Contact Service
- `AddressUpdated`: From Contact Service

## 📝 Examples

### **Complete Shipment Creation Flow**

1. **Create Shipment**

```bash
curl -X POST http://localhost:8095/api/v1/logistics/shipments \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "customerId": "550e8400-e29b-41d4-a716-446655440001",
    "companyId": "550e8400-e29b-41d4-a716-446655440002",
    "pickupAddress": {
      "street": "456 Factory St",
      "city": "Bursa",
      "state": "Bursa",
      "postalCode": "16000",
      "country": "Turkey"
    },
    "deliveryAddress": {
      "street": "123 Main St",
      "city": "Istanbul",
      "state": "Istanbul",
      "postalCode": "34000",
      "country": "Turkey"
    },
    "packages": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440003",
        "productName": "Cotton Fabric",
        "quantity": 100,
        "weight": 50.5,
        "dimensions": {
          "length": 100,
          "width": 80,
          "height": 20
        },
        "fragile": false,
        "specialInstructions": "Handle with care"
      }
    ],
    "deliveryDate": "2024-02-15",
    "priority": "HIGH",
    "notes": "Urgent delivery"
  }'
```

2. **Update Shipment Status**

```bash
curl -X PUT http://localhost:8095/api/v1/logistics/shipments/{shipmentId}/status \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_TRANSIT",
    "location": "Bursa Distribution Center",
    "notes": "Package picked up and in transit"
  }'
```

3. **Get Tracking Information**

```bash
curl -X GET http://localhost:8095/api/v1/logistics/tracking/{trackingNumber} \
  -H "Authorization: Bearer <token>"
```

## 🔧 Configuration

### **Application Properties**

```yaml
server:
  port: 8095

spring:
  application:
    name: logistics-service
  datasource:
    url: jdbc:postgresql://localhost:5432/logistics_db
    username: logistics_user
    password: logistics_pass
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
  order:
    url: http://localhost:8094
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

# External APIs
external:
  maps:
    api-key: "your-maps-api-key"
    base-url: "https://maps.googleapis.com/maps/api"
  tracking:
    api-key: "your-tracking-api-key"
    base-url: "https://api.tracking.com"
```

---

**Last Updated**: 2024-01-XX  
**Version**: 1.0.0  
**Maintainer**: Logistics Service Team
