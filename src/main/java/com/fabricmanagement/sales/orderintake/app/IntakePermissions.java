package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Evaluates the current user's permission keys the same way the endpoint annotations do. */
@Component
@RequiredArgsConstructor
class IntakePermissions {

  private final SpELPermissionEvaluator auth;

  boolean has(PermissionKey key) {
    return auth.can(
        SecurityContextHolder.getContext().getAuthentication(), key.resource(), key.action());
  }
}
