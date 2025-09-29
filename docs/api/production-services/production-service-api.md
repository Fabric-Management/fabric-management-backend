# Production Service API Documentation

## 📋 Overview

Production Service, fabric management sisteminde production management, manufacturing planning, production scheduling ve production monitoring için tasarlanmış specialized microservice'dir. Bu servis, üretim planlaması, makine yönetimi ve üretim süreçlerini yönetir.

### **Service Information**

- **Service Name**: Production Service
- **Port**: 8096
- **Base URL**: `http://localhost:8096`
- **Context Path**: `/api/v1/production`

### **Responsibilities**

- Production planning ve scheduling
- Machine ve equipment management
- Production order management
- Quality control integration
- Production monitoring ve reporting
- Integration with Order, Inventory, ve Logistics services

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

### **Production Planning**

#### **Create Production Plan**

```http
POST /api/v1/production/plans
```

**Request Body:**

```json
{
  "orderId": "uuid",
  "companyId": "uuid",
  "productId": "uuid",
  "productName": "Cotton Fabric",
  "quantity": 1000,
  "unit": "METERS",
  "plannedStartDate": "2024-02-01",
  "plannedEndDate": "2024-02-15",
  "priority": "HIGH",
  "productionSteps": [
    {
      "stepName": "Cutting",
      "machineType": "CUTTING_MACHINE",
      "estimatedDuration": 480,
      "dependencies": []
    },
    {
      "stepName": "Sewing",
      "machineType": "SEWING_MACHINE",
      "estimatedDuration": 720,
      "dependencies": ["Cutting"]
    },
    {
      "stepName": "Finishing",
      "machineType": "FINISHING_MACHINE",
      "estimatedDuration": 240,
      "dependencies": ["Sewing"]
    }
  ],
  "qualityRequirements": {
    "tolerance": 0.02,
    "inspectionRequired": true,
    "certificationRequired": false
  },
  "notes": "High priority order for urgent delivery"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "planNumber": "PLAN-2024-001",
    "orderId": "uuid",
    "companyId": "uuid",
    "productId": "uuid",
    "productName": "Cotton Fabric",
    "quantity": 1000,
    "unit": "METERS",
    "status": "PLANNED",
    "plannedStartDate": "2024-02-01",
    "plannedEndDate": "2024-02-15",
    "priority": "HIGH",
    "productionSteps": [...],
    "qualityRequirements": {...},
    "notes": "High priority order for urgent delivery",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "message": "Production plan created successfully"
}
```

#### **Get Production Plan by ID**

```http
GET /api/v1/production/plans/{planId}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "planNumber": "PLAN-2024-001",
    "orderId": "uuid",
    "companyId": "uuid",
    "productId": "uuid",
    "productName": "Cotton Fabric",
    "quantity": 1000,
    "unit": "METERS",
    "status": "IN_PROGRESS",
    "plannedStartDate": "2024-02-01",
    "plannedEndDate": "2024-02-15",
    "actualStartDate": "2024-02-01T08:00:00Z",
    "actualEndDate": null,
    "priority": "HIGH",
    "productionSteps": [
      {
        "id": "uuid",
        "stepName": "Cutting",
        "machineType": "CUTTING_MACHINE",
        "status": "COMPLETED",
        "estimatedDuration": 480,
        "actualDuration": 450,
        "startTime": "2024-02-01T08:00:00Z",
        "endTime": "2024-02-01T15:30:00Z",
        "dependencies": [],
        "assignedMachine": "CUT-001",
        "assignedOperator": "John Doe"
      },
      {
        "id": "uuid",
        "stepName": "Sewing",
        "machineType": "SEWING_MACHINE",
        "status": "IN_PROGRESS",
        "estimatedDuration": 720,
        "actualDuration": null,
        "startTime": "2024-02-01T16:00:00Z",
        "endTime": null,
        "dependencies": ["Cutting"],
        "assignedMachine": "SEW-001",
        "assignedOperator": "Jane Smith"
      }
    ],
    "qualityRequirements": {...},
    "notes": "High priority order for urgent delivery",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-02-01T16:00:00Z"
  }
}
```

#### **Update Production Plan Status**

```http
PUT /api/v1/production/plans/{planId}/status
```

**Request Body:**

