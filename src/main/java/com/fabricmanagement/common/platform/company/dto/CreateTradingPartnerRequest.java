package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.PartnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new trading partner.
 *
 * <p>The system automatically handles registry deduplication based on tax_id + country.
 *
 * <h2>Deduplication:</h2>
 *
 * <ul>
 *   <li>If tax_id is provided and matches existing registry, links to existing
 *   <li>If tax_id is null, creates new registry (no deduplication possible)
 *   <li>If same registry already exists for this tenant, upgrades to BOTH
 * </ul>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * {
 *   "companyName": "Akkaya Tekstil A.Ş.",
 *   "taxId": "1234567890",
 *   "country": "TUR",
 *   "partnerType": "SUPPLIER",
 *   "customName": "Ana İplik Tedarikçimiz",
 *   "relationshipMeta": {
 *     "payment_terms": "NET30",
 *     "credit_limit": 100000,
 *     "contact_person": "Ahmet Yılmaz",
 *     "contact_email": "ahmet@akkaya.com"
 *   }
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTradingPartnerRequest {

  /** Official company name (used for registry if new) */
  @NotBlank(message = "Company name is required")
  @Size(max = 255, message = "Company name must not exceed 255 characters")
  private String companyName;

  /**
   * Tax identification number (optional).
   *
   * <p>When provided, enables cross-tenant deduplication. Partners with same tax_id + country share
   * a single registry record.
   */
  @Size(max = 50, message = "Tax ID must not exceed 50 characters")
  private String taxId;

  /**
   * ISO 3166-1 alpha-3 country code (e.g., TUR, USA, DEU).
   *
   * <p>Defaults to "TUR" if not provided.
   */
  @Size(max = 3, message = "Country code must be 3 characters")
  private String country;

  /** Type of business relationship */
  @NotNull(message = "Partner type is required")
  private PartnerType partnerType;

  /**
   * Custom name/alias for this partner (optional).
   *
   * <p>Allows tenants to use their own naming while registry maintains official name.
   */
  @Size(max = 255, message = "Custom name must not exceed 255 characters")
  private String customName;

  /**
   * Relationship-specific metadata (optional).
   *
   * <p>Examples: payment_terms, credit_limit, discount_rate, contact_person, notes
   */
  private Map<String, Object> relationshipMeta;
}
