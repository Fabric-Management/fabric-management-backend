package com.fabricmanagement.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CONSIGNEE-1 C5/C8: a partner is verified by who made it, or by a verifier later. */
@ExtendWith(MockitoExtension.class)
class TradingPartnerVerificationTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID REGISTRY_ID = UUID.randomUUID();
  private static final UUID VERIFIER = UUID.randomUUID();

  @Mock private TradingPartnerRepository partnerRepository;
  @Mock private TradingPartnerRegistryService registryService;
  @Mock private TradingPartnerRegistryRepository registryRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private OrganizationFacade organizationFacade;
  @Mock private PartnerContactService partnerContactService;
  @Mock private DeliveryVerificationPolicy verificationPolicy;

  @InjectMocks private TradingPartnerService service;
  @InjectMocks private DeliveryPartnerService deliveryPartners;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static TradingPartnerRegistry registry() {
    return TradingPartnerRegistry.builder()
        .id(REGISTRY_ID)
        .uid("REG-V")
        .taxId("1234567890")
        .officialName("Akkaya Tekstil")
        .country("TUR")
        .build();
  }

  /** The repositories and facades a creation goes through. */
  private void stubCreation() {
    when(registryService.findOrCreate(any(), any(), any())).thenReturn(registry());
    when(partnerRepository.findByTenantIdAndRegistryId(TENANT, REGISTRY_ID))
        .thenReturn(Optional.empty());
    when(partnerRepository.save(any(TradingPartner.class)))
        .thenAnswer(
            call -> {
              TradingPartner partner = call.getArgument(0);
              partner.setId(UUID.randomUUID());
              partner.setUid("TP-V");
              partner.setTenantId(TENANT);
              return partner;
            });
    when(organizationFacade.createPartnerOrganization(any(), any(), any()))
        .thenReturn(OrganizationDto.builder().id(UUID.randomUUID()).build());
  }

  private TradingPartnerDto createSupplier() {
    stubCreation();
    return service.createPartner(
        CreateTradingPartnerRequest.builder()
            .companyName("Akkaya Tekstil")
            .taxId("1234567890")
            .country("TUR")
            .partnerType(PartnerType.SUPPLIER)
            .build());
  }

  private TradingPartner quickCreated() {
    stubCreation();
    QuickCreatePartnerLinkRequest request = new QuickCreatePartnerLinkRequest();
    request.setCompanyName("Hackney Workshop");
    request.setCountry("TUR");
    return deliveryPartners.findOrCreate(request);
  }

  private TradingPartner stored(Verification state) {
    TradingPartner partner =
        TradingPartner.create(registry(), PartnerType.DELIVERY_PARTNER, "Workshop", state);
    partner.setId(UUID.randomUUID());
    partner.setTenantId(TENANT);
    when(partnerRepository.findByTenantIdAndId(TENANT, partner.getId()))
        .thenReturn(Optional.of(partner));
    return partner;
  }

  @Test
  void aPartnerMadeByAUserWithoutThePermissionIsUnverified() {
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());

    TradingPartnerDto created = createSupplier();

    assertThat(created.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(created.getVerifiedBy()).isNull();
  }

  @Test
  void aPartnerMadeByAVerifierIsVerifiedByThem() {
    Instant at = Instant.parse("2026-10-16T09:00:00Z");
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.verifiedBy(VERIFIER, at));

    TradingPartnerDto created = createSupplier();

    assertThat(created.getVerificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(created.getVerifiedBy()).isEqualTo(VERIFIER);
    assertThat(created.getVerifiedAt()).isEqualTo(at);
  }

  @Test
  void aQuickCreatedDeliveryPartnerIsVerifiedOnlyWhenTheActorHoldsThePermission() {
    when(verificationPolicy.forCurrentActor()).thenReturn(Verification.unverified());
    assertThat(quickCreated().getVerification().isVerified()).isFalse();
  }

  @Test
  void aQuickCreatedDeliveryPartnerOfAVerifierIsVerified() {
    when(verificationPolicy.forCurrentActor())
        .thenReturn(Verification.verifiedBy(VERIFIER, Instant.now()));
    TradingPartner partner = quickCreated();

    assertThat(partner.getVerification().isVerified()).isTrue();
    assertThat(partner.getVerification().getVerifiedBy()).isEqualTo(VERIFIER);
  }

  @Test
  void rowsThatPredateTheRuleAreVerifiedWithNobodyToName() {
    TradingPartner partner =
        TradingPartner.create(registry(), PartnerType.CUSTOMER, "Old customer");

    assertThat(partner.getVerification().isVerified()).isTrue();
    assertThat(TradingPartnerDto.from(partner).getVerificationStatus())
        .isEqualTo(DeliveryVerificationStatus.VERIFIED);
  }

  @Test
  void verifyingAnUnverifiedPartnerRecordsWhoAndWhen() {
    TradingPartner partner = stored(Verification.unverified());

    boolean changed = service.verifyDelivery(TENANT, partner.getId(), VERIFIER);

    assertThat(changed).isTrue();
    assertThat(partner.getVerification().isVerified()).isTrue();
    assertThat(partner.getVerification().getVerifiedBy()).isEqualTo(VERIFIER);
    verify(partnerRepository).save(partner);
  }

  @Test
  void verifyingAVerifiedPartnerChangesNothing() {
    Verification earlier = Verification.verifiedBy(UUID.randomUUID(), Instant.now());
    TradingPartner partner = stored(earlier);

    boolean changed = service.verifyDelivery(TENANT, partner.getId(), VERIFIER);

    assertThat(changed).isFalse();
    assertThat(partner.getVerification()).isSameAs(earlier);
    verify(partnerRepository, never()).save(any());
  }

  @Test
  void aPartnerOfAnotherTenantCannotBeVerified() {
    UUID unknown = UUID.randomUUID();
    when(partnerRepository.findByTenantIdAndId(TENANT, unknown)).thenReturn(Optional.empty());
    when(partnerRepository.findByTenantIdAndLegacyCompanyId(TENANT, unknown))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifyDelivery(TENANT, unknown, VERIFIER))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
