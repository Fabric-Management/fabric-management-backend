package com.fabricmanagement.finance.invoice.infra.repository;

import com.fabricmanagement.finance.invoice.domain.CreditNoteApplication;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditNoteApplicationRepository
    extends JpaRepository<CreditNoteApplication, UUID> {

  List<CreditNoteApplication> findByTenantIdAndCreditNoteIdAndIsActiveTrue(
      UUID tenantId, UUID creditNoteId);

  Optional<CreditNoteApplication> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  @Query(
      "SELECT COALESCE(SUM(a.amount.amount), 0) FROM CreditNoteApplication a "
          + "WHERE a.tenantId = :tenantId AND a.creditNoteId = :creditNoteId AND a.isActive = true")
  BigDecimal sumAppliedAmount(
      @Param("tenantId") UUID tenantId, @Param("creditNoteId") UUID creditNoteId);
}
