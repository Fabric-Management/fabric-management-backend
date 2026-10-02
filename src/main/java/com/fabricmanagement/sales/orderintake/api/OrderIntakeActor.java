package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves the authenticated actor of an order-intake request. */
final class OrderIntakeActor {

  private OrderIntakeActor() {}

  static UUID current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getDetails() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new NotFoundException("Authenticated user context not found");
  }
}
