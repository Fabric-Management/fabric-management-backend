package com.fabricmanagement.common.platform.company.domain;

import java.util.Map;
import java.util.Set;

/**
 * Validator for pricing tier names based on OS type.
 *
 * <p>Each OS has its own valid tier names. This utility class provides
 * validation and default tier selection based on the OS.</p>
 *
 * <h2>Tier Naming by OS:</h2>
 * <ul>
 *   <li><strong>YarnOS, LoomOS, KnitOS, DyeOS, EdgeOS:</strong> Starter, Professional, Enterprise</li>
 *   <li><strong>AnalyticsOS:</strong> Standard, Advanced, Enterprise</li>
 *   <li><strong>AccountOS, CustomOS:</strong> Standard, Professional, Enterprise</li>
 *   <li><strong>IntelligenceOS:</strong> Professional, Enterprise (no starter tier)</li>
 *   <li><strong>FabricOS:</strong> Base (single tier, included for all)</li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // Validate tier for YarnOS
 * boolean isValid = PricingTierValidator.isValidTier("YarnOS", "Professional"); // true
 * boolean isInvalid = PricingTierValidator.isValidTier("YarnOS", "Standard"); // false
 *
 * // Get default tier
 * String defaultTier = PricingTierValidator.getDefaultTier("AnalyticsOS"); // "Standard"
 * }</pre>
 */
public class PricingTierValidator {

    /**
     * Map of OS codes to their valid pricing tier names.
     */
    private static final Map<String, Set<String>> OS_VALID_TIERS = Map.of(
        "YarnOS", Set.of("Starter", "Professional", "Enterprise"),
        "LoomOS", Set.of("Starter", "Professional", "Enterprise"),
        "KnitOS", Set.of("Starter", "Professional", "Enterprise"),
        "DyeOS", Set.of("Starter", "Professional", "Enterprise"),
        "EdgeOS", Set.of("Starter", "Professional", "Enterprise"),
        "AnalyticsOS", Set.of("Standard", "Advanced", "Enterprise"),
        "AccountOS", Set.of("Standard", "Professional", "Enterprise"),
        "CustomOS", Set.of("Standard", "Professional", "Enterprise"),
        "IntelligenceOS", Set.of("Professional", "Enterprise"),
        "FabricOS", Set.of("Base")
    );

    /**
     * Check if a tier name is valid for the given OS.
     *
     * @param osCode the OS code (e.g., "YarnOS")
     * @param tierName the tier name to validate (e.g., "Professional")
     * @return true if the tier is valid for this OS
     */
    public static boolean isValidTier(String osCode, String tierName) {
        if (osCode == null || tierName == null) {
            return false;
        }

        Set<String> validTiers = OS_VALID_TIERS.get(osCode);
        if (validTiers == null) {
            // Unknown OS - allow any tier (for extensibility)
            return true;
        }

        return validTiers.contains(tierName);
    }

    /**
     * Get all valid tier names for a given OS.
     *
     * @param osCode the OS code (e.g., "YarnOS")
     * @return set of valid tier names, or empty set if OS is unknown
     */
    public static Set<String> getValidTiers(String osCode) {
        return OS_VALID_TIERS.getOrDefault(osCode, Set.of());
    }

    /**
     * Get the default (lowest/entry-level) tier for a given OS.
     *
     * @param osCode the OS code
     * @return the default tier name
     */
    public static String getDefaultTier(String osCode) {
        if (osCode == null) {
            return "Starter";
        }

        return switch (osCode) {
            case "YarnOS", "LoomOS", "KnitOS", "DyeOS", "EdgeOS" -> "Starter";
            case "AnalyticsOS", "AccountOS", "CustomOS" -> "Standard";
            case "IntelligenceOS" -> "Professional";
            case "FabricOS" -> "Base";
            default -> "Starter"; // Fallback for unknown OS
        };
    }

    /**
     * Get the tier hierarchy level (0 = lowest, higher = more premium).
     *
     * <p>Useful for comparing tiers or checking if upgrade is required.</p>
     *
     * @param tierName the tier name
     * @return hierarchy level (0-2), or -1 if unknown
     */
    public static int getTierLevel(String tierName) {
        if (tierName == null) {
            return -1;
        }

        return switch (tierName) {
            case "Starter", "Standard", "Base" -> 0;
            case "Professional", "Advanced" -> 1;
            case "Enterprise" -> 2;
            default -> -1;
        };
    }

    /**
     * Check if a tier meets or exceeds a minimum required tier.
     *
     * <p>Example: "Enterprise" meets minimum of "Professional" → true</p>
     *
     * @param currentTier the current tier
     * @param minimumTier the minimum required tier
     * @return true if current tier meets or exceeds minimum
     */
    public static boolean meetsMinimumTier(String currentTier, String minimumTier) {
        int currentLevel = getTierLevel(currentTier);
        int minimumLevel = getTierLevel(minimumTier);

        if (currentLevel == -1 || minimumLevel == -1) {
            return false;
        }

        return currentLevel >= minimumLevel;
    }

    /**
     * Check if the OS has a specific tier available.
     *
     * @param osCode the OS code
     * @param tierName the tier name to check
     * @return true if the OS offers this tier
     */
    public static boolean osSupportsTier(String osCode, String tierName) {
        return isValidTier(osCode, tierName);
    }

    /**
     * Get a user-friendly display name for a tier.
     *
     * @param tierName the tier name
     * @return display name with description
     */
    public static String getTierDisplayName(String tierName) {
        if (tierName == null) {
            return "Unknown";
        }

        return switch (tierName) {
            case "Starter" -> "Starter (Entry-level)";
            case "Standard" -> "Standard (Entry-level)";
            case "Professional" -> "Professional (Advanced)";
            case "Advanced" -> "Advanced (Premium)";
            case "Enterprise" -> "Enterprise (Full-featured)";
            case "Base" -> "Base (Included)";
            default -> tierName;
        };
    }
}

