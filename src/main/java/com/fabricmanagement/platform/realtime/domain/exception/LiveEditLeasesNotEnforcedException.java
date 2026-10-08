package com.fabricmanagement.platform.realtime.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Field leases are not enforced for this kind of record in this tenant yet (CEDIT-07 §3.4): none is
 * granted, and the safe save works without one, as before.
 */
public class LiveEditLeasesNotEnforcedException extends DomainException {

  public static final String CODE = "EDIT_LEASES_NOT_ENFORCED";

  public LiveEditLeasesNotEnforcedException() {
    super("Field leases are not enforced here; edit without them", CODE, 409);
  }
}
