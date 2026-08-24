package com.fabricmanagement.production.core.batch.domain.port;

import java.util.UUID;

/** Minimal production-owned view of an IWM location's QC custody eligibility. */
public record QcLocationValidationResult(
    UUID locationId, String locationCode, boolean validQcLocation) {}
