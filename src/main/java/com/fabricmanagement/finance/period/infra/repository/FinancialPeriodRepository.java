package com.fabricmanagement.finance.period.infra.repository;

import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import com.fabricmanagement.finance.period.domain.FinancialPeriodStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID> {

  Optional<FinancialPeriod> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<FinancialPeriod> findWithLockByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FinancialPeriod> findByTenantIdAndPeriodYearAndPeriodMonth(
      UUID tenantId, int periodYear, int periodMonth);

  Optional<FinancialPeriod> findTopByTenantIdAndStatusOrderByEndDateDesc(
      UUID tenantId, FinancialPeriodStatus status);

  Optional<FinancialPeriod> findTopByTenantIdAndStatusAndEndDateBeforeOrderByEndDateDesc(
      UUID tenantId, FinancialPeriodStatus status, LocalDate endDate);

  boolean existsByTenantIdAndStatusAndEndDateAfter(
      UUID tenantId, FinancialPeriodStatus status, LocalDate endDate);
}