```json
{
  "status": "IN_PROGRESS",
  "actualStartDate": "2024-02-01T08:00:00Z",
  "notes": "Production started on schedule"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "planNumber": "PLAN-2024-001",
    "status": "IN_PROGRESS",
    "actualStartDate": "2024-02-01T08:00:00Z",
    "updatedAt": "2024-02-01T08:00:00Z"
  },
  "message": "Production plan status updated successfully"
}
```

### **Machine Management**

#### **Get Available Machines**

```http
GET /api/v1/production/machines/available
```

**Query Parameters:**

- `machineType` (optional): Filter by machine type
- `date` (optional): Check availability for specific date
- `duration` (optional): Required duration in minutes

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "machineCode": "CUT-001",
      "machineName": "Cutting Machine 1",
      "machineType": "CUTTING_MACHINE",
      "status": "AVAILABLE",
      "capacity": 100,
      "currentLoad": 0,
      "maintenanceDue": "2024-03-15",
      "lastMaintenance": "2024-01-01",
      "location": "Production Floor A"
    },
    {
      "id": "uuid",
      "machineCode": "SEW-001",
      "machineName": "Sewing Machine 1",
      "machineType": "SEWING_MACHINE",
      "status": "AVAILABLE",
      "capacity": 80,
      "currentLoad": 0,
      "maintenanceDue": "2024-03-20",
      "lastMaintenance": "2024-01-05",
      "location": "Production Floor B"
    }
  ]
}
```

#### **Assign Machine to Production Step**

```http
PUT /api/v1/production/plans/{planId}/steps/{stepId}/assign-machine
```

**Request Body:**

```json
{
  "machineId": "uuid",
  "operatorId": "uuid",
  "scheduledStartTime": "2024-02-01T08:00:00Z",
  "notes": "Assigned to experienced operator"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "stepName": "Cutting",
    "assignedMachine": "CUT-001",
    "assignedOperator": "John Doe",
    "scheduledStartTime": "2024-02-01T08:00:00Z",
    "status": "ASSIGNED"
  },
  "message": "Machine assigned to production step successfully"
}
```

#### **Get Machine Status**

```http
GET /api/v1/production/machines/{machineId}/status
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "machineCode": "CUT-001",
    "machineName": "Cutting Machine 1",
    "status": "RUNNING",
    "currentProductionPlan": "PLAN-2024-001",
    "currentStep": "Cutting",
    "operator": "John Doe",
    "startTime": "2024-02-01T08:00:00Z",
    "estimatedCompletion": "2024-02-01T15:30:00Z",
    "productionMetrics": {
      "unitsProduced": 150,
      "targetUnits": 200,
      "efficiency": 0.75,
      "qualityRate": 0.98
    },
    "alerts": []
  }
}
```

### **Production Monitoring**

#### **Get Production Dashboard**

```http
GET /api/v1/production/dashboard
```

**Query Parameters:**

- `date` (optional): Filter by date (default: today)
- `companyId` (optional): Filter by company

**Response:**

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalPlans": 15,
      "activePlans": 8,
      "completedPlans": 5,
      "delayedPlans": 2,
      "totalProduction": 5000,
      "targetProduction": 6000,
      "efficiency": 0.83
    },
    "machineStatus": [
      {
        "machineCode": "CUT-001",
        "status": "RUNNING",
        "utilization": 0.85,
        "currentPlan": "PLAN-2024-001"
      },
      {
        "machineCode": "SEW-001",
        "status": "IDLE",
        "utilization": 0.0,
        "currentPlan": null
      }
    ],
    "productionTrends": [
      {
        "date": "2024-02-01",
        "planned": 1000,
        "actual": 950,
        "efficiency": 0.95
      },
      {
        "date": "2024-02-02",
        "planned": 1200,
        "actual": 1100,
        "efficiency": 0.92
      }
    ],
    "alerts": [
      {
        "type": "MAINTENANCE_DUE",
        "machineCode": "CUT-001",
        "message": "Maintenance due in 5 days",
        "priority": "MEDIUM"
      }
    ]
  }
}
```

#### **Get Production Reports**

```http
GET /api/v1/production/reports
```

**Query Parameters:**

- `startDate` (required): Report start date
- `endDate` (required): Report end date
- `reportType` (optional): Report type (SUMMARY, DETAILED, EFFICIENCY)
- `companyId` (optional): Filter by company

**Response:**

