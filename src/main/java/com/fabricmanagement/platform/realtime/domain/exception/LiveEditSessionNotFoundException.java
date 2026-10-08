package com.fabricmanagement.platform.realtime.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * The edit session is not an open, unexpired session of this user on this resource (CEDIT-06 §3).
 * The client opens a new session; nothing about other users' sessions is revealed.
 */
public class LiveEditSessionNotFoundException extends DomainException {

  public static final String CODE = "EDIT_SESSION_NOT_FOUND";

  public LiveEditSessionNotFoundException() {
    super("This edit session has ended; open a new one", CODE, 404);
  }
}
