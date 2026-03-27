package com.fabricmanagement.platform.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth module configuration (lockout, verification, JWT, password).
 *
 * <p>Bind with prefix "auth". Example: auth.lockout.max-attempts=5
 */
@ConfigurationProperties(prefix = "auth")
@Validated
@Data
public class AuthProperties {

  @Valid private LockoutProperties lockout = new LockoutProperties();

  @Valid private VerificationProperties verification = new VerificationProperties();

  @Valid private JwtProperties jwt = new JwtProperties();

  @Valid private PasswordProperties password = new PasswordProperties();

  @Data
  public static class LockoutProperties {
    @Min(3)
    @Max(10)
    private int maxAttempts = 5;

    private Duration lockDuration = Duration.ofMinutes(30);

    /** Lock duration in seconds (for code that expects seconds). */
    public int getLockDurationSeconds() {
      return (int) lockDuration.getSeconds();
    }
  }

  @Data
  public static class VerificationProperties {
    @Min(4)
    @Max(8)
    private int codeLength = 6;

    private Duration codeExpiry = Duration.ofMinutes(10);

    @Min(1)
    @Max(5)
    private int maxAttempts = 3;
  }

  @Data
  public static class JwtProperties {
    private Duration accessTokenExpiry = Duration.ofMinutes(15);
    private Duration refreshTokenExpiry = Duration.ofDays(7);
  }

  @Data
  public static class PasswordProperties {
    @Min(4)
    @Max(12)
    private int bcryptStrength = 10;
  }
}
