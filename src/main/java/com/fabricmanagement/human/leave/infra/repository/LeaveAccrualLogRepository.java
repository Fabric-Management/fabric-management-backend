package com.fabricmanagement.human.leave.infra.repository;

import com.fabricmanagement.human.leave.domain.LeaveAccrualLog;
import com.fabricmanagement.human.leave.domain.LeaveType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveAccrualLogRepository extends JpaRepository<LeaveAccrualLog, UUID> {

  @Query(
      """
        select log from LeaveAccrualLog log
        where log.tenantId = :tenantId
          and log.employeeId = :employeeId
          and log.leaveType = :leaveType
          and log.occurredAt >= :since
        order by log.occurredAt desc
        """)
  List<LeaveAccrualLog> findRecentLogs(
      @Param("tenantId") UUID tenantId,
      @Param("employeeId") UUID employeeId,
      @Param("leaveType") LeaveType leaveType,
      @Param("since") Instant since);
}
