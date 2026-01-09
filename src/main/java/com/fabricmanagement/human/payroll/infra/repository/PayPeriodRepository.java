package com.fabricmanagement.human.payroll.infra.repository;

import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayPeriodStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayPeriodRepository extends JpaRepository<PayPeriod, UUID> {

  @Query(
      """
        select pp from PayPeriod pp
        where pp.tenantId = :tenantId
          and pp.periodCode = :periodCode
        """)
  Optional<PayPeriod> findByCode(
      @Param("tenantId") UUID tenantId, @Param("periodCode") String periodCode);

  @Query(
      """
        select pp from PayPeriod pp
        where pp.tenantId = :tenantId
          and pp.countryCode = :countryCode
          and pp.status = :status
        order by pp.startDate desc
        """)
  List<PayPeriod> findByStatus(
      @Param("tenantId") UUID tenantId,
      @Param("countryCode") String countryCode,
      @Param("status") PayPeriodStatus status);

  @Query(
      """
        select pp from PayPeriod pp
        where pp.tenantId = :tenantId
          and pp.countryCode = :countryCode
          and pp.startDate <= :date
          and pp.endDate >= :date
        """)
  Optional<PayPeriod> findPeriodCoveringDate(
      @Param("tenantId") UUID tenantId,
      @Param("countryCode") String countryCode,
      @Param("date") LocalDate date);
}
