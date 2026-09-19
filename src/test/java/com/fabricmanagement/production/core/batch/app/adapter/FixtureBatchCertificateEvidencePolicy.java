package com.fabricmanagement.production.core.batch.app.adapter;

import com.fabricmanagement.production.core.batch.app.BatchCertificateEvidencePolicy;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCertificateKind;
import com.fabricmanagement.production.core.batch.domain.BatchCertification;
import com.fabricmanagement.production.core.batch.domain.BatchCertificationScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Accepted certificate semantics for integration fixtures only; never registered in production. */
final class FixtureBatchCertificateEvidencePolicy implements BatchCertificateEvidencePolicy {

  static final String SCHEME = "FIXTURE_CERT";

  private final Set<UUID> authoritativeLots = ConcurrentHashMap.newKeySet();

  void declareAuthoritative(Batch batch) {
    authoritativeLots.add(batch.getId());
  }

  void clearDeclarations() {
    authoritativeLots.clear();
  }

  @Override
  public String policyId() {
    return "SALES_REQ_1_ACCEPTED_FIXTURE";
  }

  @Override
  public String policyVersion() {
    return "1";
  }

  @Override
  public boolean supports(String scheme, BatchCertificateKind certificateKind) {
    return SCHEME.equals(scheme)
        && Set.of(BatchCertificateKind.SCOPE, BatchCertificateKind.TRANSACTION)
            .contains(certificateKind);
  }

  @Override
  public EvidenceSet evidenceSet(
      Batch batch,
      String scheme,
      BatchCertificateKind certificateKind,
      List<BatchCertification> records) {
    return new EvidenceSet(records, authoritativeLots.contains(batch.getId()));
  }

  @Override
  public Assessment assess(
      Batch batch,
      String scheme,
      BatchCertificateKind certificateKind,
      EvidenceSet evidenceSet,
      LocalDate evaluationDate) {
    List<BatchCertification> schemeRecords =
        evidenceSet.records().stream()
            .filter(row -> scheme.equals(row.getCertification().getCertificationCode()))
            .toList();
    if (schemeRecords.isEmpty()) {
      return new Assessment(Outcome.UNKNOWN, List.of());
    }

    BatchCertificationScope requiredCoverage = requiredCoverage(certificateKind);
    List<BatchCertification> matchingKindAndCoverage =
        schemeRecords.stream()
            .filter(row -> row.getCertificateKind() == certificateKind)
            .filter(row -> row.getScope() == requiredCoverage)
            .toList();
    List<BatchCertification> valid =
        matchingKindAndCoverage.stream()
            .filter(
                row ->
                    (row.getValidFrom() == null || !row.getValidFrom().isAfter(evaluationDate))
                        && (row.getValidUntil() == null
                            || !row.getValidUntil().isBefore(evaluationDate)))
            .toList();
    if (!valid.isEmpty()) {
      return new Assessment(Outcome.MATCH, valid);
    }

    if (schemeRecords.stream().anyMatch(row -> row.getCertificateKind() == null)) {
      return new Assessment(Outcome.UNKNOWN, schemeRecords);
    }
    if (!evidenceSet.authoritative()) {
      return new Assessment(Outcome.UNKNOWN, schemeRecords);
    }
    return new Assessment(Outcome.EXCLUDED, schemeRecords);
  }

  private BatchCertificationScope requiredCoverage(BatchCertificateKind certificateKind) {
    return switch (certificateKind) {
      case SCOPE -> BatchCertificationScope.FACILITY;
      case TRANSACTION -> BatchCertificationScope.BATCH;
    };
  }
}
