package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityBasis;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.ApprovalAuthorityView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.GrantApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RevokeApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.ApprovalAuthorityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Granting keeps the address the grantor confirmed and the contact point reads; revoking takes the
 * authority's row lock (ADR-0014 OD-13).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalAuthorityServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID ADDRESS = UUID.randomUUID();
  private static final UUID MANAGER = UUID.randomUUID();

  @Mock private ApprovalAuthorityRepository authorities;
  @Mock private TradingPartnerService partners;
  @Mock private OrganizationContactFacade contacts;

  private ApprovalAuthorityService service;
  private String cardReads = "ann.lee@example.com";

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new ApprovalAuthorityService(
            authorities, partners, contacts, Clock.fixed(NOW, ZoneOffset.UTC));
    when(partners.findById(TENANT, CUSTOMER))
        .thenReturn(Optional.of(TradingPartnerDto.builder().organizationId(ORGANIZATION).build()));
    when(contacts.getOrganizationContacts(ORGANIZATION))
        .thenAnswer(
            invocation ->
                List.of(
                    OrganizationContactDto.builder()
                        .organizationId(ORGANIZATION)
                        .contactId(ADDRESS)
                        .contact(
                            OrganizationContactDto.ContactData.builder()
                                .id(ADDRESS)
                                .contactType(ContactType.EMAIL)
                                .contactValue(cardReads)
                                .build())
                        .build()));
    when(authorities.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ApprovalAuthority value = invocation.getArgument(0);
              ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
              return value;
            });
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void theAuthorityIsGrantedForTheConfirmedAddressTheContactPointReads() {
    ApprovalAuthorityView view = service.grant(CUSTOMER, request(" Ann.Lee@example.com "), MANAGER);

    assertThat(view.authorisedEmail()).isEqualTo("ann.lee@example.com");
    assertThat(view.contactAddressChanged()).isFalse();
    assertThat(view.active()).isTrue();
  }

  @Test
  void anAddressTheContactPointNoLongerReadsIsNotGranted() {
    // The card was edited after the grantor looked at it.
    cardReads = "someone.else@example.com";

    assertThatThrownBy(() -> service.grant(CUSTOMER, request("ann.lee@example.com"), MANAGER))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED));
    verify(authorities, never()).saveAndFlush(any());
  }

  @Test
  void theListShowsAnAuthorityWhoseContactPointNowReadsAnotherAddress() {
    ApprovalAuthority granted = authority();
    when(authorities.findByTenantIdAndTradingPartnerIdOrderByGrantedAtDesc(TENANT, CUSTOMER))
        .thenReturn(List.of(granted));
    cardReads = "someone.else@example.com";

    assertThat(service.list(CUSTOMER))
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.authorisedEmail()).isEqualTo("ann.lee@example.com");
              assertThat(view.contactAddressChanged()).isTrue();
            });
  }

  @Test
  void revokingLocksTheAuthorityFirst() {
    ApprovalAuthority granted = authority();
    when(authorities.lockByTenantIdAndId(TENANT, granted.getId())).thenReturn(Optional.of(granted));

    ApprovalAuthorityView view =
        service.revoke(
            CUSTOMER,
            granted.getId(),
            new RevokeApprovalAuthorityRequest("Left the company"),
            MANAGER);

    assertThat(view.revokedAt()).isEqualTo(NOW);
    verify(authorities).lockByTenantIdAndId(TENANT, granted.getId());
  }

  @Test
  void anotherCustomersAuthorityIsNotFoundForRevoking() {
    ApprovalAuthority granted = authority();
    ReflectionTestUtils.setField(granted, "tradingPartnerId", UUID.randomUUID());
    when(authorities.lockByTenantIdAndId(TENANT, granted.getId())).thenReturn(Optional.of(granted));

    assertThatThrownBy(
            () ->
                service.revoke(
                    CUSTOMER,
                    granted.getId(),
                    new RevokeApprovalAuthorityRequest("Left the company"),
                    MANAGER))
        .isInstanceOf(NotFoundException.class);
    assertThat(granted.isRevoked()).isFalse();
  }

  @Test
  void aChangedContactPointEndsItsOpenAuthoritiesUnderTheirLock() {
    ApprovalAuthority granted = authority();
    when(authorities.lockOpenByTenantIdAndContactId(TENANT, ADDRESS)).thenReturn(List.of(granted));

    int ended =
        service.endForContactChange(ADDRESS, null, ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);

    assertThat(ended).isEqualTo(1);
    assertThat(granted.getRevocationCause())
        .isEqualTo(ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);
    assertThat(granted.getRevokedBy()).isEqualTo(SystemUser.ID);
    assertThat(granted.getRevokedAt()).isEqualTo(NOW);
    verify(authorities).flush();
  }

  @Test
  void aContactRemovedFromAnotherOrganizationLeavesThisCustomersAuthority() {
    ApprovalAuthority granted = authority();
    when(authorities.lockOpenByTenantIdAndContactId(TENANT, ADDRESS)).thenReturn(List.of(granted));

    int ended =
        service.endForContactChange(
            ADDRESS, UUID.randomUUID(), ApprovalAuthorityEnd.CONTACT_REMOVED);
    assertThat(ended).isZero();
    assertThat(granted.isRevoked()).isFalse();

    ended =
        service.endForContactChange(ADDRESS, ORGANIZATION, ApprovalAuthorityEnd.CONTACT_REMOVED);
    assertThat(ended).isEqualTo(1);
    assertThat(granted.getRevocationCause()).isEqualTo(ApprovalAuthorityEnd.CONTACT_REMOVED);
  }

  @Test
  void theViewSaysWhyAnAuthorityEnded() {
    ApprovalAuthority granted = authority();
    granted.endForContactChange(ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED, SystemUser.ID, NOW);
    when(authorities.findByTenantIdAndTradingPartnerIdOrderByGrantedAtDesc(TENANT, CUSTOMER))
        .thenReturn(List.of(granted));

    assertThat(service.list(CUSTOMER))
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.revocationCause())
                  .isEqualTo(ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);
              assertThat(view.active()).isFalse();
            });
  }

  private static GrantApprovalAuthorityRequest request(String email) {
    return new GrantApprovalAuthorityRequest(
        "Ann Lee",
        ADDRESS,
        email,
        ApprovalAuthorityBasis.WRITTEN_MANDATE,
        "Mandate letter",
        LocalDate.of(2026, 10, 1),
        null);
  }

  private static ApprovalAuthority authority() {
    ApprovalAuthority value =
        ApprovalAuthority.grant(
            CUSTOMER,
            "Ann Lee",
            ADDRESS,
            "ann.lee@example.com",
            ApprovalAuthorityBasis.WRITTEN_MANDATE,
            "Mandate letter",
            LocalDate.of(2026, 10, 1),
            null,
            MANAGER,
            NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    return value;
  }
}
