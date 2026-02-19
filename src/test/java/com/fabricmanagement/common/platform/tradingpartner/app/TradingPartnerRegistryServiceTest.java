package com.fabricmanagement.common.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.common.platform.tradingpartner.domain.VerifiedStatus;
import com.fabricmanagement.common.platform.tradingpartner.domain.event.TradingPartnerLinkedEvent;
import com.fabricmanagement.common.platform.tradingpartner.domain.event.TradingPartnerRegistryCreatedEvent;
import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerRegistryDto;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingPartnerRegistryService")
class TradingPartnerRegistryServiceTest {

  private static final UUID REGISTRY_ID = UUID.randomUUID();
  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID VERIFIER_USER_ID = UUID.randomUUID();

  @Mock private TradingPartnerRegistryRepository registryRepository;
  @Mock private TradingPartnerRepository partnerRepository;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private TradingPartnerRegistryService service;

  @Nested
  @DisplayName("findOrCreate")
  class FindOrCreate {

    @Test
    void returnsExistingRegistryWhenTaxIdAndCountryMatch() {
      TradingPartnerRegistry existing =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-EXISTING")
              .taxId("1234567890")
              .officialName("Akkaya Tekstil")
              .country("TUR")
              .verifiedStatus(VerifiedStatus.UNVERIFIED)
              .build();

      when(registryRepository.findByTaxIdAndCountry("1234567890", "TUR"))
          .thenReturn(Optional.of(existing));

      TradingPartnerRegistry result = service.findOrCreate("1234567890", "Akkaya Tekstil", "TUR");

      assertThat(result).isSameAs(existing);
      verify(registryRepository, never()).save(any());
      verify(eventPublisher, never()).publish(any(TradingPartnerRegistryCreatedEvent.class));
    }

    @Test
    void createsNewRegistryWhenNoMatchAndPublishesEvent() {
      when(registryRepository.findByTaxIdAndCountry("1234567890", "TUR"))
          .thenReturn(Optional.empty());
      when(registryRepository.save(any(TradingPartnerRegistry.class)))
          .thenAnswer(
              inv -> {
                TradingPartnerRegistry r = inv.getArgument(0);
                r.setId(REGISTRY_ID);
                r.setUid("REG-NEW");
                return r;
              });

      TradingPartnerRegistry result = service.findOrCreate("1234567890", "Akkaya Tekstil", "TUR");

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(REGISTRY_ID);
      assertThat(result.getTaxId()).isEqualTo("1234567890");
      assertThat(result.getOfficialName()).isEqualTo("Akkaya Tekstil");
      assertThat(result.getCountry()).isEqualTo("TUR");

      ArgumentCaptor<TradingPartnerRegistryCreatedEvent> eventCaptor =
          ArgumentCaptor.forClass(TradingPartnerRegistryCreatedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      TradingPartnerRegistryCreatedEvent event = eventCaptor.getValue();
      assertThat(event.getRegistryId()).isEqualTo(REGISTRY_ID);
      assertThat(event.getTaxId()).isEqualTo("1234567890");
      assertThat(event.getOfficialName()).isEqualTo("Akkaya Tekstil");
      assertThat(event.getCountry()).isEqualTo("TUR");
    }

    @Test
    void createsNewRegistryWhenTaxIdIsNull() {
      when(registryRepository.save(any(TradingPartnerRegistry.class)))
          .thenAnswer(
              inv -> {
                TradingPartnerRegistry r = inv.getArgument(0);
                r.setId(REGISTRY_ID);
                r.setUid("REG-NOTAX");
                return r;
              });

      TradingPartnerRegistry result = service.findOrCreate(null, "Foreign Partner", "USA");

      assertThat(result).isNotNull();
      assertThat(result.getTaxId()).isNull();
      assertThat(result.getOfficialName()).isEqualTo("Foreign Partner");
      assertThat(result.getCountry()).isEqualTo("USA");
      verify(registryRepository).save(any(TradingPartnerRegistry.class));
    }
  }

  @Nested
  @DisplayName("linkToTenant")
  class LinkToTenant {

    @Test
    void linksRegistryAndPublishesTradingPartnerLinkedEvent() {
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-1")
              .taxId("123")
              .officialName("Partner Co")
              .country("TUR")
              .verifiedStatus(VerifiedStatus.UNVERIFIED)
              .linkedTenantId(null)
              .build();

      TradingPartner tp1 =
          TradingPartner.builder().registry(registry).partnerType(PartnerType.SUPPLIER).build();
      tp1.setId(UUID.randomUUID());
      tp1.setTenantId(TENANT_ID);
      UUID otherTenantId = UUID.randomUUID();
      TradingPartner tp2 =
          TradingPartner.builder().registry(registry).partnerType(PartnerType.CUSTOMER).build();
      tp2.setId(UUID.randomUUID());
      tp2.setTenantId(otherTenantId);

      when(registryRepository.findById(REGISTRY_ID)).thenReturn(Optional.of(registry));
      when(registryRepository.save(any(TradingPartnerRegistry.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(partnerRepository.findByRegistryId(REGISTRY_ID)).thenReturn(List.of(tp1, tp2));

      service.linkToTenant(REGISTRY_ID, TENANT_ID, VERIFIER_USER_ID);

      assertThat(registry.getLinkedTenantId()).isEqualTo(TENANT_ID);
      assertThat(registry.getVerifiedStatus()).isEqualTo(VerifiedStatus.VERIFIED);
      assertThat(registry.getVerifiedBy()).isEqualTo(VERIFIER_USER_ID);

      ArgumentCaptor<TradingPartnerLinkedEvent> eventCaptor =
          ArgumentCaptor.forClass(TradingPartnerLinkedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      TradingPartnerLinkedEvent event = eventCaptor.getValue();
      assertThat(event.getRegistryId()).isEqualTo(REGISTRY_ID);
      assertThat(event.getLinkedTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getAffectedTenantIds()).containsExactlyInAnyOrder(TENANT_ID, otherTenantId);
    }

    @Test
    void throwsWhenRegistryNotFound() {
      when(registryRepository.findById(REGISTRY_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.linkToTenant(REGISTRY_ID, TENANT_ID, VERIFIER_USER_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Registry not found");
    }

    @Test
    void throwsWhenRegistryAlreadyLinkedToDifferentTenant() {
      UUID otherTenantId = UUID.randomUUID();
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-1")
              .linkedTenantId(otherTenantId)
              .build();

      when(registryRepository.findById(REGISTRY_ID)).thenReturn(Optional.of(registry));

      assertThatThrownBy(() -> service.linkToTenant(REGISTRY_ID, TENANT_ID, VERIFIER_USER_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already linked to different tenant");
      verify(registryRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void returnsDtoWhenRegistryExists() {
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.builder()
              .id(REGISTRY_ID)
              .uid("REG-1")
              .taxId("123")
              .officialName("Partner Co")
              .country("TUR")
              .verifiedStatus(VerifiedStatus.UNVERIFIED)
              .build();

      when(registryRepository.findById(REGISTRY_ID)).thenReturn(Optional.of(registry));

      Optional<TradingPartnerRegistryDto> result = service.findById(REGISTRY_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(REGISTRY_ID);
      assertThat(result.get().getOfficialName()).isEqualTo("Partner Co");
    }

    @Test
    void returnsEmptyWhenRegistryNotFound() {
      when(registryRepository.findById(REGISTRY_ID)).thenReturn(Optional.empty());

      Optional<TradingPartnerRegistryDto> result = service.findById(REGISTRY_ID);

      assertThat(result).isEmpty();
    }
  }
}
