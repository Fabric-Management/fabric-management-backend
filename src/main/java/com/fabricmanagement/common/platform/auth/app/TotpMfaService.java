package com.fabricmanagement.common.platform.auth.app;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TotpMfaService {

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

  public String generateSecret() {
    return secretGenerator.generate();
  }

  public String getQrCodeImageUri(String secret, String accountName, String issuer) {
    QrData data =
        new QrData.Builder()
            .label(accountName)
            .secret(secret)
            .issuer(issuer)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();

    try {
      byte[] imageData = qrGenerator.generate(data);
      return getDataUriForImage(imageData, qrGenerator.getImageMimeType());
    } catch (QrGenerationException e) {
      log.error("Error generating QR code for TOTP MFA setup.", e);
      throw new RuntimeException("Unable to generate QR code", e);
    }
  }

  public boolean verifyCode(String secret, String code) {
    TimeProvider timeProvider = new SystemTimeProvider();
    CodeGenerator codeGenerator = new DefaultCodeGenerator();
    CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    // Verification checks current and adjacent time periods to account for slight clock drift
    return verifier.isValidCode(secret, code);
  }
}
