package com.fabricmanagement.platform.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why the server ends a live stream; the reason is a fixed control value, never user text. */
@Schema(
    name = "LiveCloseReason",
    enumAsRef = true,
    description =
        "Why the server closes the stream. After any reason the client may open a new request;"
            + " the new connection starts with ready and the current state is read again.")
public enum LiveCloseReason {
  /** The access token the stream was opened with has expired: sign in again, then reconnect. */
  AUTH_EXPIRED,
  /** The user may no longer read the resource (permission, scope, user or resource changed). */
  ACCESS_REVOKED,
  /** The connection reached its maximum lifetime or the server is shutting down: reconnect. */
  RECONNECT_REQUIRED,
  /** The server could not check the resource now: reconnect after a pause. */
  TEMPORARILY_UNAVAILABLE,
  /** The client did not read the stream in time; its connection was dropped. */
  SLOW_CONSUMER,
  /** Live events were switched off: keep working without them and try again later. */
  FEATURE_DISABLED
}
