package com.fabricmanagement.platform.realtime.app;

import java.io.IOException;
import java.time.Instant;

/**
 * The transport of one connection (CEDIT-05 R1). No method ever blocks the caller, and ending a
 * stream never leaves the container waiting on the client: a frame the client cannot take yet waits
 * in a small, coalescing buffer, the transport reports since when it has been unable to write, and
 * the service aborts a transport that stays stalled longer than the send timeout, whether the
 * connection is still open or already closing.
 */
interface LiveChannel {

  /** Hands one frame to the transport and writes what the client can take now; never blocks. */
  void send(LiveFrame frame) throws IOException;

  /** Since when the container holds bytes the client has not taken; null while it keeps up. */
  Instant stalledSince();

  /**
   * Ends the stream normally: frames still waiting are written first, and the response is completed
   * only once the container holds nothing unsent, so completing never waits on the client. A client
   * that does not take the rest keeps the transport stalled until the service aborts it. Safe to
   * call again.
   */
  void complete();

  /**
   * Ends the stream now: waiting frames are dropped and, when the container still holds unsent
   * bytes, the connection is closed without flushing them. Never blocks. Safe to call again.
   */
  void abort();

  /** Registers the transport's notifications; an end that already happened is reported now. */
  void listen(Listener listener);

  interface Listener {

    /** Final: the container finished the response (normally, or after the client went away). */
    void completed();

    /** Final: writing failed or the container reported an error on the stream. */
    void failed(Throwable failure);

    /**
     * Not final: the container's async timeout fired, a backstop the scheduler normally never
     * reaches. The transport ends itself; {@link #completed()} or {@link #failed} follows.
     */
    void timedOut();
  }
}
