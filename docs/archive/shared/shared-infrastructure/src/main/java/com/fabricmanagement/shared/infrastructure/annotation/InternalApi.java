package com.fabricmanagement.shared.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for internal API endpoints
 * 
 * Endpoints marked with this annotation should only be accessible
 * from other internal microservices, not from external clients.
 * 
 * Usage:
 * @InternalApi
 * @GetMapping("/internal-endpoint")
 * public ResponseEntity<?> internalMethod() { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalApi {
    /**
     * Optional description of why this endpoint is internal
     */
    String value() default "";
}
