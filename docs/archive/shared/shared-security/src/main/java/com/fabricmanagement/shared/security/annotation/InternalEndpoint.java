package com.fabricmanagement.shared.security.annotation;

import java.lang.annotation.*;

/**
 * Internal Endpoint Annotation
 * 
 * Marks a controller method as an internal endpoint that:
 * 1. Requires X-Internal-API-Key header (no JWT needed)
 * 2. Should only be called by other microservices
 * 3. Bypasses JWT authentication
 * 
 * Security:
 * - InternalAuthenticationFilter automatically detects this annotation
 * - Validates X-Internal-API-Key header
 * - Creates INTERNAL_SERVICE security context
 * 
 * Usage:
 * 
 * ```java
 * @InternalEndpoint
 * @PostMapping("/api/v1/companies")
 * public ResponseEntity<UUID> createCompany(@RequestBody CreateCompanyDto dto) {
 *     // This endpoint requires X-Internal-API-Key
 *     // Called by User Service during tenant onboarding
 *     // No JWT needed!
 * }
 * ```
 * 
 * Benefits:
 * ✅ Self-documenting (clear which endpoints are internal)
 * ✅ Compile-time safety (typo-proof)
 * ✅ Refactoring-friendly (IDE can track annotation usage)
 * ✅ Zero hardcoded paths (DRY principle)
 * ✅ Automatic discovery by security filter
 * 
 * Best Practices:
 * - Use for service-to-service endpoints only
 * - Combine with method-level @PostMapping/@GetMapping
 * - Document WHY endpoint is internal (which service calls it)
 * - Consider adding description parameter for documentation
 * 
 * @author Fabric Management Team
 * @since 3.2.0 - Internal Endpoint Annotation Pattern (Oct 13, 2025)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InternalEndpoint {
    
    /**
     * Description of why this endpoint is internal
     * 
     * Example: "Called by User Service during tenant onboarding"
     */
    String description() default "";
    
    /**
     * Which service(s) call this endpoint
     * 
     * Example: {"user-service", "company-service"}
     */
    String[] calledBy() default {};
    
    /**
     * Whether this endpoint is critical for the calling service
     * 
     * If true, failure of this endpoint should trigger alerts
     */
    boolean critical() default false;
}

