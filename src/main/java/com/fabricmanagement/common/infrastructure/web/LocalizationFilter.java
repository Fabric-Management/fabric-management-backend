package com.fabricmanagement.common.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that reads the {@code Accept-Language} header from each incoming request and
 * stores the resolved locale in the thread-local {@link LocalizationContext}.
 *
 * <h2>Supported locales</h2>
 *
 * <p>The filter resolves the best match from the supported locale list (currently {@code tr} and
 * {@code en}). If no supported locale matches the header, the fallback is {@code "en"}.
 *
 * <h2>Order</h2>
 *
 * <p>Must run <b>before</b> any service that uses {@link LocalizationContext#getLocale()}. It is
 * registered with the lowest ordered filter position in {@link
 * com.fabricmanagement.common.infrastructure.security.SecurityConfig}.
 *
 * <h2>Thread safety</h2>
 *
 * <p>The {@code finally} block guarantees {@link LocalizationContext#clear()} is called even on
 * exceptions, preventing ThreadLocal leaks when thread pools are reused.
 */
@Component
@Slf4j
public class LocalizationFilter extends OncePerRequestFilter {

  /** Locales the backend officially supports. Extend as new languages are added. */
  private static final List<Locale> SUPPORTED_LOCALES =
      List.of(Locale.forLanguageTag("tr"), Locale.ENGLISH);

  private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      String resolved = resolveLocale(request);
      LocalizationContext.setLocale(resolved);
      log.trace("LocalizationFilter: locale={} for {}", resolved, request.getRequestURI());
      filterChain.doFilter(request, response);
    } finally {
      LocalizationContext.clear(); // Always clean up to prevent memory leaks
    }
  }

  /**
   * Resolves the best-match locale from the {@code Accept-Language} header.
   *
   * @param request incoming HTTP request
   * @return primary language subtag in lowercase (e.g. "tr", "en")
   */
  private static String resolveLocale(HttpServletRequest request) {
    String header = request.getHeader("Accept-Language");
    if (header == null || header.isBlank()) {
      return DEFAULT_LOCALE.getLanguage();
    }

    // Parse and look for best match
    List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
    Locale best = Locale.lookup(ranges, SUPPORTED_LOCALES);
    return (best != null ? best : DEFAULT_LOCALE).getLanguage();
  }
}
