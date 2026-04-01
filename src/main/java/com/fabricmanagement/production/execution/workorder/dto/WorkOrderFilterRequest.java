package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter criteria for WorkOrder listing.
 *
 * <p>All fields are optional — null values are ignored during query building. Combines with {@code
 * Pageable} for paginated, filtered results.
 */
@Schema(description = "Optional filter criteria for WorkOrder listing")
public record WorkOrderFilterRequest(
    @Schema(description = "Filter by WorkOrder status", example = "IN_PROGRESS")
        WorkOrderStatus status,
    @Schema(description = "Filter by customer/supplier trading partner") UUID tradingPartnerId,
    @Schema(description = "Filter by linked sales order") UUID salesOrderId,
    @Schema(description = "Filter by production recipe") UUID recipeId,
    @Schema(
            description = "Case-insensitive partial match on work order number or product code",
            example = "WO-2026")
        String searchText,
    @Schema(
            description = "Deadline range start (inclusive, ISO 8601)",
            example = "2026-04-01T00:00:00Z")
        Instant deadlineFrom,
    @Schema(
            description = "Deadline range end (inclusive, ISO 8601)",
            example = "2026-04-07T23:59:59Z")
        Instant deadlineTo,
    @Schema(description = "Creation date range start (inclusive, ISO 8601)") Instant createdFrom,
    @Schema(description = "Creation date range end (inclusive, ISO 8601)") Instant createdTo) {}
