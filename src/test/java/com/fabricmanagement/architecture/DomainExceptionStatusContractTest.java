package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler;
import com.fabricmanagement.common.infrastructure.web.exception.OptimisticLockConflictException;
import com.fabricmanagement.production.common.exception.ForbiddenOperationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.InsufficientWeightException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.WeightReconciliationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

@DisplayName("Domain Exception Status Contract Tests")
class DomainExceptionStatusContractTest {

  @Test
  @DisplayName("WeightReconciliationException MUST map to 409 Conflict")
  void testWeightReconciliationException() {
    WeightReconciliationException ex =
        new WeightReconciliationException(
            UUID.randomUUID(), "B1", BigDecimal.ZERO, BigDecimal.ZERO);
    assertThat(ex.getHttpStatus()).isEqualTo(409);
    assertThat(ex.getErrorCode()).isEqualTo("WEIGHT_RECONCILIATION_MISMATCH");
  }

  @Test
  @DisplayName("InsufficientWeightException MUST map to 422 Unprocessable Entity")
  void testInsufficientWeightException() {
    InsufficientWeightException ex =
        new InsufficientWeightException("BARCODE", BigDecimal.ONE, BigDecimal.ZERO);
    assertThat(ex.getHttpStatus()).isEqualTo(422);
    assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_WEIGHT");
  }

  @Test
  @DisplayName("OptimisticLockConflictException MUST map to 409 Conflict")
  void testOptimisticLockConflictException() {
    OptimisticLockConflictException ex =
        new OptimisticLockConflictException("Conflict", "Entity", 1L, 2L);
    assertThat(ex.getHttpStatus()).isEqualTo(409);
    assertThat(ex.getErrorCode()).isEqualTo("OPTIMISTIC_LOCK");
  }

  @Test
  @DisplayName("ForbiddenOperationException MUST map to 403 Forbidden")
  void testForbiddenOperationException() {
    ForbiddenOperationException ex = new ForbiddenOperationException("Forbidden");
    assertThat(ex.getHttpStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName(
      "Validation Exceptions MUST map to 422 Unprocessable Entity in GlobalExceptionHandler")
  void testValidationExceptionMapping() throws Exception {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/test");

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
    MethodParameter parameter =
        new MethodParameter(
            this.getClass().getDeclaredMethod("testValidationExceptionMapping"), -1);
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(parameter, bindingResult);

    var response = handler.handleValidation(ex, request);
    assertThat(response.getStatus()).isEqualTo(422);
    assertThat(response.getCode()).isEqualTo("VALIDATION_ERROR");
  }
}