```json
{
  "success": true,
  "data": {
    "reportId": "uuid",
    "reportType": "SUMMARY",
    "period": {
      "startDate": "2024-02-01",
      "endDate": "2024-02-15"
    },
    "summary": {
      "totalPlans": 25,
      "completedPlans": 20,
      "delayedPlans": 3,
      "cancelledPlans": 2,
      "totalProduction": 15000,
      "targetProduction": 18000,
      "averageEfficiency": 0.83,
      "onTimeDelivery": 0.80
    },
    "machinePerformance": [
      {
        "machineCode": "CUT-001",
        "totalHours": 120,
        "productiveHours": 100,
        "utilization": 0.83,
        "efficiency": 0.85,
        "qualityRate": 0.98
      }
    ],
    "productionTrends": [...],
    "qualityMetrics": {
      "totalInspections": 100,
      "passedInspections": 95,
      "failedInspections": 5,
      "qualityRate": 0.95
    }
  }
}
```

### **Quality Control Integration**

#### **Record Quality Inspection**

```http
POST /api/v1/production/quality/inspections
```

**Request Body:**

```json
{
  "productionPlanId": "uuid",
  "stepId": "uuid",
  "inspectorId": "uuid",
  "inspectionType": "IN_PROCESS",
  "inspectionResults": [
    {
      "parameter": "DIMENSIONS",
      "specification": "100cm ± 0.5cm",
      "actualValue": "100.2cm",
      "status": "PASS",
      "notes": "Within tolerance"
    },
    {
      "parameter": "QUALITY",
      "specification": "No defects",
      "actualValue": "Minor thread issue",
      "status": "FAIL",
      "notes": "Thread quality below standard"
    }
  ],
  "overallStatus": "CONDITIONAL_PASS",
  "notes": "Minor quality issue, acceptable for this order"
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "productionPlanId": "uuid",
    "stepId": "uuid",
    "inspectorId": "uuid",
    "inspectionType": "IN_PROCESS",
    "inspectionResults": [...],
    "overallStatus": "CONDITIONAL_PASS",
    "notes": "Minor quality issue, acceptable for this order",
    "inspectedAt": "2024-02-01T12:00:00Z"
  },
  "message": "Quality inspection recorded successfully"
}
```

## 📊 Data Models

### **ProductionPlan**

