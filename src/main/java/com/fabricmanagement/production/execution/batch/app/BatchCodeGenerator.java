package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generates unique batch codes for split operations.
 *
 * <p>Convention: parent code + {@code -P1}, {@code -P2}, … suffix. Example:
 *
 * <pre>
 * Source:       FIBER-2025-001
 * First split:  FIBER-2025-001-P1
 * Second split: FIBER-2025-001-P2
 * </pre>
 *
 * <p>Counts existing children of the parent batch (via {@code parent_batch_id}) to determine the
 * next sequence number. Codes are unique per tenant (enforced by DB constraint {@code
 * uq_batch_tenant_code}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchCodeGenerator {

  private final BatchRepository batchRepository;

  /**
   * Generate the next split batch code for a given parent batch.
   *
   * <p>Uses {@link TenantContext} for tenant. Counts existing children via {@code parent_batch_id}
   * to produce the next P-number.
   *
   * @param parentBatchId ID of the parent batch (used to count existing children)
   * @param parentCode batch code of the parent (e.g. {@code FIBER-2025-001})
   * @return unique split code (e.g. {@code FIBER-2025-001-P1}, {@code FIBER-2025-001-P2})
   */
  public String generateSplitCode(UUID parentBatchId, String parentCode) {
    UUID tenantId = TenantContext.requireTenantId();
    long existingCount = batchRepository.countByTenantIdAndParentBatchId(tenantId, parentBatchId);
    int nextNum = (int) existingCount + 1;
    String code = parentCode + "-P" + nextNum;

    log.debug(
        "Generated split code: parentCode={}, parentBatchId={}, existingChildren={} → {}",
        parentCode,
        parentBatchId,
        existingCount,
        code);

    return code;
  }
}
