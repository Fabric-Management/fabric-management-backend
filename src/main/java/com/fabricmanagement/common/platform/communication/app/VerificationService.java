package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.strategy.VerificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Verification Service - Multi-channel verification code delivery.
 *
 * <p>Automatically selects best available channel based on priority:</p>
 * <ol>
 *   <li>WhatsApp (if available)</li>
 *   <li>Email (if WhatsApp unavailable)</li>
 *   <li>SMS (fallback)</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * verificationService.sendVerificationCode("user@example.com", "123456");
 * // Automatically tries WhatsApp → Email → SMS
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final List<VerificationStrategy> strategies;

    public void sendVerificationCode(String recipient, String code) {
        log.info("Sending verification code to: {}", recipient);

        strategies.stream()
            .sorted(Comparator.comparing(VerificationStrategy::priority))
            .filter(VerificationStrategy::isAvailable)
            .findFirst()
            .ifPresentOrElse(
                strategy -> {
                    log.info("Using {} strategy", strategy.name());
                    strategy.sendVerificationCode(recipient, code);
                    log.info("Verification code sent successfully via {}", strategy.name());
                },
                () -> {
                    log.error("No verification strategy available!");
                    throw new RuntimeException("All verification channels unavailable");
                }
            );
    }
}

