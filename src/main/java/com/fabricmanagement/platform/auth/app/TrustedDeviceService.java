package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.domain.TrustedDevice;
import com.fabricmanagement.platform.auth.infra.repository.TrustedDeviceRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustedDeviceService {

  private final TrustedDeviceRepository trustedDeviceRepository;

  @Value("${application.security.trusted-device.expiration-days:30}")
  private long expirationDays;

  @Value("${application.security.trusted-device.secret:fabric-trusted-device-secret-key-change-me}")
  private String hashSecret;

  @Value("${application.security.trusted-device.enforce-ip:true}")
  private boolean enforceIp;

  @Value("${application.security.trusted-device.enforce-user-agent:true}")
  private boolean enforceUserAgent;

  /** Creates a new trusted device record and returns the raw token to be sent to the client. */
  @Transactional
  public String createTrustedDevice(UUID userId, String ipAddress, String userAgent) {
    String rawToken = UUID.randomUUID().toString();
    String deviceHash = hashToken(rawToken);

    Instant expiresAt = Instant.now().plus(expirationDays, ChronoUnit.DAYS);

    TrustedDevice trustedDevice =
        TrustedDevice.builder()
            .userId(userId)
            .deviceHash(deviceHash)
            .ipAddress(ipAddress)
            .userAgent(normalizeUserAgent(userAgent))
            .expiresAt(expiresAt)
            .build();

    trustedDeviceRepository.save(trustedDevice);
    log.info("Trusted device created for user: {}", userId);

    return rawToken;
  }

  /**
   * Validates a trusted device token with full context binding.
   *
   * <p>Checks: token existence, user ownership, expiry, IP address match, and User-Agent
   * fingerprint match. If IP or UA changes (e.g. token stolen from a different country/browser),
   * the device is auto-revoked.
   */
  @Transactional
  public boolean validateDevice(String rawToken, UUID userId, String ipAddress, String userAgent) {
    if (rawToken == null || rawToken.isBlank()) {
      return false;
    }

    String deviceHash = hashToken(rawToken);
    Optional<TrustedDevice> deviceOpt = trustedDeviceRepository.findByDeviceHash(deviceHash);

    if (deviceOpt.isEmpty()) {
      return false;
    }

    TrustedDevice device = deviceOpt.get();

    if (!device.getUserId().equals(userId)) {
      log.warn("Device token mismatch: Belongs to {}, attempted by {}", device.getUserId(), userId);
      return false;
    }

    if (device.isExpired()) {
      log.info("Trusted device expired for user: {}", userId);
      trustedDeviceRepository.delete(device);
      return false;
    }

    if (enforceIp && !isSameIp(device.getIpAddress(), ipAddress)) {
      log.warn(
          "Trusted device IP mismatch for user {}: stored={}, current={} — revoking device",
          userId,
          device.getIpAddress(),
          ipAddress);
      trustedDeviceRepository.delete(device);
      return false;
    }

    if (enforceUserAgent && !isSameUserAgentFamily(device.getUserAgent(), userAgent)) {
      log.warn(
          "Trusted device User-Agent mismatch for user {}: stored={}, current={} — revoking device",
          userId,
          truncateForLog(device.getUserAgent()),
          truncateForLog(userAgent));
      trustedDeviceRepository.delete(device);
      return false;
    }

    device.setLastUsedAt(Instant.now());
    trustedDeviceRepository.save(device);

    return true;
  }

  /**
   * @deprecated Use {@link #validateDevice(String, UUID, String, String)} instead.
   */
  @Deprecated
  @Transactional
  public boolean validateDevice(String rawToken, UUID userId) {
    return validateDevice(rawToken, userId, null, null);
  }

  private boolean isSameIp(String stored, String current) {
    if (stored == null || current == null) {
      return true;
    }
    return stored.equals(current);
  }

  /**
   * Compares browser family extracted from User-Agent strings. Tolerates minor version changes
   * (e.g. Chrome 120 vs Chrome 121) but catches cross-browser or cross-platform changes.
   */
  private boolean isSameUserAgentFamily(String stored, String current) {
    if (stored == null || current == null) {
      return true;
    }
    String storedFamily = extractBrowserFamily(stored);
    String currentFamily = extractBrowserFamily(current);
    return storedFamily.equals(currentFamily);
  }

  /**
   * Extracts a rough browser+OS family fingerprint from User-Agent. E.g. "chrome|windows",
   * "safari|mac", "firefox|linux". This is intentionally coarse — we want to catch stolen tokens
   * used from a completely different browser/OS, not block minor version updates.
   */
  private String extractBrowserFamily(String ua) {
    if (ua == null) return "unknown";
    String lower = ua.toLowerCase();

    String browser;
    if (lower.contains("edg/") || lower.contains("edge/")) {
      browser = "edge";
    } else if (lower.contains("chrome/") && !lower.contains("chromium/")) {
      browser = "chrome";
    } else if (lower.contains("firefox/")) {
      browser = "firefox";
    } else if (lower.contains("safari/") && !lower.contains("chrome/")) {
      browser = "safari";
    } else {
      browser = "other";
    }

    String os;
    if (lower.contains("windows")) {
      os = "windows";
    } else if (lower.contains("mac os") || lower.contains("macintosh")) {
      os = "mac";
    } else if (lower.contains("linux") && !lower.contains("android")) {
      os = "linux";
    } else if (lower.contains("android")) {
      os = "android";
    } else if (lower.contains("iphone") || lower.contains("ipad")) {
      os = "ios";
    } else {
      os = "other";
    }

    return browser + "|" + os;
  }

  private String normalizeUserAgent(String userAgent) {
    if (userAgent == null) return null;
    return userAgent.length() > 1000 ? userAgent.substring(0, 1000) : userAgent;
  }

  private String truncateForLog(String value) {
    if (value == null) return "null";
    return value.length() > 80 ? value.substring(0, 80) + "..." : value;
  }

  @Transactional
  public void revokeAllDevicesForUser(UUID userId) {
    trustedDeviceRepository.deleteByUserId(userId);
    log.info("Revoked all trusted devices for user: {}", userId);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String salted = rawToken + ":" + hashSecret;
      byte[] hashBytes = digest.digest(salted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 algorithm not found", e);
      throw new RuntimeException("Failed to hash trusted device token", e);
    }
  }
}
