package com.fabricmanagement.sales.salesorder.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * A client that opens a live stream on a plain socket with a tiny receive buffer, reads only the
 * response head and then stops reading (CEDIT-05 L18, R1). The server's writes back up for real. It
 * can leave in two ways: a normal close, or a reset that drops the connection at once.
 */
final class RawSseClient implements AutoCloseable {

  private final Socket socket;
  private final InputStream in;
  private final int status;

  private RawSseClient(Socket socket, InputStream in, int status) {
    this.socket = socket;
    this.in = in;
    this.status = status;
  }

  static RawSseClient connect(int port, UUID orderId, String token) throws IOException {
    return connect(port, "/api/v1/sales/orders/" + orderId + "/live-events", token);
  }

  static RawSseClient connect(int port, String path, String token) throws IOException {
    Socket socket = new Socket();
    socket.setReceiveBufferSize(1024);
    socket.connect(new InetSocketAddress("localhost", port), 5000);
    socket.setSoTimeout(15000);
    OutputStream out = socket.getOutputStream();
    String request =
        "GET "
            + path
            + " HTTP/1.1\r\n"
            + "Host: localhost:"
            + port
            + "\r\n"
            + "Authorization: Bearer "
            + token
            + "\r\n"
            + "Accept: text/event-stream\r\n"
            + "Connection: close\r\n\r\n";
    out.write(request.getBytes(StandardCharsets.US_ASCII));
    out.flush();
    InputStream in = socket.getInputStream();
    String head = readHead(in);
    int status = Integer.parseInt(head.substring(9, 12));
    return new RawSseClient(socket, in, status);
  }

  int status() {
    return status;
  }

  /**
   * Resumes reading until the server closes the connection. True when it closed (or reset) within
   * the bound; false when it was still open.
   */
  boolean readsToEnd(Duration bound) throws IOException {
    socket.setSoTimeout((int) bound.toMillis());
    byte[] buffer = new byte[8192];
    long deadline = System.nanoTime() + bound.toNanos();
    try {
      while (in.read(buffer) != -1) {
        if (System.nanoTime() > deadline) {
          return false;
        }
      }
      return true;
    } catch (SocketTimeoutException stillOpen) {
      return false;
    } catch (SocketException reset) {
      return true;
    }
  }

  /** Leaves with a TCP reset: the server's next attempt to write fails. */
  void reset() {
    try {
      socket.setSoLinger(true, 0);
    } catch (SocketException ignored) {
      // Closing below still ends the connection.
    }
    close();
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException ignored) {
      // Closing is best effort.
    }
  }

  /** The status line and headers, byte by byte, so nothing of the body is consumed. */
  private static String readHead(InputStream in) throws IOException {
    ByteArrayOutputStream head = new ByteArrayOutputStream();
    int matched = 0;
    byte[] end = {'\r', '\n', '\r', '\n'};
    while (matched < end.length) {
      int next = in.read();
      if (next == -1) {
        throw new IOException("The server closed before the response head");
      }
      head.write(next);
      matched = next == end[matched] ? matched + 1 : (next == '\r' ? 1 : 0);
    }
    return head.toString(StandardCharsets.US_ASCII);
  }
}
