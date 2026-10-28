package com.fabricmanagement.product.fabric.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;
import java.util.List;

/** Fabric master-data rule violations. Activation problems are a 409 with every problem listed. */
public class FabricDomainException extends ProductDomainException {

  private final List<String> problems;

  public FabricDomainException(String message) {
    super(message);
    this.problems = List.of();
  }

  /** A refusal the client tells apart by its code, e.g. a duplicate price (409). */
  public FabricDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
    this.problems = List.of();
  }

  public FabricDomainException(List<String> problems, String message) {
    super(message, "FABRIC_ARTICLE_INCOMPLETE", 409);
    this.problems = List.copyOf(problems);
    withDetail("problems", this.problems);
  }

  public List<String> getProblems() {
    return problems;
  }
}
