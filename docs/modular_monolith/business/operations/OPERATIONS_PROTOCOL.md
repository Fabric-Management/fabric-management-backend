# 🎯 OPERATIONS DOMAIN PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `operations/`  
**Dependencies:** `common`, `governance`, `production`, `logistics`, `human`  
**Priority:** ⭐ STRATEGIC CORE

---

## 🎯 DOMAIN PURPOSE

Operations Domain orchestrates all operational activities across the fabric management lifecycle.

### **Core Responsibilities**

- ✅ **Job Management** - Create, track, complete jobs
- ✅ **WorkOrder Management** - Executable tasks with stages
- ✅ **Assignment** - Match personnel to tasks
- ✅ **Workflow Automation** - Template-based execution
- ✅ **SLA Monitoring** - Deadline tracking & escalation
- ✅ **Timeline Tracking** - Complete action history

---

## 🏗️ SUB-MODULES

### **operations/job/** - Job & Work Order Management

**Entities:**

- `Job` - High-level work definition
- `WorkOrder` - Executable task
- `WorkOrderStage` - Stage within workflow
- `JobStatus` - Lifecycle state

**Reference:** [JOB_PROTOCOL.md](./job/JOB_PROTOCOL.md)

### **operations/assignment/** - Personnel Assignment

**Entities:**

- `Assignment` - Who does what
- `SkillTag` - Employee skills
- `RoleRequirement` - Required skills

**Reference:** [ASSIGNMENT_PROTOCOL.md](./assignment/ASSIGNMENT_PROTOCOL.md)

### **operations/workflow/** - Workflow Engine

**Entities:**

- `WorkflowTemplate` - Predefined workflows
- `StageDefinition` - Stage definitions
- `SLA` - Deadline policies

**Reference:** [WORKFLOW_PROTOCOL.md](./workflow/WORKFLOW_PROTOCOL.md)

### **operations/tracking/** - Traceability

**Entities:**

- `Timeline` - Activity timeline
- `EventLog` - Action logging

**Reference:** [TRACKING_PROTOCOL.md](./tracking/TRACKING_PROTOCOL.md)

---

## 🔗 CROSS-DOMAIN INTEGRATION

```
Operations → Production (Facade)
  └─ Get material info for job context

Operations → Logistics (Facade)
  └─ Check inventory before job creation

Operations → Human (Facade)
  └─ Get available personnel for assignment

Operations → Finance (Event)
  └─ Publish JobCompletedEvent for costing

Operations → Governance (Policy)
  └─ Check authorization for stage transitions
```

---

## 📊 WORKFLOW EXAMPLE

```java
// Create job from template
CreateJobCommand cmd = CreateJobCommand.builder()
    .templateCode("WEAVING_STANDARD")
    .orderId(orderId)
    .targetQuantity(1000)
    .targetUnit("METER")
    .build();

Job job = jobService.createJob(cmd);
// Job created with 5 stages from template
// Stages: Preparation → Setup → Weaving → QC → Transfer

// Assign personnel to stage
AssignWorkerCommand assignCmd = AssignWorkerCommand.builder()
    .jobId(job.getId())
    .stageId(stage1.getId())
    .workerId(employee.getId())
    .build();

assignmentService.assignWorker(assignCmd);
// Worker assigned, capacity updated, notification sent

// Advance to next stage
AdvanceStageCommand advanceCmd = AdvanceStageCommand.builder()
    .jobId(job.getId())
    .currentStageId(stage1.getId())
    .completionNotes("Yarn prepared successfully")
    .build();

jobService.advanceStage(advanceCmd);
// Stage completed, next stage started, timeline logged
```

---

**Last Updated:** 2025-10-24  
**Maintained By:** Fabric Management Team
