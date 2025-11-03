package com.fabricmanagement.common.platform.communication.domain.strategy;

import com.fabricmanagement.common.platform.communication.infra.client.WhatsAppClient;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WhatsApp verification strategy - Priority 1 (Highest).
 *
 * <p>Sends verification codes via WhatsApp Business API.</p>
 * <p><b>Priority:</b> Highest (1) - Fastest delivery, highest open rate.</p>
 *
 * <p><b>Requirements:</b></p>
 * <ul>
 *   <li>WhatsApp Business API account configured</li>
 *   <li>Verification template approved in Meta Business</li>
 *   <li>Phone number must have WhatsApp capability</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppStrategy implements VerificationStrategy {

    private final WhatsAppClient whatsAppClient;

    @Override
    public void sendVerificationCode(String recipient, String code) {
        log.info("Sending WhatsApp verification code to: {}", 
            PiiMaskingUtil.maskPhone(recipient));

        try {
            whatsAppClient.sendVerificationCode(recipient, code);
            log.info("✅ WhatsApp verification code sent successfully");
        } catch (Exception e) {
            log.error("❌ Failed to send WhatsApp verification code: {}", e.getMessage(), e);
            throw new RuntimeException("WhatsApp sending failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return whatsAppClient.isHealthy();
    }

    @Override
    public int priority() {
        return 1; // Highest priority
    }

    @Override
    public String name() {
        return "WhatsApp";
    }
}

