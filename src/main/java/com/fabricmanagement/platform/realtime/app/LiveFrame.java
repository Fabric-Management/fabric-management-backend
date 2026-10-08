package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.platform.realtime.dto.LiveClosedDto;
import com.fabricmanagement.platform.realtime.dto.LiveInvalidatedDto;
import com.fabricmanagement.platform.realtime.dto.LiveReadyDto;

/** One unit the channel writes: three typed data frames and the keepalive comment. */
sealed interface LiveFrame
    permits LiveFrame.Ready, LiveFrame.Invalidated, LiveFrame.Closed, LiveFrame.Heartbeat {

  record Ready(LiveReadyDto data) implements LiveFrame {}

  record Invalidated(LiveInvalidatedDto data) implements LiveFrame {}

  record Closed(LiveClosedDto data) implements LiveFrame {}

  /** An SSE comment: not data, not a change, only proof that the connection is alive. */
  enum Heartbeat implements LiveFrame {
    INSTANCE
  }
}
