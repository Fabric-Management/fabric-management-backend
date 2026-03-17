package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.AssigneeRole;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderAssignee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderAssigneeRepository extends JpaRepository<WorkOrderAssignee, UUID> {
  List<WorkOrderAssignee> findByWorkOrderIdAndIsActiveTrue(UUID workOrderId);

  boolean existsByWorkOrderIdAndRoleAndIsActiveTrue(UUID workOrderId, AssigneeRole role);
}
