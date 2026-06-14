package com.fabricmanagement.finance.common.infra;

import com.fabricmanagement.finance.common.domain.DocumentNumberCounter;
import com.fabricmanagement.finance.common.domain.DocumentNumberCounterKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentNumberCounterRepository
    extends JpaRepository<DocumentNumberCounter, DocumentNumberCounterKey> {

  @Modifying
  @Query(
      value =
          "INSERT INTO finance.document_number_counter (tenant_id, series, year, last_value) "
              + "VALUES (:tenantId, :series, :year, 0) "
              + "ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void ensureCounterExists(
      @Param("tenantId") UUID tenantId, @Param("series") String series, @Param("year") int year);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT c FROM DocumentNumberCounter c "
          + "WHERE c.tenantId = :tenantId AND c.series = :series AND c.year = :year")
  Optional<DocumentNumberCounter> findForUpdate(
      @Param("tenantId") UUID tenantId, @Param("series") String series, @Param("year") int year);
}
