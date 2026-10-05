package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An address as used on the order (ADR-0014 D11). It is copied at the time of use, optionally from
 * a partner's address card ({@code sourceAddressId}); later edits of the card never change it.
 *
 * <p>A draft keeps what is known ("the producer in Manchester" is a city only): every part is
 * optional here and entered parts are checked for form. Completeness is asked by the gate that
 * needs it ({@link #isComplete()}), not at entry (D1).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddressSnapshot {

  public static final int MAX_LINE = 200;
  public static final int MAX_CITY = 100;
  public static final int MAX_REGION = 100;
  public static final int MAX_POSTAL_CODE = 20;

  private String line1;
  private String line2;
  private String city;
  private String region;
  private String postalCode;
  private String countryCode;
  private UUID sourceAddressId;

  /** The address as entered, or null when nothing was entered. */
  public static AddressSnapshot of(
      String line1,
      String line2,
      String city,
      String region,
      String postalCode,
      String countryCode,
      UUID sourceAddressId) {
    String first = Text.trimmed(line1);
    String second = Text.trimmed(line2);
    String town = Text.trimmed(city);
    String area = Text.trimmed(region);
    String postal = Text.trimmed(postalCode);
    String country = Text.trimmed(countryCode);
    if (first == null
        && second == null
        && town == null
        && area == null
        && postal == null
        && country == null) {
      if (sourceAddressId != null) {
        throw new OrderDomainException("An address copied from a card needs its content");
      }
      return null;
    }
    if (country != null) {
      country = country.toUpperCase(Locale.ROOT);
      if (!country.matches("[A-Z]{2}")) {
        throw new OrderDomainException("Give the country as its two-letter ISO code");
      }
    }
    AddressSnapshot value = new AddressSnapshot();
    value.line1 = Text.limited(first, MAX_LINE, "The address line is too long");
    value.line2 = Text.limited(second, MAX_LINE, "The address line is too long");
    value.city = Text.limited(town, MAX_CITY, "The city is too long");
    value.region = Text.limited(area, MAX_REGION, "The region is too long");
    value.postalCode = Text.limited(postal, MAX_POSTAL_CODE, "The postal code is too long");
    value.countryCode = country;
    value.sourceAddressId = sourceAddressId;
    return value;
  }

  /** Enough to deliver or invoice to: a street line, the city and the country. */
  public boolean isComplete() {
    return line1 != null && city != null && countryCode != null;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AddressSnapshot that
        && Objects.equals(line1, that.line1)
        && Objects.equals(line2, that.line2)
        && Objects.equals(city, that.city)
        && Objects.equals(region, that.region)
        && Objects.equals(postalCode, that.postalCode)
        && Objects.equals(countryCode, that.countryCode)
        && Objects.equals(sourceAddressId, that.sourceAddressId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(line1, line2, city, region, postalCode, countryCode, sourceAddressId);
  }
}
