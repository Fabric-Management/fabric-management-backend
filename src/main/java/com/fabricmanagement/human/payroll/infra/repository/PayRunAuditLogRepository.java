package com.fabricmanagement.human.payroll.infra.repository;

import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayRunAuditLogRepository extends JpaRepository<PayRunAuditLog, UUID> {

  @Query(
      """
        select audit from PayRunAuditLog audit
        where audit.payRun = :payRun
        order by audit.occurredAt desc
        """)
  List<PayRunAuditLog> findByPayRun(@Param("payRun") PayRun payRun);
}
