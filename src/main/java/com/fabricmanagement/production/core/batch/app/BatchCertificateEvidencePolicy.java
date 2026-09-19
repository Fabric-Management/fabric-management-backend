package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCertificateKind;
import com.fabricmanagement.production.core.batch.domain.BatchCertification;
import java.time.LocalDate;
import java.util.List;

/**
 * Evidence-owner contract for one accepted certificate scheme and document kind.
 *
 * <p>An absent policy means the document kind cannot produce MATCH or EXCLUDED. In particular,
 * SALES-REQ-1 registers no GOTS policy; CERT-CHAIN-1 owns that future decision.
 */
public interface BatchCertificateEvidencePolicy {

  String policyId();

  String policyVersion();

  boolean supports(String scheme, BatchCertificateKind certificateKind);

  Assessment assess(
      Batch batch,
      String scheme,
      BatchCertificateKind certificateKind,
      List<BatchCertification> records,
      LocalDate evaluationDate);

  enum Outcome {
    MATCH,
    EXCLUDED,
    UNKNOWN
  }

  record Assessment(Outcome outcome, List<BatchCertification> evidence) {
    public Assessment {
      if (outcome == null) {
        throw new IllegalArgumentException("Certificate assessment outcome is required");
      }
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
  }
}
