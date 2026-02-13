package com.fabricmanagement.common.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingPartnerResolver")
class TradingPartnerResolverTest {

  @Mock private TradingPartnerRepository partnerRepository;

  @InjectMocks private TradingPartnerResolver resolver;

  private UUID tenantId;
  private UUID tradingPartnerId;
  private UUID legacyCompanyId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    tradingPartnerId = UUID.randomUUID();
    legacyCompanyId = UUID.randomUUID();
  }

  private void setLegacyFallbackEnabled(boolean enabled) throws Exception {
    Field field = TradingPartnerResolver.class.getDeclaredField("legacyFallbackEnabled");
    field.setAccessible(true);
    field.set(resolver, enabled);
  }

  @Nested
  @DisplayName("resolvePartnerId")
  class ResolvePartnerId {

    @Test
    @DisplayName("returns partnerId when it exists as TradingPartner.id")
    void returnsPartnerIdWhenExistsDirectly() {
      when(partnerRepository.existsByTenantIdAndId(tenantId, tradingPartnerId)).thenReturn(true);

      UUID result = resolver.resolvePartnerId(tenantId, tradingPartnerId);

      assertThat(result).isEqualTo(tradingPartnerId);
    }

    @Test
    @DisplayName("returns resolved TradingPartner.id when partnerId is legacy company ID")
    void returnsResolvedIdWhenLegacyCompanyId() {
      TradingPartner partner = createMockPartner(tradingPartnerId, legacyCompanyId);

      when(partnerRepository.existsByTenantIdAndId(tenantId, legacyCompanyId)).thenReturn(false);
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, legacyCompanyId))
          .thenReturn(Optional.of(partner));

      UUID result = resolver.resolvePartnerId(tenantId, legacyCompanyId);

      assertThat(result).isEqualTo(tradingPartnerId);
    }

    @Test
    @DisplayName("throws exception when partnerId not found")
    void throwsWhenPartnerNotFound() {
      UUID unknownId = UUID.randomUUID();
      when(partnerRepository.existsByTenantIdAndId(tenantId, unknownId)).thenReturn(false);
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, unknownId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> resolver.resolvePartnerId(tenantId, unknownId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trading partner not found");
    }

    @Test
    @DisplayName("throws exception when partnerId is null")
    void throwsWhenPartnerIdNull() {
      assertThatThrownBy(() -> resolver.resolvePartnerId(tenantId, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be null");
    }
  }

  @Nested
  @DisplayName("tryResolvePartnerId")
  class TryResolvePartnerId {

    @Test
    @DisplayName("returns Optional with partnerId when exists directly")
    void returnsOptionalWhenExistsDirectly() {
      when(partnerRepository.existsByTenantIdAndId(tenantId, tradingPartnerId)).thenReturn(true);

      Optional<UUID> result = resolver.tryResolvePartnerId(tenantId, tradingPartnerId);

      assertThat(result).contains(tradingPartnerId);
    }

    @Test
    @DisplayName("returns Optional with resolved ID when legacy company ID")
    void returnsOptionalWhenLegacyId() {
      TradingPartner partner = createMockPartner(tradingPartnerId, legacyCompanyId);

      when(partnerRepository.existsByTenantIdAndId(tenantId, legacyCompanyId)).thenReturn(false);
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, legacyCompanyId))
          .thenReturn(Optional.of(partner));

      Optional<UUID> result = resolver.tryResolvePartnerId(tenantId, legacyCompanyId);

      assertThat(result).contains(tradingPartnerId);
    }

    @Test
    @DisplayName("returns empty Optional when not found")
    void returnsEmptyWhenNotFound() {
      UUID unknownId = UUID.randomUUID();
      when(partnerRepository.existsByTenantIdAndId(tenantId, unknownId)).thenReturn(false);
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, unknownId))
          .thenReturn(Optional.empty());

      Optional<UUID> result = resolver.tryResolvePartnerId(tenantId, unknownId);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty Optional when partnerId is null")
    void returnsEmptyWhenNull() {
      Optional<UUID> result = resolver.tryResolvePartnerId(tenantId, null);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("getEffectivePartnerId")
  class GetEffectivePartnerId {

    @Test
    @DisplayName("returns tradingPartnerId when not null")
    void returnsTradingPartnerIdWhenNotNull() {
      UUID result = resolver.getEffectivePartnerId(tradingPartnerId, legacyCompanyId, tenantId);

      assertThat(result).isEqualTo(tradingPartnerId);
    }

    @Test
    @DisplayName("returns resolved ID via legacy lookup when tradingPartnerId is null")
    void returnsResolvedIdWhenTradingPartnerIdNull() throws Exception {
      setLegacyFallbackEnabled(true);
      TradingPartner partner = createMockPartner(tradingPartnerId, legacyCompanyId);

      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, legacyCompanyId))
          .thenReturn(Optional.of(partner));

      UUID result = resolver.getEffectivePartnerId(null, legacyCompanyId, tenantId);

      assertThat(result).isEqualTo(tradingPartnerId);
    }

    @Test
    @DisplayName("throws exception when legacy fallback disabled and tradingPartnerId is null")
    void throwsWhenLegacyFallbackDisabled() throws Exception {
      setLegacyFallbackEnabled(false);

      assertThatThrownBy(() -> resolver.getEffectivePartnerId(null, legacyCompanyId, tenantId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Legacy fallback disabled");
    }

    @Test
    @DisplayName("throws exception when both IDs are null")
    void throwsWhenBothNull() throws Exception {
      setLegacyFallbackEnabled(true);

      assertThatThrownBy(() -> resolver.getEffectivePartnerId(null, null, tenantId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Both trading_partner_id and company_id are null");
    }
  }

  @Nested
  @DisplayName("resolvePartner")
  class ResolvePartner {

    @Test
    @DisplayName("returns partner when found by ID")
    void returnsPartnerWhenFoundById() {
      TradingPartner partner = createMockPartner(tradingPartnerId, null);

      when(partnerRepository.findByTenantIdAndId(tenantId, tradingPartnerId))
          .thenReturn(Optional.of(partner));

      Optional<TradingPartner> result = resolver.resolvePartner(tenantId, tradingPartnerId);

      assertThat(result).contains(partner);
    }

    @Test
    @DisplayName("returns partner when found by legacy ID")
    void returnsPartnerWhenFoundByLegacyId() {
      TradingPartner partner = createMockPartner(tradingPartnerId, legacyCompanyId);

      when(partnerRepository.findByTenantIdAndId(tenantId, legacyCompanyId))
          .thenReturn(Optional.empty());
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, legacyCompanyId))
          .thenReturn(Optional.of(partner));

      Optional<TradingPartner> result = resolver.resolvePartner(tenantId, legacyCompanyId);

      assertThat(result).contains(partner);
    }

    @Test
    @DisplayName("returns empty when partner not found")
    void returnsEmptyWhenNotFound() {
      UUID unknownId = UUID.randomUUID();

      when(partnerRepository.findByTenantIdAndId(tenantId, unknownId)).thenReturn(Optional.empty());
      when(partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, unknownId))
          .thenReturn(Optional.empty());

      Optional<TradingPartner> result = resolver.resolvePartner(tenantId, unknownId);

      assertThat(result).isEmpty();
    }
  }

  // Helper to create mock TradingPartner
  private TradingPartner createMockPartner(UUID id, UUID legacyId) {
    TradingPartner partner = new TradingPartner();
    try {
      Field idField = TradingPartner.class.getSuperclass().getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(partner, id);

      if (legacyId != null) {
        Field legacyField = TradingPartner.class.getDeclaredField("legacyCompanyId");
        legacyField.setAccessible(true);
        legacyField.set(partner, legacyId);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return partner;
  }
}
