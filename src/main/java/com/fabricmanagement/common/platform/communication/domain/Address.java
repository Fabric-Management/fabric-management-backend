package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Address entity - Generic address information for User and Company.
 *
 * <p>Represents physical addresses that can be associated with either a User (HOME, WORK) or a
 * Company (HEADQUARTERS, BRANCH, WAREHOUSE, etc.).
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>✅ Multiple address types (HOME, WORK, HEADQUARTERS, BRANCH, etc.)
 *   <li>✅ Primary address flag
 *   <li>✅ Label for categorization
 *   <li>✅ Full address components (street, city, state, postal, country)
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * // User's home address
 * Address homeAddress = Address.builder()
 *     .streetAddress("123 Main St, Apt 4B")
 *     .city("Istanbul")
 *     .state("Istanbul")
 *     .postalCode("34000")
 *     .country("Turkey")
 *     .addressType(AddressType.HOME)
 *     .label("Home")
 *     .build();
 *
 * // Company headquarters
 * Address hqAddress = Address.builder()
 *     .streetAddress("456 Business Ave, Floor 10")
 *     .city("Istanbul")
 *     .state("Istanbul")
 *     .postalCode("34000")
 *     .country("Turkey")
 *     .addressType(AddressType.HEADQUARTERS)
 *     .isPrimary(true)
 *     .label("Main Headquarters")
 *     .build();
 * }</pre>
 */
@Entity
@Table(
    name = "common_address",
    schema = "common_communication",
    indexes = {
      @Index(name = "idx_address_type", columnList = "address_type"),
      @Index(name = "idx_address_city", columnList = "city"),
      @Index(name = "idx_address_country", columnList = "country"),
      @Index(name = "idx_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address extends BaseEntity {

  /**
   * Street address (full street address with building/apartment number)
   *
   * <p>Example: "123 Main St, Apt 4B" or "456 Business Ave, Floor 10"
   */
  @Column(name = "street_address", nullable = false, length = 500)
  private String streetAddress;

  /** City name */
  @Column(name = "city", nullable = false, length = 100)
  private String city;

  /**
   * State/Province/Region
   *
   * <p>Examples: "Istanbul", "California", "Ontario"
   */
  @Column(name = "state", length = 100)
  private String state;

  /** Postal/ZIP code */
  @Column(name = "postal_code", length = 20)
  private String postalCode;

  /**
   * Country name
   *
   * <p>Examples: "Turkey", "United States", "Canada"
   */
  @Column(name = "country", nullable = false, length = 100)
  private String country;

  /**
   * ISO 3166-1 alpha-2 country code
   *
   * <p>Examples: "TR", "GB", "DE", "FR", "US"
   *
   * <p>Required for address validation and standardization
   */
  @Column(name = "country_code", length = 2)
  private String countryCode;

  /**
   * District/County/Sub-administrative area
   *
   * <p>Examples: "Brooklyn", "Westminster", "Bavaria"
   */
  @Column(name = "district", length = 100)
  private String district;

  /** Second address line: apartment, floor, suite, building name */
  @Column(name = "address_line2", length = 255)
  private String addressLine2;

  /** Latitude coordinate (from Google Geocoding) */
  @Column(name = "latitude")
  private Double latitude;

  /** Longitude coordinate (from Google Geocoding) */
  @Column(name = "longitude")
  private Double longitude;

  /**
   * Google Places ID
   *
   * <p>Unique identifier for the address in Google Places database
   */
  @Column(name = "place_id", length = 255)
  private String placeId;

  /** Address type (HOME, WORK, HEADQUARTERS, BRANCH, etc.) */
  @Enumerated(EnumType.STRING)
  @Column(name = "address_type", nullable = false, length = 50)
  private AddressType addressType;

  /**
   * Label for categorization
   *
   * <p>Examples: "Home", "Work", "Main Headquarters", "Warehouse A", "Shipping Address"
   */
  @Column(name = "label", length = 100)
  private String label;

  /**
   * Optional location contact: depo/şube telefonu.
   *
   * <p>Replaces AddressContact junction for simple address-level contact (YAGNI).
   */
  @Column(name = "contact_phone", length = 50)
  private String contactPhone;

  /** Optional location contact: depo/şube email. */
  @Column(name = "contact_email", length = 255)
  private String contactEmail;

  /** Optional contact person: irtibat kişisi. */
  @Column(name = "contact_person", length = 100)
  private String contactPerson;

  /**
   * Google's formatted address (canonical format)
   *
   * <p>Stored separately for UI display and consistency
   */
  @Column(name = "formatted_address", length = 500)
  private String formattedAddress;

  /**
   * Get full address as formatted string
   *
   * <p>Uses formattedAddress if available (from Google), otherwise constructs from components
   */
  public String getFormattedAddress() {
    // If we have a stored formatted address (from Google), use it
    // Otherwise, construct from components
    StringBuilder sb = new StringBuilder();
    sb.append(streetAddress);
    if (district != null && !district.isBlank()) {
      sb.append(", ").append(district);
    }
    if (city != null && !city.isBlank()) {
      sb.append(", ").append(city);
    }
    if (state != null && !state.isBlank()) {
      sb.append(", ").append(state);
    }
    if (postalCode != null && !postalCode.isBlank()) {
      sb.append(" ").append(postalCode);
    }
    if (country != null && !country.isBlank()) {
      sb.append(", ").append(country);
    }
    return sb.toString();
  }

  /** Check if address has geolocation data */
  public boolean hasCoordinates() {
    return latitude != null && longitude != null;
  }

  /** Check if address is verified (has placeId) */
  public boolean isVerified() {
    return placeId != null && !placeId.isBlank();
  }

  @Override
  protected String getModuleCode() {
    return "ADDR";
  }
}
