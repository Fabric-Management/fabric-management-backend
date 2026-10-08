package com.fabricmanagement.platform.realtime.domain.exception;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;

/**
 * A live stream that could not be opened. It is raised before any stream byte is written, so it is
 * answered on the normal HTTP error path; messages never say whether a resource exists.
 */
public class LiveStreamRejectedException extends PlatformDomainException {

  private final Rejection rejection;

  public LiveStreamRejectedException(Rejection rejection) {
    super(rejection.message(), rejection.code(), rejection.status());
    this.rejection = rejection;
  }

  public LiveStreamRejectedException(Rejection rejection, String message) {
    super(message, rejection.code(), rejection.status());
    this.rejection = rejection;
  }

  public Rejection rejection() {
    return rejection;
  }

  /** Why a stream was refused, with its HTTP status and whether the client may simply retry. */
  public enum Rejection {
    UNAUTHENTICATED(401, "LIVE_STREAM_UNAUTHENTICATED", "A valid access token is required", false),
    FORBIDDEN(403, "LIVE_STREAM_FORBIDDEN", "You may not subscribe to these live events", false),
    NOT_FOUND(404, "LIVE_STREAM_NOT_FOUND", "The live resource was not found", false),
    CAPACITY(429, "LIVE_STREAM_CAPACITY", "Too many live connections; try again shortly", true),
    FEATURE_DISABLED(
        503, "LIVE_STREAM_DISABLED", "Live events are switched off; try again later", true),
    UNAVAILABLE(
        503,
        "LIVE_STREAM_UNAVAILABLE",
        "Live events are temporarily unavailable; try again shortly",
        true);

    private final int status;
    private final String code;
    private final String message;
    private final boolean retryLater;

    Rejection(int status, String code, String message, boolean retryLater) {
      this.status = status;
      this.code = code;
      this.message = message;
      this.retryLater = retryLater;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }

    public String message() {
      return message;
    }

    /** Whether the answer carries {@code Retry-After}: the refusal is temporary. */
    public boolean retryLater() {
      return retryLater;
    }
  }
}
