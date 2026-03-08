package com.fabricmanagement.common.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.common.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.common.platform.tradingpartner.domain.event.TradingPartnerCreatedEvent;
import com.fabricmanagement.common.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingPartnerService")
class TradingPartnerServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID REGISTRY_ID = UUID.randomUUID();
  private static final UUID PARTNER_ID = UUID.randomUUID();

  @Mock private TradingPartnerRepository partnerRepository;
  @Mock private TradingPartnerRegistryService registryService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private OrganizationFacade organizationFacade;

  @InjectMocks private TradingPartnerService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createPartner")
  class CreatePartner {

    @Test
    void createsNewPartnerWhenRegistryIsNewAndNoExistingRelationship() {
      CreateTradingPartnerRequest request =
          CreateTradingPartnerRequest.builder()
              .companyName("Akkaya Tekstil")
              .taxId("1234567890")
              .country("TUR")
              .partnerType(PartnerType.SUPPLIER)
              .customName("Main supplier")
              .build();

      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-ABC123")
              .taxId("1234567890")
              .officialName("Akkaya Tekstil")
              .country("TUR")
              .build();

      when(registryService.findOrCreate("1234567890", "Akkaya Tekstil", "TUR"))
          .thenReturn(registry);
      when(partnerRepository.findByTenantIdAndRegistryId(TENANT_ID, REGISTRY_ID))
          .thenReturn(Optional.empty());
      when(partnerRepository.save(any(TradingPartner.class)))
          .thenAnswer(
              inv -> {
                TradingPartner tp = inv.getArgument(0);
                tp.setId(PARTNER_ID);
                tp.setUid("TP-XYZ");
                tp.setTenantId(TENANT_ID);
                return tp;
              });

      OrganizationDto mockedOrg =
          com.fabricmanagement.common.platform.organization.dto.OrganizationDto.builder()
              .id(UUID.randomUUID())
              .build();
      when(organizationFacade.createPartnerOrganization(any(), any(), any())).thenReturn(mockedOrg);

      TradingPartnerDto result = service.createPartner(request);

      assertThat(result).isNotNull();
      assertThat(result.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
      assertThat(result.getStatus()).isEqualTo(PartnerStatus.ACTIVE);

      ArgumentCaptor<TradingPartner> partnerCaptor = ArgumentCaptor.forClass(TradingPartner.class);
      // It's saved twice (once for partner, once to update organizationId)
      verify(partnerRepository, org.mockito.Mockito.times(2)).save(partnerCaptor.capture());
      TradingPartner saved = partnerCaptor.getValue();
      assertThat(saved.getRegistry().getId()).isEqualTo(REGISTRY_ID);
      assertThat(saved.getCustomName()).isEqualTo("Main supplier");

      ArgumentCaptor<TradingPartnerCreatedEvent> eventCaptor =
          ArgumentCaptor.forClass(TradingPartnerCreatedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      TradingPartnerCreatedEvent event = eventCaptor.getValue();
      assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getRegistryId()).isEqualTo(REGISTRY_ID);
      assertThat(event.getPartnerType()).isEqualTo("SUPPLIER");
    }

    @Test
    void upgradesToBothWhenSameRegistryExistsWithDifferentType() {
      CreateTradingPartnerRequest request =
          CreateTradingPartnerRequest.builder()
              .companyName("Akkaya Tekstil")
              .taxId("1234567890")
              .country("TUR")
              .partnerType(PartnerType.CUSTOMER)
              .build();

      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-ABC123")
              .taxId("1234567890")
              .officialName("Akkaya Tekstil")
              .country("TUR")
              .build();

      TradingPartner existing =
          TradingPartner.builder()
              .registry(registry)
              .partnerType(PartnerType.SUPPLIER)
              .status(PartnerStatus.ACTIVE)
              .build();
      existing.setId(PARTNER_ID);
      existing.setUid("TP-EXISTING");
      existing.setTenantId(TENANT_ID);

      when(registryService.findOrCreate("1234567890", "Akkaya Tekstil", "TUR"))
          .thenReturn(registry);
      when(partnerRepository.findByTenantIdAndRegistryId(TENANT_ID, REGISTRY_ID))
          .thenReturn(Optional.of(existing));
      when(partnerRepository.save(any(TradingPartner.class))).thenAnswer(inv -> inv.getArgument(0));

      TradingPartnerDto result = service.createPartner(request);

      assertThat(result.getPartnerType()).isEqualTo(PartnerType.BOTH);
      verify(partnerRepository).save(existing);
      assertThat(existing.getPartnerType()).isEqualTo(PartnerType.BOTH);
      verify(eventPublisher, never()).publish(any(TradingPartnerCreatedEvent.class));
    }

    @Test
    void returnsExistingWhenSameRegistryAndSameType() {
      CreateTradingPartnerRequest request =
          CreateTradingPartnerRequest.builder()
              .companyName("Akkaya Tekstil")
              .taxId("1234567890")
              .country("TUR")
              .partnerType(PartnerType.SUPPLIER)
              .build();

      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-ABC123")
              .taxId("1234567890")
              .officialName("Akkaya Tekstil")
              .country("TUR")
              .build();

      TradingPartner existing =
          TradingPartner.builder()
              .registry(registry)
              .partnerType(PartnerType.SUPPLIER)
              .status(PartnerStatus.ACTIVE)
              .build();
      existing.setId(PARTNER_ID);
      existing.setUid("TP-EXISTING");
      existing.setTenantId(TENANT_ID);

      when(registryService.findOrCreate("1234567890", "Akkaya Tekstil", "TUR"))
          .thenReturn(registry);
      when(partnerRepository.findByTenantIdAndRegistryId(TENANT_ID, REGISTRY_ID))
          .thenReturn(Optional.of(existing));

      TradingPartnerDto result = service.createPartner(request);

      assertThat(result.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
      verify(partnerRepository, never()).save(any());
      verify(eventPublisher, never()).publish(any(TradingPartnerCreatedEvent.class));
    }
  }

  @Nested
  @DisplayName("findById (dual-read)")
  class FindById {

    @Test
    void returnsPartnerWhenFoundByTradingPartnerId() {
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .officialName("Akkaya")
              .taxId("123")
              .country("TUR")
              .build();
      TradingPartner partner =
          TradingPartner.builder()
              .registry(registry)
              .partnerType(PartnerType.SUPPLIER)
              .status(PartnerStatus.ACTIVE)
              .build();
      partner.setId(PARTNER_ID);
      partner.setUid("TP-1");
      partner.setTenantId(TENANT_ID);

      when(partnerRepository.findByTenantIdAndId(TENANT_ID, PARTNER_ID))
          .thenReturn(Optional.of(partner));

      Optional<TradingPartnerDto> result = service.findById(TENANT_ID, PARTNER_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(PARTNER_ID);
      assertThat(result.get().getOfficialName()).isEqualTo("Akkaya");
      verify(partnerRepository).findByTenantIdAndId(TENANT_ID, PARTNER_ID);
      verify(partnerRepository, never()).findByTenantIdAndLegacyCompanyId(any(), any());
    }

    @Test
    void returnsPartnerWhenFoundByLegacyCompanyId() {
      UUID legacyCompanyId = UUID.randomUUID();
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder().id(REGISTRY_ID).officialName("Legacy Co").build();
      TradingPartner partner =
          TradingPartner.builder()
              .registry(registry)
              .legacyCompanyId(legacyCompanyId)
              .partnerType(PartnerType.SUPPLIER)
              .build();
      partner.setId(PARTNER_ID);
      partner.setUid("TP-1");
      partner.setTenantId(TENANT_ID);

      when(partnerRepository.findByTenantIdAndId(TENANT_ID, legacyCompanyId))
          .thenReturn(Optional.empty());
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(TENANT_ID, legacyCompanyId))
          .thenReturn(Optional.of(partner));

      Optional<TradingPartnerDto> result = service.findById(TENANT_ID, legacyCompanyId);

      assertThat(result).isPresent();
      assertThat(result.get().getLegacyCompanyId()).isEqualTo(legacyCompanyId);
    }
  }

  @Nested
  @DisplayName("suspend")
  class Suspend {

    @Test
    void suspendsPartner() {
      TradingPartner partner =
          TradingPartner.builder()
              .registry(TradingPartnerRegistry.builder().id(REGISTRY_ID).build())
              .partnerType(PartnerType.SUPPLIER)
              .status(PartnerStatus.ACTIVE)
              .build();
      partner.setId(PARTNER_ID);
      partner.setUid("TP-1");
      partner.setTenantId(TENANT_ID);

      when(partnerRepository.findByTenantIdAndId(TENANT_ID, PARTNER_ID))
          .thenReturn(Optional.of(partner));
      when(partnerRepository.save(any(TradingPartner.class))).thenAnswer(inv -> inv.getArgument(0));

      service.suspend(TENANT_ID, PARTNER_ID);

      assertThat(partner.getStatus()).isEqualTo(PartnerStatus.SUSPENDED);
      verify(partnerRepository).save(partner);
    }

    @Test
    void throwsWhenPartnerNotFound() {
      when(partnerRepository.findByTenantIdAndId(TENANT_ID, PARTNER_ID))
          .thenReturn(Optional.empty());
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(TENANT_ID, PARTNER_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.suspend(TENANT_ID, PARTNER_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trading partner not found");
    }
  }
}
