package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService")
class AddressServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private AddressRepository addressRepository;
  @Mock private OrganizationAddressRepository organizationAddressRepository;
  @Mock private UserWorkLocationRepository userWorkLocationRepository;
  @Mock private DeliveryVerificationPolicy verificationPolicy;

  @InjectMocks private AddressService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    lenient().when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createAddress")
  class CreateAddress {

    @Test
    void createsAndSavesAddress() {
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

      Address result =
          service.createAddress(
              "14 Mill Lane",
              "Leeds",
              "Leeds",
              "LS1 1AA",
              "United Kingdom",
              AddressType.HEADQUARTERS,
              "HQ");

      ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
      verify(addressRepository).save(captor.capture());
      Address saved = captor.getValue();
      assertThat(saved.getStreetAddress()).isEqualTo("14 Mill Lane");
      assertThat(saved.getCity()).isEqualTo("Leeds");
      assertThat(saved.getState()).isEqualTo("Leeds");
      assertThat(saved.getPostalCode()).isEqualTo("LS1 1AA");
      assertThat(saved.getCountry()).isEqualTo("United Kingdom");
      assertThat(saved.getAddressType()).isEqualTo(AddressType.HEADQUARTERS);
      assertThat(saved.getLabel()).isEqualTo("HQ");
      assertThat(result).isSameAs(saved);
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void returnsEmptyWhenAddressBelongsToOtherTenant() {
      UUID addressId = UUID.randomUUID();
      Address other = Address.builder().build();
      other.setId(addressId);
      other.setTenantId(UUID.randomUUID());
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(other));

      Optional<Address> result = service.findById(addressId);

      assertThat(result).isEmpty();
    }

    @Test
    void returnsAddressWhenSameTenant() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));

      Optional<Address> result = service.findById(addressId);

      assertThat(result).contains(address);
    }
  }

  @Nested
  @DisplayName("updateAddress")
  class UpdateAddress {

    @Test
    void updatesFieldsAndSaves() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().streetAddress("Old").city("OldCity").build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

      Address result =
          service.updateAddress(
              addressId,
              "26 Dock Road",
              null,
              "York",
              "North Yorkshire",
              null,
              "YO1 1AA",
              "United Kingdom",
              null,
              AddressType.OFFICE,
              "New Label",
              null,
              null,
              null);

      verify(addressRepository).save(address);
      assertThat(address.getStreetAddress()).isEqualTo("26 Dock Road");
      assertThat(address.getCity()).isEqualTo("York");
      assertThat(address.getState()).isEqualTo("North Yorkshire");
      assertThat(address.getPostalCode()).isEqualTo("YO1 1AA");
      assertThat(address.getCountry()).isEqualTo("United Kingdom");
      assertThat(address.getLabel()).isEqualTo("New Label");
      assertThat(result).isSameAs(address);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void contactOnlyEditOnHistoricalUnlabelledCardPreservesPostalContent(String label) {
    Address address = historicalCard(label);
    when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

    Address updated = updateContactPhone(address.getId());

    assertThat(updated.getContactPhone()).isEqualTo("+44 113 496 0123");
    assertThat(updated.getLabel()).isEqualTo(label);
    assertThat(updated.getStreetAddress()).isEqualTo("14 Mill Lane");
    assertThat(updated.getAddressLine2()).isEqualTo("Loading bay 2");
    assertThat(updated.getPlaceId()).isEqualTo("google-place-reference");
    assertThat(updated.getLatitude()).isEqualTo(53.8);
    assertThat(updated.getLongitude()).isEqualTo(-1.55);
    assertThat(updated.getVerification().isVerified()).isFalse();
    verify(addressRepository).save(address);
  }

  @Test
  void contactOnlyEditDoesNotInventMissingHistoricalStreet() {
    Address address = historicalCard(null);
    address.setStreetAddress("");
    when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

    Address updated = updateContactPhone(address.getId());

    assertThat(updated.getStreetAddress()).isEmpty();
    assertThat(updated.getLabel()).isNull();
    assertThat(updated.getContactPhone()).isEqualTo("+44 113 496 0123");
  }

  @Test
  void contactOnlyEditStillRejectsAnotherTenantsCard() {
    Address address = historicalCard(null);
    address.setTenantId(UUID.randomUUID());

    assertThatThrownBy(() -> updateContactPhone(address.getId()))
        .isInstanceOf(PlatformDomainException.class);

    assertThat(address.getContactPhone()).isNull();
    verify(addressRepository, never()).save(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void newAddressStillRequiresALabel(String label) {
    assertThatThrownBy(
            () ->
                service.createAddress(
                    "14 Mill Lane",
                    "Leeds",
                    null,
                    "LS1 1AA",
                    "United Kingdom",
                    AddressType.WAREHOUSE,
                    label))
        .isInstanceOf(PlatformDomainException.class);
    verify(addressRepository, never()).save(any());
  }

  @Test
  void postalEditOfUnlabelledCardStillRequiresCompletion() {
    Address address = historicalCard(null);

    assertThatThrownBy(
            () ->
                service.updateAddress(
                    address.getId(),
                    "26 Dock Road",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(PlatformDomainException.class);

    assertThat(address.getStreetAddress()).isEqualTo("14 Mill Lane");
    assertThat(address.getPlaceId()).isEqualTo("google-place-reference");
    verify(addressRepository, never()).save(any());
  }

  private Address historicalCard(String label) {
    Address address =
        Address.builder()
            .streetAddress("14 Mill Lane")
            .addressLine2("Loading bay 2")
            .city("Leeds")
            .country("United Kingdom")
            .countryCode("GB")
            .addressType(AddressType.WAREHOUSE)
            .label(label)
            .placeId("google-place-reference")
            .latitude(53.8)
            .longitude(-1.55)
            .verification(Verification.predating())
            .build();
    address.setId(UUID.randomUUID());
    address.setTenantId(TENANT_ID);
    when(addressRepository.findById(address.getId())).thenReturn(Optional.of(address));
    return address;
  }

  private Address updateContactPhone(UUID id) {
    return service.updateAddress(
        id,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "+44 113 496 0123",
        null);
  }

  @Nested
  @DisplayName("deleteAddress")
  class DeleteAddress {

    @Test
    void softDeletesAddress() {
      UUID addressId = UUID.randomUUID();
      Address address = Address.builder().build();
      address.setId(addressId);
      address.setTenantId(TENANT_ID);
      when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
      when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
      when(organizationAddressRepository.findByAddressIdIncludingDeleted(addressId))
          .thenReturn(Optional.empty());

      service.deleteAddress(addressId);

      verify(addressRepository).save(address);
      assertThat(address.getIsActive()).isFalse();
    }
  }
}
