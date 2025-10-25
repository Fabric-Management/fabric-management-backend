package com.fabricmanagement.common.platform.communication.domain.strategy;

/**
 * Verification strategy interface - Multi-channel verification.
 *
 * <p>Priority order:
 * <ol>
 *   <li>WhatsApp (priority = 1) - Fastest, highest open rate</li>
 *   <li>Email (priority = 2) - Universal, professional</li>
 *   <li>SMS (priority = 3) - Fallback, reliable</li>
 * </ol>
 *
 * <h2>Implementation Example:</h2>
 * <pre>{@code
 * @Component
 * public class WhatsAppStrategy implements VerificationStrategy {
 *     @Override
 *     public void sendVerificationCode(String recipient, String code) {
 *         // WhatsApp Business API call
 *     }
 *
 *     @Override
 *     public boolean isAvailable() {
 *         return whatsappConfigured && whatsappApiHealthy;
 *     }
 *
 *     @Override
 *     public int priority() {
 *         return 1; // Highest priority
 *     }
 * }
 * }</pre>
 */
public interface VerificationStrategy {

    /**
     * Send verification code to recipient.
     *
     * @param recipient email or phone number
     * @param code 6-digit verification code
     */
    void sendVerificationCode(String recipient, String code);

    /**
     * Check if this strategy is available/configured.
     *
     * @return true if strategy can be used
     */
    boolean isAvailable();

    /**
     * Strategy priority (1 = highest).
     *
     * @return priority number
     */
    int priority();

    /**
     * Strategy name for logging.
     *
     * @return strategy name
     */
    String name();
}

