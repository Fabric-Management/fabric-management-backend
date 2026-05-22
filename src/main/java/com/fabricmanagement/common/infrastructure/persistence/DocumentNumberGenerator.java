package com.fabricmanagement.common.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DocumentNumberGenerator {

  @PersistenceContext private EntityManager em;

  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

  /**
   * Generates next document number atomically within the caller's transaction. Single round-trip,
   * implicit row lock, upsert on first call per prefix.
   *
   * <p>If the enclosing transaction rolls back, the sequence also rolls back (gap-free guarantee).
   *
   * @param tenantId current tenant
   * @param documentType e.g. "SALES_ORDER"
   * @param prefixCode e.g. "SO"
   * @param date date for prefix (determines reset boundary)
   * @param padWidth zero-padding width (e.g. 5 → 00001)
   * @return e.g. "SO-20260521-00003"
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public String generate(
      UUID tenantId, String documentType, String prefixCode, LocalDate date, int padWidth) {
    if (padWidth < 1) {
      throw new IllegalArgumentException("padWidth must be >= 1, got: " + padWidth);
    }
    if (documentType == null || documentType.trim().isEmpty()) {
      throw new IllegalArgumentException("documentType cannot be null or empty");
    }
    if (prefixCode == null || prefixCode.trim().isEmpty()) {
      throw new IllegalArgumentException("prefixCode cannot be null or empty");
    }

    String prefix = prefixCode + "-" + date.format(YYYYMMDD) + "-";

    // Note: next_val is semantically the "last used value" or "current value".
    // The first INSERT gives 1. Subsequent UPDATE gives document_sequence.next_val + 1.
    long seq =
        ((Number)
                em.createNativeQuery(
                        """
            INSERT INTO common_infrastructure.document_sequence
                   (tenant_id, document_type, prefix, next_val, created_at, updated_at)
            VALUES (:tenantId, :documentType, :prefix, 1, NOW(), NOW())
            ON CONFLICT (tenant_id, document_type, prefix)
            DO UPDATE SET next_val = document_sequence.next_val + 1,
                          updated_at = NOW()
            RETURNING next_val
            """)
                    .setParameter("tenantId", tenantId)
                    .setParameter("documentType", documentType)
                    .setParameter("prefix", prefix)
                    .getSingleResult())
            .longValue();

    String number = prefix + String.format("%0" + padWidth + "d", seq);
    log.debug("Generated document number: {} (type={}, tenant={})", number, documentType, tenantId);
    return number;
  }
}