```json
{
  "id": "uuid",
  "planNumber": "string",
  "orderId": "uuid",
  "companyId": "uuid",
  "productId": "uuid",
  "productName": "string",
  "quantity": "decimal",
  "unit": "METERS | PIECES | KILOGRAMS",
  "status": "PLANNED | IN_PROGRESS | COMPLETED | DELAYED | CANCELLED",
  "plannedStartDate": "date",
  "plannedEndDate": "date",
  "actualStartDate": "datetime",
  "actualEndDate": "datetime",
  "priority": "LOW | MEDIUM | HIGH | URGENT",
  "productionSteps": ["ProductionStep"],
  "qualityRequirements": "QualityRequirements",
  "notes": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### **ProductionStep**

```json
{
  "id": "uuid",
  "stepName": "string",
  "machineType": "CUTTING_MACHINE | SEWING_MACHINE | FINISHING_MACHINE | PACKAGING_MACHINE",
  "status": "PENDING | ASSIGNED | IN_PROGRESS | COMPLETED | FAILED",
  "estimatedDuration": "integer",
  "actualDuration": "integer",
  "startTime": "datetime",
  "endTime": "datetime",
  "dependencies": ["string"],
  "assignedMachine": "string",
  "assignedOperator": "string"
}
```

### **Machine**

```json
{
  "id": "uuid",
  "machineCode": "string",
  "machineName": "string",
  "machineType": "string",
  "status": "AVAILABLE | RUNNING | MAINTENANCE | BROKEN",
  "capacity": "decimal",
  "currentLoad": "decimal",
  "maintenanceDue": "date",
  "lastMaintenance": "date",
  "location": "string"
}
```

### **QualityInspection**

```json
{
  "id": "uuid",
  "productionPlanId": "uuid",
  "stepId": "uuid",
  "inspectorId": "uuid",
  "inspectionType": "IN_PROCESS | FINAL | RANDOM",
  "inspectionResults": ["InspectionResult"],
  "overallStatus": "PASS | FAIL | CONDITIONAL_PASS",
  "notes": "string",
  "inspectedAt": "datetime"
}
```

### **InspectionResult**

```json
{
  "parameter": "string",
  "specification": "string",
  "actualValue": "string",
  "status": "PASS | FAIL",
  "notes": "string"
}
```

## ⚠️ Error Handling

### **Error Response Format**

```json
{
  "success": false,
  "error": {
    "code": "PRODUCTION_PLAN_NOT_FOUND",
    "message": "Production plan with ID {planId} not found",
    "details": "Additional error details",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### **Common Error Codes**

| Code                        | HTTP Status | Description                          |
| --------------------------- | ----------- | ------------------------------------ |
| `PRODUCTION_PLAN_NOT_FOUND` | 404         | Production plan not found            |
| `MACHINE_NOT_AVAILABLE`     | 409         | Machine not available for scheduling |
| `INVALID_PRODUCTION_STATUS` | 400         | Invalid production status transition |
| `INSUFFICIENT_CAPACITY`     | 400         | Insufficient machine capacity        |
| `QUALITY_INSPECTION_FAILED` | 400         | Quality inspection failed            |
| `UNAUTHORIZED`              | 401         | Authentication required              |
| `FORBIDDEN`                 | 403         | Insufficient permissions             |
| `INTERNAL_ERROR`            | 500         | Internal server error                |

## 🚦 Rate Limiting

- **Rate Limit**: 1000 requests per hour per user
- **Burst Limit**: 100 requests per minute
- **Headers**:
  - `X-RateLimit-Limit`: Request limit per hour
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time in UTC

## 🔄 Event Integration

### **Published Events**

- `ProductionPlanCreated`: When a new production plan is created
- `ProductionPlanStatusChanged`: When production plan status is updated
- `MachineAssigned`: When machine is assigned to production step
- `ProductionStepCompleted`: When production step is completed
- `QualityInspectionCompleted`: When quality inspection is completed

### **Consumed Events**

- `OrderConfirmed`: From Order Service
- `InventoryReserved`: From Inventory Service
- `MachineMaintenanceScheduled`: From Maintenance Service
- `QualityStandardUpdated`: From Quality Control Service

## 📝 Examples

### **Complete Production Planning Flow**

1. **Create Production Plan**

```bash
curl -X POST http://localhost:8096/api/v1/production/plans \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "productId": "550e8400-e29b-41d4-a716-446655440002",
    "productName": "Cotton Fabric",
    "quantity": 1000,
    "unit": "METERS",
    "plannedStartDate": "2024-02-01",
    "plannedEndDate": "2024-02-15",
    "priority": "HIGH",
    "productionSteps": [
      {
        "stepName": "Cutting",
        "machineType": "CUTTING_MACHINE",
        "estimatedDuration": 480,
        "dependencies": []
      },
      {
        "stepName": "Sewing",
        "machineType": "SEWING_MACHINE",
        "estimatedDuration": 720,
        "dependencies": ["Cutting"]
      }
    ],
    "qualityRequirements": {
      "tolerance": 0.02,
      "inspectionRequired": true,
      "certificationRequired": false
    },
    "notes": "High priority order"
  }'
```

2. **Assign Machine to Production Step**

```bash
curl -X PUT http://localhost:8096/api/v1/production/plans/{planId}/steps/{stepId}/assign-machine \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "machineId": "550e8400-e29b-41d4-a716-446655440003",
    "operatorId": "550e8400-e29b-41d4-a716-446655440004",
    "scheduledStartTime": "2024-02-01T08:00:00Z",
    "notes": "Assigned to experienced operator"
  }'
```

3. **Get Production Dashboard**

```bash
curl -X GET http://localhost:8096/api/v1/production/dashboard \
  -H "Authorization: Bearer <token>"
```

## 🔧 Configuration

### **Application Properties**

```yaml
server:
  port: 8096

spring:
  application:
    name: production-service
  datasource:
    url: jdbc:postgresql://localhost:5432/production_db
    username: production_user
    password: production_pass
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
  inventory:
    url: http://localhost:8089
  catalog:
    url: http://localhost:8090
  logistics:
    url: http://localhost:8095
  quality-control:
    url: http://localhost:8104

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

# Production Configuration
production:
  scheduling:
    algorithm: "PRIORITY_BASED"
    buffer-time: 30
  quality:
    auto-inspection: true
    tolerance: 0.02
  monitoring:
    real-time-updates: true
    alert-thresholds:
      efficiency: 0.80
      quality-rate: 0.95
```

---

**Last Updated**: 2024-01-XX  
**Version**: 1.0.0  
**Maintainer**: Production Service Team
