package com.fabricmanagement.sales.common.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared soft-FK validation for sales-owned snapshots over production references. */
public final class SalesReferenceResolver {

  private SalesReferenceResolver() {}

  public static <T> T resolveNewSelection(
      UUID referenceId,
      String referenceType,
      Supplier<Optional<T>> finder,
      Predicate<T> available) {
    if (referenceId == null) {
      return null;
    }
    T reference =
        finder
            .get()
            .orElseThrow(() -> new NotFoundException(referenceType + " not found: " + referenceId));
    if (!available.test(reference)) {
      throw SalesDomainException.referenceNoLongerAvailable(referenceType, referenceId.toString());
    }
    return reference;
  }
}
