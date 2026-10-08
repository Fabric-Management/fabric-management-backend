package com.fabricmanagement.sales.salesorder.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A real HTTP client of one live stream (CEDIT-05 §8): it reads the socket on its own thread and
 * parses Server-Sent Events as a browser does, so the tests see what was flushed, in order. A
 * refused stream keeps its status, headers and problem body.
 */
final class LiveSse implements AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
  private static final Frame END = new Frame(null, null, null);

  /** One SSE block: a named data frame or a comment (the keepalive). */
  record Frame(String event, String data, String comment) {

    boolean isHeartbeat() {
      return event == null && data == null && comment != null;
    }

    JsonNode envelope() {
      try {
        return JSON.readTree(data);
      } catch (IOException failure) {
        throw new AssertionError("Frame data is not JSON: " + data, failure);
      }
    }

    /** The frame DTO inside the ApiResponse envelope. */
    JsonNode body() {
      return envelope().path("data");
    }

    String revision() {
      return body().path("revision").asText();
    }

    String reason() {
      return body().path("reason").asText();
    }
  }

  private final HttpResponse<InputStream> response;
  private final String errorBody;
  private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
  private final List<Frame> received = new CopyOnWriteArrayList<>();
  private final StringBuffer raw = new StringBuffer();
  private final Thread reader;
  private volatile boolean ended;

  private LiveSse(HttpResponse<InputStream> response) throws IOException {
    this.response = response;
    if (response.statusCode() != 200) {
      try (InputStream body = response.body()) {
        this.errorBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
      }
      this.reader = null;
      this.ended = true;
      return;
    }
    this.errorBody = null;
    this.reader = new Thread(this::read, "live-sse-test-reader");
    this.reader.setDaemon(true);
    this.reader.start();
  }

  static LiveSse open(URI uri, Map<String, String> headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(15));
    request.header("Accept", "text/event-stream");
    headers.forEach(request::header);
    try {
      return new LiveSse(CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofInputStream()));
    } catch (IOException failure) {
      throw new AssertionError("Live stream request failed", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  int status() {
    return response.statusCode();
  }

  HttpHeaders headers() {
    return response.headers();
  }

  String errorBody() {
    return errorBody;
  }

  /** Everything read from the socket so far, as text. */
  String rawText() {
    return raw.toString();
  }

  List<Frame> received() {
    return new ArrayList<>(received);
  }

  boolean ended() {
    return ended;
  }

  /** The next frame (heartbeats included); fails if none comes in time or the stream ended. */
  Frame next(Duration timeout) {
    Frame frame = poll(timeout);
    if (frame == null) {
      throw new AssertionError("No frame within " + timeout + "; received " + received);
    }
    return frame;
  }

  /** The next data frame, skipping keepalive comments. */
  Frame nextEvent(Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      Frame frame = next(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
      if (!frame.isHeartbeat()) {
        return frame;
      }
    }
  }

  /** Waits for the next frame matching the condition; frames before it are consumed. */
  Frame await(Predicate<Frame> condition, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      Frame frame = next(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
      if (condition.test(frame)) {
        return frame;
      }
    }
  }

  /**
   * Waits for the given number of keepalive comments and returns the data frames seen meanwhile:
   * each keepalive follows a successful check, so after two of them every change committed before
   * the wait began would already have produced a frame.
   */
  List<Frame> eventsUntilHeartbeats(int heartbeats, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    List<Frame> events = new ArrayList<>();
    int seen = 0;
    while (seen < heartbeats) {
      Frame frame = next(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
      if (frame.isHeartbeat()) {
        seen++;
      } else {
        events.add(frame);
      }
    }
    return events;
  }

  /** Waits until the server ended the stream; returns the frames read until then. */
  List<Frame> awaitEnd(Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    List<Frame> rest = new ArrayList<>();
    while (true) {
      Frame frame;
      try {
        frame = frames.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
      if (frame == null) {
        throw new AssertionError("The stream did not end within " + timeout);
      }
      if (frame == END) {
        return rest;
      }
      rest.add(frame);
    }
  }

  private Frame poll(Duration timeout) {
    try {
      Frame frame = frames.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
      if (frame == END) {
        frames.add(END);
        throw new AssertionError("The stream ended; received " + received);
      }
      return frame;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  @Override
  public void close() {
    try {
      response.body().close();
    } catch (IOException ignored) {
      // The connection is gone either way.
    }
    if (reader != null) {
      reader.interrupt();
    }
  }

  private void read() {
    try (BufferedReader lines =
        new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      String event = null;
      StringBuilder data = null;
      StringBuilder comment = null;
      String line;
      while ((line = lines.readLine()) != null) {
        raw.append(line).append('\n');
        if (line.isEmpty()) {
          if (event != null || data != null || comment != null) {
            Frame frame =
                new Frame(
                    event,
                    data == null ? null : data.toString(),
                    comment == null ? null : comment.toString());
            received.add(frame);
            frames.add(frame);
          }
          event = null;
          data = null;
          comment = null;
        } else if (line.startsWith(":")) {
          comment = new StringBuilder(line.substring(1).trim());
        } else if (line.startsWith("event:")) {
          event = value(line, "event:");
        } else if (line.startsWith("data:")) {
          data = data == null ? new StringBuilder() : data.append('\n');
          data.append(value(line, "data:"));
        }
      }
    } catch (IOException closed) {
      // The test closed the client, or the server reset the connection.
    } finally {
      ended = true;
      frames.add(END);
    }
  }

  private static String value(String line, String field) {
    String value = line.substring(field.length());
    return value.startsWith(" ") ? value.substring(1) : value;
  }

  static Optional<String> header(LiveSse stream, String name) {
    return stream.headers().firstValue(name);
  }
}
