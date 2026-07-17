package com.fabricmanagement.production.masterdata.color.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService.PartnerClassification;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingPartnerQueryAdapterTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PARTNER_ID = UUID.randomUUID();

  @Mock private TradingPartnerQueryService queryService;

  private TradingPartnerQueryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TradingPartnerQueryAdapter(queryService);
  }

  @ParameterizedTest
  @MethodSource("compatibilityMatrix")
  void enforcesTheLockedRoleCompatibilityMatrix(
      PartnerRole role, PartnerType type, boolean expected) {
    when(queryService.findClassification(TENANT_ID, PARTNER_ID))
        .thenReturn(Optional.of(new PartnerClassification(type, PartnerStatus.ACTIVE, true)));

    assertThat(adapter.isActiveAndCompatible(TENANT_ID, PARTNER_ID, role)).isEqualTo(expected);
  }

  @Test
  void rejectsMissingInactiveSoftDeletedAndCrossTenantPartners() {
    when(queryService.findClassification(TENANT_ID, PARTNER_ID)).thenReturn(Optional.empty());
    assertThat(adapter.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .isFalse();

    when(queryService.findClassification(TENANT_ID, PARTNER_ID))
        .thenReturn(
            Optional.of(
                new PartnerClassification(PartnerType.CUSTOMER, PartnerStatus.SUSPENDED, true)));
    assertThat(adapter.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .isFalse();

    when(queryService.findClassification(TENANT_ID, PARTNER_ID))
        .thenReturn(
            Optional.of(
                new PartnerClassification(PartnerType.CUSTOMER, PartnerStatus.ACTIVE, false)));
    assertThat(adapter.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .isFalse();
  }

  private static Stream<Arguments> compatibilityMatrix() {
    return Stream.of(
        Arguments.of(PartnerRole.CUSTOMER, PartnerType.CUSTOMER, true),
        Arguments.of(PartnerRole.CUSTOMER, PartnerType.BOTH, true),
        Arguments.of(PartnerRole.CUSTOMER, PartnerType.SUPPLIER, false),
        Arguments.of(PartnerRole.CUSTOMER, PartnerType.SUBCONTRACTOR, false),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.SUPPLIER, true),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.BOTH, true),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.SUBCONTRACTOR, true),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.SERVICE_PROVIDER, false),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.SISTER_COMPANY, false),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.SUBSIDIARY, false),
        Arguments.of(PartnerRole.SUPPLIER, PartnerType.PARENT_COMPANY, false));
  }
}
