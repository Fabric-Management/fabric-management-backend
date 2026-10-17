package com.fabricmanagement.common.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The verification state of a record used to deliver goods: the status, who verified it and when
 * (CONSIGNEE-1 C5). One value in the three tables that carry it (trading partner, address card,
 * order delivery), under the same column names.
 *
 * <p>Immutable: a change of state is a new value. Rows that predate the rule are {@link
 * #predating()}: verified, with no verifier and no time.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification {

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 20)
  private DeliveryVerificationStatus status = DeliveryVerificationStatus.VERIFIED;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  private Verification(DeliveryVerificationStatus status, UUID verifiedBy, Instant verifiedAt) {
    this.status = status;
    this.verifiedBy = verifiedBy;
    this.verifiedAt = verifiedAt;
  }

  /** Entered by someone who may not confirm it. */
  public static Verification unverified() {
    return new Verification(DeliveryVerificationStatus.UNVERIFIED, null, null);
  }

  /** Confirmed by {@code verifier} at {@code at}. */
  public static Verification verifiedBy(UUID verifier, Instant at) {
    return new Verification(DeliveryVerificationStatus.VERIFIED, verifier, at);
  }

  /** A record that predates the rule, or is made by the system: verified, nobody to name. */
  public static Verification predating() {
    return new Verification(DeliveryVerificationStatus.VERIFIED, null, null);
  }

  public boolean isVerified() {
    return status.isVerified();
  }
}
