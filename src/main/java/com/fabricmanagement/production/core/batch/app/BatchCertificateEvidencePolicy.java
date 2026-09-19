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

  /**
   * Supplies the lot-owned certificate record set together with its completeness declaration.
   *
   * <p>The conservative default is deliberately non-authoritative: record absence, expiry or
   * incompatible coverage cannot prove exclusion until the evidence owner explicitly declares that
   * the set is complete for the evaluated lot. A future production policy may obtain that
   * declaration from its evidence-owner record; SALES-REQ-1 registers no production policy.
   */
  default EvidenceSet evidenceSet(
      Batch batch,
      String scheme,
      BatchCertificateKind certificateKind,
      List<BatchCertification> records) {
    return new EvidenceSet(records, false);
  }

  Assessment assess(
      Batch batch,
      String scheme,
      BatchCertificateKind certificateKind,
      EvidenceSet evidenceSet,
      LocalDate evaluationDate);

  enum Outcome {
    MATCH,
    EXCLUDED,
    UNKNOWN
  }

  record EvidenceSet(List<BatchCertification> records, boolean authoritative) {
    public EvidenceSet {
      records = records == null ? List.of() : List.copyOf(records);
    }
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
