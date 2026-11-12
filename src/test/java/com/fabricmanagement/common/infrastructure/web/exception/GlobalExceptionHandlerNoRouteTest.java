package com.fabricmanagement.common.infrastructure.web.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** A request no controller maps is the client's mistake: 404, never the generic 500 (OBS-404-1). */
class GlobalExceptionHandlerNoRouteTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new RemovedRouteController())
            .setControllerAdvice(new GlobalExceptionHandler(null))
            .build();
  }

  @Test
  @DisplayName("a route that does not exist answers 404 NOT_FOUND naming the method and path")
  void missingRouteIsNotFound() throws Exception {
    mockMvc
        .perform(post("/api/v1/sales/orders/42/confirm"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(
            jsonPath("$.detail").value("No endpoint for POST /api/v1/sales/orders/42/confirm"))
        .andExpect(jsonPath("$.instance").value("/api/v1/sales/orders/42/confirm"));
  }

  /** Stands in for the resource handler that throws when nothing else matched. */
  @RestController
  static class RemovedRouteController {

    @PostMapping("/api/v1/sales/orders/{id}/confirm")
    void confirm() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.POST, "api/v1/sales/orders/42/confirm");
    }
  }
}
