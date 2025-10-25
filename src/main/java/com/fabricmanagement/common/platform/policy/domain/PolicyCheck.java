package com.fabricmanagement.common.platform.policy.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy check annotation for endpoint protection.
 *
 * <p>Usage on controller methods to enforce policy-based access control.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @PostMapping("/materials")
 * @PolicyCheck(resource="fabric.material", action="create")
 * public ResponseEntity<?> createMaterial(@RequestBody CreateMaterialRequest request) {
 *     // Implementation
 * }
 * }</pre>
 *
 * <h2>4-Layer Check Flow:</h2>
 * <ol>
 *   <li>Subscription check (Layer 1-3) - EnhancedSubscriptionService</li>
 *   <li>Policy check (Layer 4) - THIS annotation triggers PolicyService</li>
 * </ol>
 *
 * <p>If policy check fails, AccessDeniedException is thrown.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PolicyCheck {

    /**
     * Resource being accessed.
     *
     * <p>Format: {domain}.{entity}</p>
     * <p>Example: "fabric.material", "fabric.yarn", "logistics.inventory"</p>
     *
     * @return resource identifier
     */
    String resource();

    /**
     * Action being performed.
     *
     * <p>Common values: "create", "read", "update", "delete"</p>
     * <p>Custom values allowed: "approve", "export", "publish"</p>
     *
     * @return action name
     */
    String action();

    /**
     * Optional feature ID for subscription check.
     *
     * <p>If specified, checks both subscription AND policy.</p>
     * <p>Format: {os_prefix}.{module}.{feature_name}</p>
     * <p>Example: "yarn.blend.management"</p>
     *
     * @return feature ID, or empty string if not required
     */
    String featureId() default "";

    /**
     * Optional quota type to check.
     *
     * <p>If specified, verifies tenant is within usage limits.</p>
     * <p>Example: "api_calls", "fiber_entities"</p>
     *
     * @return quota type, or empty string if not required
     */
    String quotaType() default "";
}

