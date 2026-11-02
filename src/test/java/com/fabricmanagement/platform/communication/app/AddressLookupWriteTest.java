package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.platform.communication.infra.client.GoogleMapsClient;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;

class AddressLookupWriteTest {
  private final UUID tenant = UUID.randomUUID();
  private final AddressRepository repository = mock(AddressRepository.class);
  private final GoogleMapsClient google = mock(GoogleMapsClient.class);
  private final DeliveryVerificationPolicy policy = mock(DeliveryVerificationPolicy.class);
  private final AddressService addresses =
      new AddressService(
          repository,
          mock(OrganizationAddressRepository.class),
          mock(UserWorkLocationRepository.class),
          policy);
  private final AddressValidationService service = new AddressValidationService(google, addresses);

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenant);
    when(policy.forCurrentActor()).thenReturn(Verification.unverified());
    when(repository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  private AddressValidationResponse found() {
    return AddressValidationResponse.builder()
        .verificationStatus(AddressValidationResponse.VerificationStatus.VERIFIED)
        .streetAddress("14 Mill Lane")
        .flatNumber("Flat 4B")
        .city("Leeds")
        .country("United Kingdom")
        .countryCode("gb")
        .postalCode("LS1 1AA")
        .placeId("google-place-reference")
        .latitude(53.8)
        .longitude(-1.55)
        .build();
  }

  private Address existing() {
    Address address =
        Address.builder()
            .streetAddress("14 Mill Lane")
            .city("Leeds")
            .country("United Kingdom")
            .countryCode("GB")
            .addressType(AddressType.WAREHOUSE)
            .label("Dispatch")
            .addressLine2("Loading bay 2")
            .placeId("google-place-reference")
            .verification(Verification.verifiedBy(UUID.randomUUID(), Instant.now()))
            .build();
    address.setTenantId(tenant);
    address.setId(UUID.randomUUID());
    when(repository.findById(address.getId())).thenReturn(Optional.of(address));
    return address;
  }

  @Test
  void lookupCreationRetainsFlatAndCoordinatesWithoutGrantingDeliveryApproval() {
    when(google.validateByPlaceId("google-place-reference", null)).thenReturn(found());
    Address saved =
        service.validateAndCreateAddress(
            ValidateAddressRequest.builder()
                .placeId("google-place-reference")
                .addressType("warehouse")
                .label("Dispatch")
                .build());
    assertThat(saved.getAddressLine2()).isEqualTo("Flat 4B");
    assertThat(saved.getFormattedAddress()).contains("Flat 4B");
    assertThat(saved.getCountryCode()).isEqualTo("GB");
    assertThat(saved.getLatitude()).isEqualTo(53.8);
    assertThat(saved.getPlaceId()).isEqualTo("google-place-reference");
    assertThat(saved.getTenantId()).isEqualTo(tenant);
    assertThat(saved.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    verify(repository).save(saved);
  }

  @Test
  void lookupCreationUsesTheSameVerifierPolicyAsManualCreation() {
    UUID verifier = UUID.randomUUID();
    when(policy.forCurrentActor()).thenReturn(Verification.verifiedBy(verifier, Instant.now()));
    Address saved = addresses.createFromLookup(found(), AddressType.HOME, "Home");
    assertThat(saved.getVerifiedBy()).isEqualTo(verifier);
  }

  @Test
  void partialAreaResultCannotBePersistedAsACompletePostalAddress() {
    AddressValidationResponse area = found();
    area.setStreetAddress(null);
    area.setVerificationStatus(AddressValidationResponse.VerificationStatus.PARTIAL);
    assertThatThrownBy(() -> addresses.createFromLookup(area, AddressType.WAREHOUSE, "Dispatch"))
        .isInstanceOf(PlatformDomainException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void refreshKeepsEntranceInstructionsAndReappliesDeliveryPolicy() {
    Address address = existing();
    when(google.validateByPlaceId(address.getPlaceId(), null)).thenReturn(found());
    Address refreshed = service.revalidateAddress(address.getId());
    assertThat(refreshed.getAddressLine2()).isEqualTo("Loading bay 2");
    assertThat(refreshed.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(refreshed.getVerifiedBy()).isNull();
  }

  @Test
  void anotherTenantsAddressIsRejectedBeforeCallingGoogle() {
    Address address = existing();
    address.setTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> service.revalidateAddress(address.getId()))
        .isInstanceOf(PlatformDomainException.class);
    verifyNoInteractions(google);
    verify(repository, never()).save(any());
  }

  @Test
  void deletedAddressCannotBeRefreshed() {
    Address address = existing();
    address.setIsActive(false);
    assertThatThrownBy(() -> service.revalidateAddress(address.getId()))
        .isInstanceOf(PlatformDomainException.class);
    verifyNoInteractions(google);
  }

  @Test
  void invalidCoordinatesAreRejectedBeforeSaving() {
    AddressValidationResponse invalid = found();
    invalid.setLatitude(Double.NaN);
    assertThatThrownBy(() -> addresses.createFromLookup(invalid, AddressType.HOME, "Home"))
        .isInstanceOf(PlatformDomainException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void ordinaryUpdateInvalidatesMapMatchButKeepsUnchangedSecondLine() {
    Address address = existing();
    address.setLatitude(53.8);
    address.setLongitude(-1.55);
    Address updated =
        addresses.updateAddress(
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
            null);
    assertThat(updated.getPlaceId()).isNull();
    assertThat(updated.hasCoordinates()).isFalse();
    assertThat(updated.getAddressLine2()).isEqualTo("Loading bay 2");
    assertThat(updated.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
  }

  @Test
  void countryChangeWithoutANewCodeClearsTheOldCode() {
    Address address = existing();
    Address updated =
        addresses.updateAddress(
            address.getId(),
            null,
            null,
            null,
            null,
            null,
            null,
            "Germany",
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(updated.getCountryCode()).isNull();
  }

  @Test
  void nullProviderResultCannotOverwriteAnExistingAddress() {
    Address address = existing();
    address.setAddressLine2(null);
    when(google.validateByPlaceId(address.getPlaceId(), null)).thenReturn(null);

    assertThatThrownBy(() -> service.revalidateAddress(address.getId()))
        .isInstanceOf(PlatformDomainException.class);

    assertThat(address.getStreetAddress()).isEqualTo("14 Mill Lane");
    assertThat(address.getPlaceId()).isEqualTo("google-place-reference");
    assertThat(address.getAddressLine2()).isNull();
    assertThat(address.getVerification().isVerified()).isTrue();
    verify(repository, never()).save(any());
  }

  @Test
  void failedProviderResultCannotOverwriteAnExistingAddress() {
    Address address = existing();
    AddressValidationResponse failed =
        AddressValidationResponse.builder()
            .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
            .build();
    when(google.validateByPlaceId(address.getPlaceId(), null)).thenReturn(failed);
    assertThatThrownBy(() -> service.revalidateAddress(address.getId()))
        .isInstanceOf(PlatformDomainException.class);
    assertThat(address.getStreetAddress()).isEqualTo("14 Mill Lane");
    verify(repository, never()).save(any());
  }
}
