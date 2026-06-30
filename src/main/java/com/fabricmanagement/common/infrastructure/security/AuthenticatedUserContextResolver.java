package com.fabricmanagement.common.infrastructure.security;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthenticatedUserContextResolver {

  public Optional<AuthenticatedUserContext> resolve(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    if (authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return Optional.of(context);
    }

    log.warn("Principal is not an instance of AuthenticatedUserContext");
    return Optional.empty();
  }
}
