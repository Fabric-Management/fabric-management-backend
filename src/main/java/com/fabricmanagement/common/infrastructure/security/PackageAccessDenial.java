package com.fabricmanagement.common.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Remembers, for the current request only, that a permission check failed because the tenant's
 * packages do not open the resource. A {@code @PreAuthorize} expression can only answer true or
 * false, so the error handler reads this to tell the client <em>why</em> access was refused.
 */
public final class PackageAccessDenial {

  private static final String ATTRIBUTE = PackageAccessDenial.class.getName() + ".RESOURCE";

  private PackageAccessDenial() {}

  static void record(String resource) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.setAttribute(ATTRIBUTE, resource, RequestAttributes.SCOPE_REQUEST);
    }
  }

  /** The resource whose package was not enabled for this request, if that was the reason. */
  public static Optional<String> resourceIn(HttpServletRequest request) {
    Object resource = request.getAttribute(ATTRIBUTE);
    return resource instanceof String value ? Optional.of(value) : Optional.empty();
  }
}
