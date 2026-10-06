package com.fabricmanagement.common.infrastructure.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A domain failure about request fields answers the problem's typed {@code errors} map, as bean
 * validation does, beside its details; the {@code errors} key is written once (CEDIT-03 R3).
 */
class GlobalExceptionHandlerFieldErrorsTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler(null))
            .build();
  }

  @Test
  @DisplayName("field errors are the typed errors map; details stay top-level properties")
  void fieldErrorsAreTheTypedErrorsMap() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/fail/field"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.key").value("paymentTerms"))
            .andExpect(
                jsonPath("$.errors['header.paymentTerms.value']").value("At most 200 characters"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body.split("\"errors\"", -1)).as("one errors key in %s", body).hasSize(2);
  }

  @Test
  @DisplayName("a failure without field errors has no errors map")
  void noFieldErrorsNoMap() throws Exception {
    mockMvc
        .perform(get("/fail/plain"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  @DisplayName("a detail cannot take a name the problem body already has")
  void reservedDetailNamesAreRefused() {
    assertThatThrownBy(() -> new TestFailure("x", 422).withDetail("errors", "y"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TestFailure("x", 422).withDetail("code", "y"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class TestFailure extends DomainException {
    TestFailure(String code, int status) {
      super("Refused", code, status);
    }
  }

  @RestController
  static class FailingController {
    @GetMapping("/fail/field")
    void field() {
      throw new TestFailure("VALIDATION_ERROR", 422)
          .withDetail("key", "paymentTerms")
          .withFieldError("header.paymentTerms.value", "At most 200 characters");
    }

    @GetMapping("/fail/plain")
    void plain() {
      throw new TestFailure("SOMETHING_TAKEN", 409);
    }
  }
}
