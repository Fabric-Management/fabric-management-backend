package com.fabricmanagement.common.platform.user.domain.value;

/** Status of a profile update request. */
public enum ProfileUpdateRequestStatus {
  /** Request submitted, awaiting HR/Admin approval. */
  PENDING,

  /** Request approved by HR/Admin, profile updated. */
  APPROVED,

  /** Request rejected by HR/Admin. */
  REJECTED
}
