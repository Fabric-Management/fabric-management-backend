package com.fabricmanagement.common.infrastructure.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for validating duplicate entries in collections.
 * 
 * <p>Used to detect and log duplicate names/keys in lists that are sent to frontend,
 * preventing React key conflicts and data integrity issues.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * List<PositionDto> positions = ...;
 * DuplicateValidator.validateAndLog(positions, PositionDto::getPositionName, "positions");
 * }</pre>
 */
@Slf4j
public class DuplicateValidator {

    /**
     * Validates that all items in a list have unique keys based on a key extractor function.
     * Logs warnings for duplicates but does not throw exceptions (non-blocking).
     * 
     * @param <T> Type of items in the list
     * @param items List of items to validate
     * @param keyExtractor Function to extract the key from each item (e.g., PositionDto::getPositionName)
     * @param entityType Human-readable entity type name for logging (e.g., "positions", "departments")
     * @return true if no duplicates found, false if duplicates exist
     */
    public static <T> boolean validateAndLog(List<T> items, Function<T, String> keyExtractor, String entityType) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // Group items by key and find duplicates
        Map<String, List<T>> groupedByKey = items.stream()
            .collect(Collectors.groupingBy(keyExtractor));

        // Find keys with more than one item
        Map<String, List<T>> duplicates = groupedByKey.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (duplicates.isEmpty()) {
            return true;
        }

        // Log warnings for each duplicate
        log.warn("⚠️ Found {} duplicate key(s) in {} (total items: {}):", 
            duplicates.size(), entityType, items.size());
        
        duplicates.forEach((key, duplicateItems) -> {
            log.warn("  - Duplicate key '{}' found {} times:", key, duplicateItems.size());
            duplicateItems.forEach(item -> {
                String itemInfo = extractItemInfo(item);
                log.warn("    • {}", itemInfo);
            });
        });

        return false;
    }

    /**
     * Validates duplicates and throws exception if found (blocking).
     * 
     * @param <T> Type of items in the list
     * @param items List of items to validate
     * @param keyExtractor Function to extract the key from each item
     * @param entityType Human-readable entity type name for logging
     * @throws IllegalStateException if duplicates are found
     */
    public static <T> void validateOrThrow(List<T> items, Function<T, String> keyExtractor, String entityType) {
        if (!validateAndLog(items, keyExtractor, entityType)) {
            throw new IllegalStateException(
                String.format("Duplicate keys found in %s. Check logs for details.", entityType));
        }
    }

    /**
     * Extracts a human-readable string representation of an item for logging.
     * Attempts to extract ID and name fields using reflection-like approach.
     */
    private static <T> String extractItemInfo(T item) {
        if (item == null) {
            return "null";
        }

        try {
            // Try to get ID and name using common patterns
            String className = item.getClass().getSimpleName();
            
            // Try to invoke getId() if exists
            try {
                java.lang.reflect.Method getIdMethod = item.getClass().getMethod("getId");
                Object id = getIdMethod.invoke(item);
                if (id != null) {
                    return String.format("%s[id=%s]", className, id);
                }
            } catch (Exception ignored) {
                // Ignore if getId() doesn't exist
            }

            // Fallback to toString()
            return item.toString();
        } catch (Exception e) {
            return item.toString();
        }
    }

    /**
     * Validates multiple lists at once and returns a summary.
     * 
     * @param validations Map of entity type name to validation result
     * @return true if all validations passed, false otherwise
     */
    public static boolean validateAll(Map<String, Boolean> validations) {
        boolean allValid = validations.values().stream().allMatch(Boolean::booleanValue);
        
        if (!allValid) {
            log.warn("⚠️ Some duplicate validations failed. Check logs above for details.");
        } else {
            log.debug("✅ All duplicate validations passed.");
        }
        
        return allValid;
    }
}

