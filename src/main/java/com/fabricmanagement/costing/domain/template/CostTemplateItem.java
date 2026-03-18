package com.fabricmanagement.costing.domain.template;

import java.math.BigDecimal;

/**
 * Immutable value object stored as a JSONB element inside {@link CostTemplate#items}.
 *
 * <p>Each record links one {@code costItemCode} to this template with an optional relative {@code
 * weight} (e.g. 0.12 = 12 % of some base) and an {@code isIncluded} flag for soft-toggling without
 * removing the entry.
 */
public record CostTemplateItem(
    /** References {@code costing.cost_item.code}. */
    String costItemCode,

    /**
     * Fractional weight of this cost item in the template computation. For PERCENTAGE-based items
     * this doubles as the rate (e.g. 0.12 → 12 %). May be null if the item is always used at its
     * raw unit price.
     */
    BigDecimal weight,

    /** When false, the item is suppressed from calculation without being removed. */
    boolean isIncluded) {}
