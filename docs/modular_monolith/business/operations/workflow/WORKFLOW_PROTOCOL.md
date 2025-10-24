# ⚙️ WORKFLOW MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `operations/workflow`  
**Dependencies:** `common`, `operations/job`

---

## 🎯 MODULE PURPOSE

Workflow module provides **template-based job creation** and **automated stage management**.

### **Core Responsibilities**

- ✅ **Template Management** - Define reusable workflows
- ✅ **Stage Automation** - Auto-create stages from template
- ✅ **SLA Management** - Deadline tracking per stage
- ✅ **Transition Rules** - Validate stage advancement
- ✅ **Performance Monitoring** - Track SLA compliance

---

## 📋 DOMAIN MODELS

### **WorkflowTemplate Entity**

```java
@Entity
@Table(name = "ops_workflow_template", schema = "operations")
public class WorkflowTemplate extends BaseEntity {

    private String templateCode; // "WEAVING_STANDARD"
    private String templateName;
    private String description;

    @Enumerated(EnumType.STRING)
    private WorkflowCategory category; // PRODUCTION, LOGISTICS, QUALITY

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<StageDefinition> stages;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> defaultSLA;
}
```

### **StageDefinition (Value Object)**

```json
{
  "sequence": 1,
  "stageName": "Yarn Preparation",
  "description": "Prepare yarn for weaving",
  "estimatedDuration": "PT4H",
  "requiredSkills": ["yarn_handling", "quality_check"],
  "requiredDepartment": "production",
  "slaDeadline": "PT24H",
  "escalationPolicy": "MANAGER_NOTIFY"
}
```

---

## 🔄 WORKFLOW EXECUTION

```
1. Select Template
   └─ WorkflowTemplate.findByCode("WEAVING_STANDARD")

2. Generate Job
   ├─ Create Job entity
   └─ Auto-create WorkOrders from template stages

3. Set SLA per Stage
   └─ Apply default SLA + custom overrides

4. Monitor Execution
   ├─ SLAMonitor checks deadlines every 5 min
   ├─ Send notifications on approaching deadline
   └─ Escalate on breach
```

---

**Last Updated:** 2025-10-24
