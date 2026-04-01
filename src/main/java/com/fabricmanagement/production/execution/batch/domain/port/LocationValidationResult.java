package com.fabricmanagement.production.execution.batch.domain.port;

/**
 * Immutable result of a warehouse location validation for production.
 *
 * <p>Contains only the minimal data that production needs: whether the location is a valid
 * production location and its code for logging/events. Production never sees IWM enums or entities.
 *
 * @param locationId the validated location ID
 * @param locationCode the human-readable location code (e.g. "M-01", "PL-A")
 * @param validProductionLocation true if the location is MACHINE or PRODUCTION_LINE
 */
public record LocationValidationResult(
    java.util.UUID locationId, String locationCode, boolean validProductionLocation) {}
