package com.fabricmanagement.common.util;

/**
 * Extracts human-readable device name from User-Agent strings.
 *
 * <p>Produces labels like "Chrome on Windows", "Safari on macOS", "Mobile Safari on iOS" for
 * display in the "Active Sessions" UI.
 */
public final class DeviceInfoUtil {

  private DeviceInfoUtil() {}

  /**
   * Extract a human-readable device name from a User-Agent header.
   *
   * @param userAgent raw User-Agent string (nullable)
   * @return readable label, e.g. "Chrome on Windows"
   */
  public static String extractDeviceName(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return "Unknown Device";
    }

    String browser = detectBrowser(userAgent);
    String os = detectOS(userAgent);

    return browser + " on " + os;
  }

  private static String detectBrowser(String ua) {
    String lower = ua.toLowerCase();

    if (lower.contains("edg/") || lower.contains("edge/")) return "Edge";
    if (lower.contains("opr/") || lower.contains("opera")) return "Opera";
    if (lower.contains("chrome/") && !lower.contains("chromium/")) return "Chrome";
    if (lower.contains("firefox/")) return "Firefox";
    if (lower.contains("safari/") && !lower.contains("chrome/")) return "Safari";
    if (lower.contains("postman")) return "Postman";
    if (lower.contains("curl")) return "cURL";

    return "Unknown Browser";
  }

  private static String detectOS(String ua) {
    String lower = ua.toLowerCase();

    if (lower.contains("iphone")) return "iOS (iPhone)";
    if (lower.contains("ipad")) return "iOS (iPad)";
    if (lower.contains("android")) return "Android";
    if (lower.contains("windows nt 10")) return "Windows";
    if (lower.contains("windows")) return "Windows";
    if (lower.contains("mac os x") || lower.contains("macintosh")) return "macOS";
    if (lower.contains("linux") && !lower.contains("android")) return "Linux";
    if (lower.contains("cros")) return "Chrome OS";

    return "Unknown OS";
  }
}
