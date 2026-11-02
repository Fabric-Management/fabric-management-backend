package com.fabricmanagement.platform.communication.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import org.junit.jupiter.api.Test;

class AddressContentTest {
  private AddressContent content(String street, String secondLine, String label) {
    return new AddressContent(
        street,
        secondLine,
        "Leeds",
        null,
        null,
        "LS1 1AA",
        "United Kingdom",
        "gb",
        AddressType.WAREHOUSE,
        label);
  }

  private Address located() {
    Address address = Address.builder().build();
    address.replaceContent(content("14 Mill Lane", "Loading bay 2", "Dispatch"));
    address.setPlaceId("google-place-reference");
    address.setLatitude(53.8);
    address.setLongitude(-1.55);
    address.setFormattedAddress("Earlier provider text");
    return address;
  }

  @Test
  void editingPostalContentInvalidatesTheEarlierLocation() {
    Address address = located();
    address.replaceContent(content("26 Dock Road", "Loading bay 2", "Dispatch"));
    assertThat(address.getPlaceId()).isNull();
    assertThat(address.hasCoordinates()).isFalse();
    assertThat(address.getFormattedAddress())
        .contains("26 Dock Road", "Loading bay 2")
        .doesNotContain("Earlier provider text");
  }

  @Test
  void changingOnlyTheLabelPreservesTheLocation() {
    Address address = located();
    address.replaceContent(content("14 Mill Lane", "Loading bay 2", "Goods entrance"));
    assertThat(address.getPlaceId()).isEqualTo("google-place-reference");
    assertThat(address.hasCoordinates()).isTrue();
  }

  @Test
  void trimmingDoesNotRewriteBritishBuildingNumbers() {
    AddressContent value = content(" 14 Mill Lane ", " Flat 4B ", " Home ");
    assertThat(value.streetAddress()).isEqualTo("14 Mill Lane");
    assertThat(value.addressLine2()).isEqualTo("Flat 4B");
    assertThat(value.countryCode()).isEqualTo("GB");
  }

  @Test
  void invalidPostalContentIsRejectedForInternalCallersToo() {
    assertThatThrownBy(() -> content(" ", null, "Dispatch"))
        .isInstanceOf(PlatformDomainException.class);
    assertThatThrownBy(() -> content("x".repeat(501), null, "Dispatch"))
        .isInstanceOf(PlatformDomainException.class);
    assertThatThrownBy(() -> content("14 Mill Lane", null, " "))
        .isInstanceOf(PlatformDomainException.class);
  }
}
