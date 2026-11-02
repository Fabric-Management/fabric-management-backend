package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.platform.communication.infra.client.GoogleMapsClient;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real persistence round-trip and application tenant guards; RLS itself has its own IT suite. */
class AddressWriteIsolationIT extends AbstractIntegrationTest {
  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();
  @Autowired private AddressService addresses;
  @Autowired private AddressValidationService lookups;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private GoogleMapsClient google;
  @MockitoBean private DeliveryVerificationPolicy policy;

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbc.update(
        "DELETE FROM common_communication.common_address WHERE tenant_id IN (?, ?)",
        tenantA,
        tenantB);
  }

  @Test
  void lookupCreationRoundTripsSecondLineAndDoesNotGrantDeliveryApproval() {
    TenantContext.setCurrentTenantId(tenantA);
    when(policy.forCurrentActor()).thenReturn(Verification.unverified());
    AddressValidationResponse result =
        AddressValidationResponse.builder()
            .verificationStatus(AddressValidationResponse.VerificationStatus.VERIFIED)
            .streetAddress("14 Mill Lane")
            .flatNumber("Flat 4B")
            .city("Leeds")
            .country("United Kingdom")
            .countryCode("GB")
            .placeId("google-place-reference")
            .latitude(53.8)
            .longitude(-1.55)
            .build();
    Address created = addresses.createFromLookup(result, AddressType.HOME, "Home");
    Address saved = addresses.requireActiveAddress(created.getId());
    assertThat(saved.getAddressLine2()).isEqualTo("Flat 4B");
    assertThat(saved.getPlaceId()).isEqualTo("google-place-reference");
    assertThat(saved.getVerification().isVerified()).isFalse();
  }

  @Test
  void foreignTenantCannotRefreshOrEditAnAddressAndTheOriginalSurvives() {
    TenantContext.setCurrentTenantId(tenantA);
    when(policy.forCurrentActor()).thenReturn(Verification.unverified());
    Address created =
        addresses.createAddress(
            "14 Mill Lane",
            "Leeds",
            null,
            "LS1 1AA",
            "United Kingdom",
            AddressType.WAREHOUSE,
            "Dispatch");
    TenantContext.setCurrentTenantId(tenantB);
    assertThatThrownBy(() -> lookups.revalidateAddress(created.getId()))
        .isInstanceOf(PlatformDomainException.class);
    assertThatThrownBy(
            () ->
                addresses.updateAddress(
                    created.getId(),
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
    verifyNoInteractions(google);
    TenantContext.setCurrentTenantId(tenantA);
    assertThat(addresses.requireActiveAddress(created.getId()).getStreetAddress())
        .isEqualTo("14 Mill Lane");
  }

  @Test
  void contactOnlyUpdateRoundTripsAnExistingUnlabelledAddress() {
    TenantContext.setCurrentTenantId(tenantA);
    when(policy.forCurrentActor()).thenReturn(Verification.unverified());
    Address created =
        addresses.createAddress(
            "14 Mill Lane",
            "Leeds",
            null,
            "LS1 1AA",
            "United Kingdom",
            AddressType.WAREHOUSE,
            "Dispatch");
    // Reproduce a record written by the old lookup endpoint, which did not require a label.
    jdbc.update(
        "UPDATE common_communication.common_address SET label = NULL WHERE id = ?",
        created.getId());

    addresses.updateAddress(
        created.getId(),
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

    Address saved = addresses.requireActiveAddress(created.getId());
    assertThat(saved.getContactPhone()).isEqualTo("+44 113 496 0123");
    assertThat(saved.getLabel()).isNull();
    assertThat(saved.getStreetAddress()).isEqualTo("14 Mill Lane");
    assertThat(saved.getVerification().isVerified()).isFalse();
  }
}
