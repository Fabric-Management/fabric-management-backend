package com.fabricmanagement.platform.realtime.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Granting the leases would exceed a technical bound (CEDIT-07 §3.1): too many held by this edit
 * session or on this record. Nothing was granted; the client releases leases it no longer needs.
 */
public class LiveEditLeaseLimitException extends DomainException {

  public static final String CODE = "EDIT_LEASE_LIMIT_REACHED";

  public LiveEditLeaseLimitException(String message) {
    super(message, CODE, 409);
  }
}
