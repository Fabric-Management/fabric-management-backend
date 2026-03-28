package com.fabricmanagement.production.execution.batch.domain.exception;

import java.time.LocalDate;

/**
 * Thrown when a batch with organic indicator (e.g. fiber_organic_cert_no) has no valid GOTS
 * certification at reserve or start-production time. Used when {@code
 * batch.certification.enforce-on-reserve} is enabled.
 *
 * <p>HTTP 409 Conflict — client must update certification before reserving or starting production.
 */
public class BatchCertificationExpiredException extends BatchDomainException {

  private final String batchCode;
  private final String certCode;
  private final LocalDate validUntil;

  public BatchCertificationExpiredException(String batchCode, String certCode) {
    this(batchCode, certCode, null);
  }

  public BatchCertificationExpiredException(
      String batchCode, String certCode, LocalDate validUntil) {
    super(buildMessage(batchCode, certCode, validUntil), "BATCH_CERTIFICATION_EXPIRED", 409);
    this.batchCode = batchCode;
    this.certCode = certCode;
    this.validUntil = validUntil;
    withDetail("batchCode", batchCode);
    withDetail("certCode", certCode);
    if (validUntil != null) {
      withDetail("validUntil", validUntil.toString());
    }
  }

  private static String buildMessage(String batchCode, String certCode, LocalDate validUntil) {
    StringBuilder sb =
        new StringBuilder()
            .append("Batch [")
            .append(batchCode)
            .append("] has no valid GOTS certification. ");
    if (validUntil != null) {
      sb.append(certCode).append(" expired on ").append(validUntil).append(". ");
    } else {
      sb.append(certCode).append(" missing or expired. ");
    }
    sb.append("Update certification before reserving.");
    return sb.toString();
  }

  public String getBatchCode() {
    return batchCode;
  }

  public String getCertCode() {
    return certCode;
  }

  public LocalDate getValidUntil() {
    return validUntil;
  }
}
