# 📊 TRACKING MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `operations/tracking`  
**Dependencies:** `common`, `operations/job`

---

## 🎯 MODULE PURPOSE

Tracking module provides **complete traceability** and **timeline visualization** for all operational activities.

### **Core Responsibilities**

- ✅ **Timeline Generation** - Chronological activity log
- ✅ **Event Recording** - Capture all state changes
- ✅ **Traceability** - Who did what, when, why
- ✅ **Audit Integration** - Link to platform audit
- ✅ **Visualization** - Kanban, Gantt, Timeline views

---

## 📋 DOMAIN MODELS

### **Timeline Entity**

```java
@Entity
@Table(name = "ops_timeline", schema = "operations")
public class Timeline extends BaseEntity {

    private UUID jobId;
    private UUID workOrderId;

    private String action; // "CREATED", "ASSIGNED", "STARTED", "COMPLETED"
    private String description;

    private UUID performedBy;
    private Instant performedAt;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> eventData;
}
```

### **Timeline Query**

```java
GET /api/operations/jobs/{jobId}/timeline

Response:
[
  { "action": "CREATED", "by": "admin@acme.com", "at": "2025-10-24T08:00:00Z" },
  { "action": "ASSIGNED", "by": "manager@acme.com", "at": "2025-10-24T09:00:00Z" },
  { "action": "STARTED", "by": "worker@acme.com", "at": "2025-10-24T10:00:00Z" },
  { "action": "STAGE_ADVANCED", "by": "worker@acme.com", "at": "2025-10-24T14:00:00Z" }
]
```

---

**Last Updated:** 2025-10-24
