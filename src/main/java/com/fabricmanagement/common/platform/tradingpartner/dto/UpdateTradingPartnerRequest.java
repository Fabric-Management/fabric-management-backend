package com.fabricmanagement.common.platform.tradingpartner.dto;

import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing trading partner relationship.
 *
 * <p>Only tenant-side relationship data can be updated. Registry-level data (officialName, taxId,
 * country) is immutable from the tenant perspective and belongs to the platform-level registry.
 *
 * <h2>Updatable fields:</h2>
 *
 * <ul>
 *   <li>{@code customName} — tenant alias for this partner
 *   <li>{@code partnerType} — business relationship type
 *   <li>{@code relationshipMeta} — payment terms, credit limits, notes, etc.
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTradingPartnerRequest {

  /**
   * Tenant's custom name/alias for this partner (optional).
   *
   * <p>Set to {@code null} or empty to clear the alias and fall back to the official name.
   */
  @Size(max = 255, message = "Custom name must not exceed 255 characters")
  private String customName;

  /** Type of business relationship. If {@code null}, the existing type is kept. */
  private PartnerType partnerType;

  /**
   * Relationship-specific metadata (optional).
   *
   * <p>Replaces the existing metadata entirely when provided. Set to {@code null} to leave metadata
   * unchanged. Examples: payment_terms, credit_limit, discount_rate, notes.
   */
  private Map<String, Object> relationshipMeta;
}
