package com.fabricmanagement.human.payroll.infra.repository;

import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunPayout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayRunPayoutRepository extends JpaRepository<PayRunPayout, UUID> {

  @Query(
      """
        select p from PayRunPayout p
        where p.payRun = :payRun
        """)
  List<PayRunPayout> findByPayRun(@Param("payRun") PayRun payRun);

  @Query(
      """
        select p from PayRunPayout p
        where p.payRun = :payRun
          and p.employeeId = :employeeId
        """)
  Optional<PayRunPayout> findByPayRunAndEmployee(
      @Param("payRun") PayRun payRun, @Param("employeeId") UUID employeeId);
}
