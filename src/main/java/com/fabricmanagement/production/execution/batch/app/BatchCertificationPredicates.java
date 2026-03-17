package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import java.time.LocalDate;

/**
 * Package-private predicates for batch certification validity and type checks. Shared by
 * BatchService and BatchCertificationService to avoid duplicated logic (DRY). Not a Spring bean —
 * stateless utility only.
 */
final class BatchCertificationPredicates {

  private BatchCertificationPredicates() {}

  static boolean isGotsCertification(BatchCertification cert) {
    if (cert.getCertification() == null) {
      return false;
    }
    return "GOTS".equals(cert.getCertification().getCertificationCode());
  }

  static boolean isCertificationStillValid(BatchCertification cert, LocalDate today) {
    return cert.getValidUntil() == null || !cert.getValidUntil().isBefore(today);
  }
}
