package com.fabricmanagement.common.infrastructure.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server errors carry a reference the user can quote and the log can be searched for; temporary
 * infrastructure failures are 503 (retry later), everything else stays 500.
 */
class GlobalExceptionHandlerServerErrorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler(null))
            .build();
  }

  @AfterEach
  void clearMdc() {
    MDC.remove(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
  }

  @Test
  @DisplayName("unexpected error returns 500 UNEXPECTED_ERROR with the request trace id")
  void unexpectedErrorCarriesTraceId() throws Exception {
    MDC.put(GlobalExceptionHandler.TRACE_ID_MDC_KEY, TRACE_ID);

    mockMvc
        .perform(get("/fail/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  @DisplayName("a missing table is a defect, not a temporary outage: 500, not 503")
  void badSqlGrammarStaysInternalServerError() throws Exception {
    mockMvc
        .perform(get("/fail/missing-table"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
        .andExpect(header().doesNotExist("Retry-After"));
  }

  @Test
  @DisplayName("no database connection returns 503 with Retry-After and the trace id")
  void noConnectionIsServiceUnavailable() throws Exception {
    MDC.put(GlobalExceptionHandler.TRACE_ID_MDC_KEY, TRACE_ID);

    mockMvc
        .perform(get("/fail/no-connection"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  @DisplayName("a transaction that cannot start returns 503")
  void transactionThatCannotStartIsServiceUnavailable() throws Exception {
    mockMvc
        .perform(get("/fail/no-transaction"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"));
  }

  @Test
  @DisplayName("a query timeout returns 503")
  void queryTimeoutIsServiceUnavailable() throws Exception {
    mockMvc
        .perform(get("/fail/query-timeout"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"));
  }

  @Test
  @DisplayName("without an active trace a 32-character hex reference is generated")
  void generatesReferenceWithoutActiveTrace() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/public/signup");

    ApiProblemDetail response =
        new GlobalExceptionHandler(null).handleGeneric(new IllegalStateException("boom"), request);

    assertThat(response.getTraceId()).matches("[0-9a-f]{32}");
  }

  @RestController
  static class FailingController {

    @GetMapping("/fail/unexpected")
    String unexpected() {
      throw new UnsupportedOperationException("boom");
    }

    @GetMapping("/fail/missing-table")
    String missingTable() {
      throw new BadSqlGrammarException(
          "select",
          "select id from common_communication.communication_email_outbox",
          new SQLException("relation does not exist", "42P01"));
    }

    @GetMapping("/fail/no-connection")
    String noConnection() {
      throw new CannotGetJdbcConnectionException("Connection refused");
    }

    @GetMapping("/fail/no-transaction")
    String noTransaction() {
      throw new CannotCreateTransactionException("Could not open JPA EntityManager");
    }

    @GetMapping("/fail/query-timeout")
    String queryTimeout() {
      throw new QueryTimeoutException("Query timed out");
    }
  }
}
