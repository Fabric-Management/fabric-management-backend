package com.fabricmanagement.common.infrastructure.web;

/**
 * Thread-local holder for the current request's locale and timezone.
 *
 * <p>Populated by {@link LocalizationFilter} from the {@code Accept-Language} HTTP header at the
 * start of each request and cleared after the response to prevent memory leaks.
 *
 * <p><b>Usage in services:</b>
 *
 * <pre>{@code
 * String locale   = LocalizationContext.getLocale();    // e.g. "tr"
 * String timezone = LocalizationContext.getTimezone();  // null unless explicitly set
 * }</pre>
 *
 * <p><b>Thread safety:</b> Each request thread has its own isolated copy through {@link
 * ThreadLocal}. The filter's {@code finally} block guarantees cleanup even when exceptions occur.
 */
public final class LocalizationContext {

  private static final ThreadLocal<String> LOCALE = new ThreadLocal<>();
  private static final ThreadLocal<String> TIMEZONE = new ThreadLocal<>();

  private LocalizationContext() {
    // Utility class — do not instantiate
  }

  // ============================================================
  // SETTERS (called by LocalizationFilter)
  // ============================================================

  public static void setLocale(String locale) {
    LOCALE.set(locale);
  }

  public static void setTimezone(String timezone) {
    TIMEZONE.set(timezone);
  }

  // ============================================================
  // GETTERS
  // ============================================================

  /**
   * Returns the primary language subtag of the current request's locale (e.g. "tr", "en"). Returns
   * "en" if no {@code Accept-Language} header was present.
   */
  public static String getLocale() {
    String locale = LOCALE.get();
    return locale != null ? locale : "en";
  }

  /**
   * Returns the timezone override from the request context, or {@code null} if none was set.
   * Timezone is not extracted from HTTP headers (only from user preferences).
   */
  public static String getTimezone() {
    return TIMEZONE.get();
  }

  // ============================================================
  // CLEAR (called after every request to prevent memory leaks)
  // ============================================================

  /** Remove all thread-local values. Must be called at the end of every request thread. */
  public static void clear() {
    LOCALE.remove();
    TIMEZONE.remove();
  }
}
