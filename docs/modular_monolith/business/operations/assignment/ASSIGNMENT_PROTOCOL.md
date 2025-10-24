# 👥 ASSIGNMENT MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `operations/assignment`  
**Dependencies:** `common`, `human/employee`

---

## 🎯 MODULE PURPOSE

Assignment module matches personnel to operational tasks based on **skills**, **capacity**, and **availability**.

### **Core Responsibilities**

- ✅ **Skill Matching** - Match job requirements to employee skills
- ✅ **Capacity Management** - Track workload & availability
- ✅ **Assignment Validation** - Policy checks before assignment
- ✅ **Workload Balancing** - Distribute tasks evenly
- ✅ **Performance Tracking** - Track assignee productivity

---

## 📋 DOMAIN MODELS

### **Assignment Entity**

```java
@Entity
@Table(name = "ops_assignment", schema = "operations")
public class Assignment extends BaseEntity {

    private UUID workOrderId; // What to do
    private UUID employeeId;  // Who does it

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status; // PENDING, ACCEPTED, IN_PROGRESS, COMPLETED

    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> requiredSkills;

    private Integer estimatedHours;
    private Integer actualHours;
}
```

### **Assignment Matching Algorithm**

```
1. Get WorkOrder skill requirements
2. Find employees with matching skills
3. Check employee capacity (max 40h/week)
4. Check department match
5. Policy validation (can assign?)
6. Rank by: skill match + capacity + past performance
7. Assign to best match
```

---

**Last Updated:** 2025-10-24
