package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.auth.domain.TrustedDevice;
import com.fabricmanagement.common.platform.auth.infra.repository.TrustedDeviceRepository;
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

  /** Creates a new trusted device record and returns the raw token to be sent to the client. */
  @Transactional
  public String createTrustedDevice(UUID userId, String ipAddress, String userAgent) {
    // Generate a random raw token for the client
    String rawToken = UUID.randomUUID().toString();

    // Hash it before storing in the database
    String deviceHash = hashToken(rawToken);

    Instant expiresAt = Instant.now().plus(expirationDays, ChronoUnit.DAYS);

    TrustedDevice trustedDevice =
        TrustedDevice.builder()
            .userId(userId)
            .deviceHash(deviceHash)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .expiresAt(expiresAt)
            .build();

    trustedDeviceRepository.save(trustedDevice);
    log.info("Trusted device created for user: {}", userId);

    return rawToken;
  }

  /**
   * Validates a raw token from the client. Only returns true if the token exists, belongs to the
   * correct user, and has not expired.
   */
  @Transactional
  public boolean validateDevice(String rawToken, UUID userId) {
    if (rawToken == null || rawToken.isBlank()) {
      return false;
    }

    String deviceHash = hashToken(rawToken);
    Optional<TrustedDevice> deviceOpt = trustedDeviceRepository.findByDeviceHash(deviceHash);

    if (deviceOpt.isEmpty()) {
      return false;
    }

    TrustedDevice device = deviceOpt.get();

    // Must belong to the exact user
    if (!device.getUserId().equals(userId)) {
      log.warn("Device token mismatch: Belongs to {}, attempted by {}", device.getUserId(), userId);
      return false;
    }

    if (device.isExpired()) {
      log.info("Trusted device expired for user: {}", userId);
      trustedDeviceRepository.delete(device);
      return false;
    }

    // Update last used timestamp
    device.setLastUsedAt(Instant.now());
    trustedDeviceRepository.save(device);

    return true;
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
