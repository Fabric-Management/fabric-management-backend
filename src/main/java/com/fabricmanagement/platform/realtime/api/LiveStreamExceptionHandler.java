package com.fabricmanagement.platform.realtime.api;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.platform.realtime.app.LiveStreamProperties;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A refused live stream, answered before any stream byte (CEDIT-05 §3.1): the standard problem body
 * with an explicit {@code application/problem+json} type (the request accepts only {@code
 * text/event-stream}), and {@code Retry-After} when the refusal is temporary (429, 503). Ordered
 * ahead of the global handler, which would answer the same status without the header.
 *
 * <p>Every web slice loads controller advice, while the channel's settings are a component of the
 * full application only; the handler therefore takes them when present and otherwise answers with
 * the default {@code Retry-After}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LiveStreamExceptionHandler {

  private final ObjectProvider<LiveStreamProperties> properties;

  public LiveStreamExceptionHandler(ObjectProvider<LiveStreamProperties> properties) {
    this.properties = properties;
  }

  @ExceptionHandler(LiveStreamRejectedException.class)
  public ResponseEntity<ApiProblemDetail> handleRejected(
      LiveStreamRejectedException rejected, HttpServletRequest request) {
    LiveStreamRejectedException.Rejection rejection = rejected.rejection();
    log.info("Live stream refused [{}]", rejection.code());
    HttpStatus status = HttpStatus.valueOf(rejection.status());
    ApiProblemDetail problem = ApiProblemDetail.forStatusAndDetail(status, rejected.getMessage());
    problem.setTitle(status.getReasonPhrase());
    problem.setCode(rejection.code());
    problem.setInstance(URI.create(request.getRequestURI()));
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (rejection.retryLater()) {
      LiveStreamProperties settings = properties.getIfAvailable(LiveStreamProperties::new);
      response.header(HttpHeaders.RETRY_AFTER, Long.toString(settings.retryAfterSeconds()));
    }
    return response.body(problem);
  }
}
