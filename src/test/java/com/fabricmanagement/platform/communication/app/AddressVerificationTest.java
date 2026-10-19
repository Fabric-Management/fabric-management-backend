package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CONSIGNEE-1 C5/C8: an address card is verified by who made it, or by a verifier later. */
@ExtendWith(MockitoExtension.class)
class AddressVerificationTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID VERIFIER = UUID.randomUUID();

  @Mock private AddressRepository addressRepository;
  @Mock private OrganizationAddressRepository organizationAddressRepository;
  @Mock private UserWorkLocationRepository userWorkLocationRepository;
  @Mock private DeliveryVerificationPolicy verificationPolicy;

  @InjectMocks private AddressService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Address create() {
    when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
    return service.createAddress(
        "1 Mill Lane", "Leeds", null, "LS1 1AA", "United Kingdom", AddressType.SHIPPING, "Dock");
  }

  private Address card(Verification state, UUID tenant) {
    Address address =
        Address.builder()
            .streetAddress("1 Mill Lane")
            .city("Leeds")
            .country("United Kingdom")
            .addressType(AddressType.SHIPPING)
            .verification(state)
            .build();
    address.setId(UUID.randomUUID());
    address.setTenantId(tenant);
    when(addressRepository.findById(address.getId())).thenReturn(Optional.of(address));
    return address;
  }

  private Address update(Address address) {
    when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
    return service.updateAddress(
        address.getId(),
        "2 Dock Road",
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
  }

  @Test
  void aCardMadeByAUserWithoutThePermissionIsUnverified() {
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());

    Address created = create();

    assertThat(created.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(created.getVerifiedBy()).isNull();
  }

  @Test
  void aCardMadeByAVerifierIsVerifiedByThem() {
    Instant at = Instant.parse("2026-10-16T09:00:00Z");
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.verifiedBy(VERIFIER, at));

    Address created = create();

    assertThat(created.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(created.getVerifiedBy()).isEqualTo(VERIFIER);
    assertThat(created.getVerifiedAt()).isEqualTo(at);
  }

  @Test
  void aCardIsNotVerifiedMerelyBecauseAMapServiceRecognisedIt() {
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());
    Address created = create();
    created.setPlaceId("ChIJ-recognised");

    assertThat(created.isVerified()).isTrue();
    assertThat(created.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
  }

  @Test
  void aVerifiedCardEditedByAUserWithoutThePermissionIsUnverifiedAgain() {
    Address address = card(Verification.verifiedBy(VERIFIER, Instant.now()), TENANT);
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());

    Address updated = update(address);

    assertThat(updated.getStreetAddress()).isEqualTo("2 Dock Road");
    assertThat(updated.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(updated.getVerifiedBy()).isNull();
  }

  @Test
  void aCardEditedByAVerifierIsVerified() {
    Address address = card(Verification.unverified(), TENANT);
    when(verificationPolicy.forCurrentActor())
        .thenReturn(Verification.verifiedBy(VERIFIER, Instant.now()));

    Address updated = update(address);

    assertThat(updated.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(updated.getVerifiedBy()).isEqualTo(VERIFIER);
  }

  @Test
  void verifyingAnUnverifiedCardRecordsWhoAndWhen() {
    Address address = card(Verification.unverified(), TENANT);

    boolean changed = service.verify(address.getId(), VERIFIER);

    assertThat(changed).isTrue();
    assertThat(address.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(address.getVerifiedBy()).isEqualTo(VERIFIER);
    assertThat(address.getVerifiedAt()).isNotNull();
    ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
    verify(addressRepository).save(saved.capture());
    assertThat(saved.getValue()).isSameAs(address);
  }

  @Test
  void verifyingAVerifiedCardChangesNothing() {
    Verification earlier = Verification.verifiedBy(UUID.randomUUID(), Instant.now());
    Address address = card(earlier, TENANT);

    boolean changed = service.verify(address.getId(), VERIFIER);

    assertThat(changed).isFalse();
    assertThat(address.getVerification()).isSameAs(earlier);
    verify(addressRepository, never()).save(any());
  }

  @Test
  void aCardOfAnotherTenantCannotBeVerified() {
    Address foreign = card(Verification.unverified(), UUID.randomUUID());

    assertThatThrownBy(() -> service.verify(foreign.getId(), VERIFIER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(foreign.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
  }

  @Test
  void theVerificationOfACardIsReadOnlyForThisTenant() {
    Address mine = card(Verification.unverified(), TENANT);
    Address foreign = card(Verification.predating(), UUID.randomUUID());

    assertThat(service.verificationOf(mine.getId()))
        .contains(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(service.verificationOf(foreign.getId())).isEmpty();
    assertThat(service.verificationOf(UUID.randomUUID())).isEmpty();
  }
}
