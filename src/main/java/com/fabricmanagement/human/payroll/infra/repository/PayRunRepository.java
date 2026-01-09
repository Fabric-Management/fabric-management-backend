package com.fabricmanagement.human.payroll.infra.repository;

import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

  @Query(
      """
        select pr from PayRun pr
        where pr.payPeriod = :payPeriod
        order by pr.runNumber desc
        """)
  List<PayRun> findByPayPeriod(@Param("payPeriod") PayPeriod payPeriod);

  @Query(
      """
        select pr from PayRun pr
        where pr.tenantId = :tenantId
          and pr.status = :status
        order by pr.startedAt desc
        """)
  List<PayRun> findByStatus(@Param("tenantId") UUID tenantId, @Param("status") PayRunStatus status);

  Optional<PayRun> findFirstByTenantIdAndPayPeriodOrderByRunNumberDesc(
      UUID tenantId, PayPeriod payPeriod);

  @Query(
      """
        select pr from PayRun pr
        join fetch pr.payPeriod pp
        where pr.id = :id
        """)
  Optional<PayRun> findWithPeriod(@Param("id") UUID id);
}
