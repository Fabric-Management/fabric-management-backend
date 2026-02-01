package com.fabricmanagement.common.infrastructure.web.rate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as rate-limited (e.g. to prevent enumeration or brute force).
 *
 * <p>Use with {@link RateLimitAspect}. Key is derived from authenticated principal (per-user
 * limit).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  /** Max number of requests allowed in the window. */
  int requests() default 5;

  /** Window duration in seconds. */
  int windowSeconds() default 60;
}
