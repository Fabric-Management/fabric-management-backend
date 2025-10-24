# 📋 JOB MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `operations/job`  
**Dependencies:** `common`, `governance`

---

## 🎯 MODULE PURPOSE

Job module manages high-level work definitions and executable work orders.

### **Core Responsibilities**

- ✅ **Job CRUD** - Create, read, update jobs
- ✅ **WorkOrder Management** - Track executable tasks
- ✅ **Stage Tracking** - Monitor stage progression
- ✅ **Status Management** - Lifecycle state transitions
- ✅ **Timeline** - Complete action history

---

## 📋 DOMAIN MODELS

### **Job Entity**

```java
@Entity
@Table(name = "ops_job", schema = "operations")
public class Job extends BaseEntity {

    private String jobName;
    private String jobCode; // Auto-generated

    @Enumerated(EnumType.STRING)
    private JobType jobType; // PRODUCTION, LOGISTICS, PROCUREMENT

    @Enumerated(EnumType.STRING)
    private JobStatus status; // CREATED, ASSIGNED, IN_PROGRESS, COMPLETED

    private UUID orderId; // Link to order (optional)
    private UUID templateId; // Workflow template used

    private Instant startDate;
    private Instant targetCompletionDate;
    private Instant actualCompletionDate;

    // Relationships
    @OneToMany(mappedBy = "job")
    private List<WorkOrder> workOrders;
}
```

### **WorkOrder Entity**

```java
@Entity
@Table(name = "ops_work_order", schema = "operations")
public class WorkOrder extends BaseEntity {

    private UUID jobId; // Parent job

    private String workOrderName;
    private Integer stageSequence; // Order in workflow

    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status;

    private UUID assignedTo; // Employee or team

    private Instant startDate;
    private Instant deadline;
    private Instant completedAt;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata; // Stage-specific data
}
```

---

## 🔗 ENDPOINTS

| Endpoint                             | Method | Purpose       | Policy                    |
| ------------------------------------ | ------ | ------------- | ------------------------- |
| `/api/operations/jobs`               | GET    | List jobs     | `operations.job.read`     |
| `/api/operations/jobs/{id}`          | GET    | Get job       | `operations.job.read`     |
| `/api/operations/jobs`               | POST   | Create job    | `operations.job.create`   |
| `/api/operations/jobs/{id}/advance`  | POST   | Advance stage | `operations.job.update`   |
| `/api/operations/jobs/{id}/complete` | POST   | Complete job  | `operations.job.complete` |

---

**Last Updated:** 2025-10-24
