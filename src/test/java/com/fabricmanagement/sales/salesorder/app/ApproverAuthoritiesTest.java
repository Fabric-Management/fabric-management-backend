package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityBasis;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.ApprovalAuthorityRepository;
import jakarta.persistence.EntityManager;
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
 * The authority in force (ADR-0014 OD-13): requests go only to the address the authority was
 * granted for; a contact point edited to another address reaches nobody; sending and deciding read
 * the authority under its row lock, so a revocation committed meanwhile is seen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproverAuthoritiesTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID CUSTOMER_ORGANIZATION = UUID.randomUUID();
  private static final UUID JANES_ADDRESS = UUID.randomUUID();
  private static final String AUTHORISED = "jane@northern-garments.example";

  @Mock private ApprovalAuthorityRepository authorities;
  @Mock private TradingPartnerService partners;
  @Mock private OrganizationContactFacade contacts;
  @Mock private EntityManager entityManager;

  private ApproverAuthorities approvers;
  private ApprovalAuthority authority;
  private String cardReads = AUTHORISED;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    approvers =
        new ApproverAuthorities(
            authorities, partners, contacts, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
    authority =
        ApprovalAuthority.grant(
            CUSTOMER,
            "Jane Smith",
            JANES_ADDRESS,
            AUTHORISED,
            ApprovalAuthorityBasis.WRITTEN_MANDATE,
            "Mandate letter",
            TODAY.minusDays(5),
            null,
            UUID.randomUUID(),
            NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(authority, "id", UUID.randomUUID());
    when(authorities.findByTenantIdAndTradingPartnerIdAndContactIdAndRevokedAtIsNull(
            TENANT, CUSTOMER, JANES_ADDRESS))
        .thenAnswer(invocation -> Optional.of(authority).filter(value -> !value.isRevoked()));
    when(authorities.findFirstByTenantIdAndTradingPartnerIdAndContactIdOrderByGrantedAtDesc(
            TENANT, CUSTOMER, JANES_ADDRESS))
        .thenAnswer(invocation -> Optional.of(authority));
    when(authorities.findByTenantIdAndId(TENANT, authority.getId()))
        .thenAnswer(invocation -> Optional.of(authority));
    when(authorities.lockByTenantIdAndId(TENANT, authority.getId()))
        .thenAnswer(invocation -> Optional.of(authority));
    when(partners.findById(TENANT, CUSTOMER))
        .thenReturn(
            Optional.of(TradingPartnerDto.builder().organizationId(CUSTOMER_ORGANIZATION).build()));
    when(contacts.getOrganizationContacts(CUSTOMER_ORGANIZATION))
        .thenAnswer(
            invocation ->
                cardReads == null
                    ? List.of()
                    : List.of(
                        OrganizationContactDto.builder()
                            .organizationId(CUSTOMER_ORGANIZATION)
                            .contactId(JANES_ADDRESS)
                            .contact(
                                OrganizationContactDto.ContactData.builder()
                                    .id(JANES_ADDRESS)
                                    .contactType(ContactType.EMAIL)
                                    .contactValue(cardReads)
                                    .build())
                            .build()));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void aRequestGoesToTheAddressTheAuthorityWasGrantedFor() {
    // The same mailbox written differently on the card.
    cardReads = " JANE@Northern-Garments.example ";

    ApproverAuthorities.Resolution resolution = approvers.resolve(order());

    assertThat(resolution.block()).isNull();
    assertThat(resolution.approver().authorityId()).isEqualTo(authority.getId());
    assertThat(resolution.approver().email()).isEqualTo(AUTHORISED);
  }

  @Test
  void aContactPointEditedToAnotherAddressCarriesNoAuthority() {
    cardReads = "someone.else@another-company.example";

    assertThat(approvers.resolve(order()).block())
        .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertThat(approvers.block(approval(), NOW))
        .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> approvers.requireValid(approval(), NOW), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> approvers.holdValid(approval(), NOW), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
  }

  @Test
  void aContactPointRemovedFromTheCardCarriesNoAuthority() {
    cardReads = null;

    assertThat(approvers.resolve(order()).block())
        .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertThat(approvers.isValid(approval(), NOW)).isFalse();
  }

  @Test
  void anAuthorityEndedByAContactChangeStaysEndedWhenTheAddressComesBack() {
    // A → B ended the authority; the card now reads A again.
    authority.endForContactChange(
        ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED, UUID.randomUUID(), NOW.minusSeconds(60));
    cardReads = AUTHORISED;

    assertThat(approvers.block(approval(), NOW))
        .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertThat(approvers.resolve(order()).block())
        .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> approvers.holdValid(approval(), NOW), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> approvers.requireValid(approval(), NOW), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
  }

  @Test
  void anEndedAuthorityIsReportedBeforeTheAddress() {
    cardReads = "someone.else@another-company.example";
    authority.revoke(UUID.randomUUID(), "Left the company", NOW.minusSeconds(60));

    assertThat(approvers.block(approval(), NOW)).isEqualTo(ApproverAuthorities.AUTHORITY_INACTIVE);
    assertThat(approvers.resolve(order()).block())
        .isEqualTo(ApproverAuthorities.AUTHORITY_INACTIVE);
  }

  @Test
  void holdingTheAuthorityLocksItAndReadsItAgainUnderTheLock() {
    // The instance was read before the lock; meanwhile a revocation committed. Reading it again
    // under the lock brings the revocation in.
    doAnswer(
            invocation -> {
              ReflectionTestUtils.setField(authority, "revokedAt", NOW.minusSeconds(1));
              ReflectionTestUtils.setField(authority, "revokedBy", UUID.randomUUID());
              ReflectionTestUtils.setField(authority, "revocationReason", "Left the company");
              return null;
            })
        .when(entityManager)
        .refresh(authority);

    assertRefused(
        () -> approvers.holdValid(approval(), NOW), ApproverAuthorities.AUTHORITY_INACTIVE);
    verify(authorities).lockByTenantIdAndId(TENANT, authority.getId());
    verify(entityManager).refresh(authority);
  }

  @Test
  void readingWithoutSendingOrDecidingTakesNoLock() {
    assertThat(approvers.isValid(approval(), NOW)).isTrue();
    approvers.requireValid(approval(), NOW);

    verify(authorities, never()).lockByTenantIdAndId(any(), any());
    verify(entityManager, never()).refresh(any());
  }

  @Test
  void aRequestWithoutItsAuthorityIsNeverInForce() {
    CustomerApproval orphan = mock(CustomerApproval.class);

    assertThat(approvers.block(orphan, NOW)).isEqualTo(ApproverAuthorities.AUTHORITY_INACTIVE);
    assertRefused(() -> approvers.holdValid(orphan, NOW), ApproverAuthorities.AUTHORITY_INACTIVE);
  }

  private SalesOrder order() {
    return SalesOrder.builder()
        .tradingPartnerId(CUSTOMER)
        .orderNumber("SO-1")
        .approverContactId(JANES_ADDRESS)
        .build();
  }

  private CustomerApproval approval() {
    CustomerApproval approval = mock(CustomerApproval.class);
    when(approval.getApprovalAuthorityId()).thenReturn(authority.getId());
    return approval;
  }

  private static void assertRefused(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
  }
}
