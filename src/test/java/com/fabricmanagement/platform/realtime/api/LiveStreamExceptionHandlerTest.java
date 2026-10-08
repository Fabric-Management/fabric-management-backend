package com.fabricmanagement.platform.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.platform.realtime.app.LiveStreamProperties;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException.Rejection;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The refusal handler (CEDIT-05 §3.1) works with and without the channel's settings: every web
 * slice loads controller advice, while the settings exist only in the full application.
 */
class LiveStreamExceptionHandlerTest {

  private final MockHttpServletRequest request =
      new MockHttpServletRequest("GET", "/api/v1/sales/orders/x/live-events");

  @Test
  @DisplayName("without the channel's settings (a web slice) a temporary refusal still says when")
  void defaultRetryWithoutSettings() {
    LiveStreamExceptionHandler handler =
        new LiveStreamExceptionHandler(
            new StaticListableBeanFactory().getBeanProvider(LiveStreamProperties.class));

    ResponseEntity<ApiProblemDetail> response =
        handler.handleRejected(new LiveStreamRejectedException(Rejection.CAPACITY), request);

    assertThat(response.getStatusCode().value()).isEqualTo(429);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("LIVE_STREAM_CAPACITY");
  }

  @Test
  @DisplayName("with the settings, Retry-After follows them; final refusals carry none")
  void configuredRetryAndFinalRefusals() {
    LiveStreamProperties settings = new LiveStreamProperties();
    settings.setRetryAfter(Duration.ofSeconds(7));
    LiveStreamExceptionHandler handler =
        new LiveStreamExceptionHandler(
            new StaticListableBeanFactory(Map.of("liveStreamProperties", settings))
                .getBeanProvider(LiveStreamProperties.class));

    assertThat(
            handler
                .handleRejected(
                    new LiveStreamRejectedException(Rejection.FEATURE_DISABLED), request)
                .getHeaders()
                .getFirst(HttpHeaders.RETRY_AFTER))
        .isEqualTo("7");
    ResponseEntity<ApiProblemDetail> notFound =
        handler.handleRejected(new LiveStreamRejectedException(Rejection.NOT_FOUND), request);
    assertThat(notFound.getStatusCode().value()).isEqualTo(404);
    assertThat(notFound.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
  }
}
