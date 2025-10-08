package com.fabricmanagement.shared.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Current Security Context Annotation
 * 
 * Use this annotation on controller method parameters to inject current security context.
 * 
 * Usage:
 * <pre>
 * {@code
 * @GetMapping("/{id}")
 * public ResponseEntity<UserResponse> getUser(
 *         @PathVariable UUID id,
 *         @CurrentSecurityContext SecurityContext ctx) {
 *     
 *     UUID tenantId = ctx.getTenantId();
 *     String userId = ctx.getUserId();
 *     // ...
 * }
 * }
 * </pre>
 * 
 * Benefits:
 * - No need to call SecurityContextHolder.getCurrentTenantId() repeatedly
 * - Cleaner and more readable code
 * - Easier to test (can pass mock SecurityContext)
 * - Follows DRY principle
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentSecurityContext {
}

