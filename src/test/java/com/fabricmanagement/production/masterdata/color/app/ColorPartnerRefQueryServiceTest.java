package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorPartnerRefRepository;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import com.fabricmanagement.production.masterdata.color.mapper.ColorMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ColorPartnerRefQueryServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID COLOR_ID = UUID.randomUUID();
  private static final UUID PARTNER_ID = UUID.randomUUID();

  @Mock private ColorPartnerRefRepository refRepository;
  @Mock private ColorRepository colorRepository;
  @Mock private TradingPartnerQueryPort tradingPartnerQueryPort;
  @Mock private ColorMapper colorMapper;

  private ColorPartnerRefQueryService queryService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    queryService =
        new ColorPartnerRefQueryService(
            refRepository, colorRepository, tradingPartnerQueryPort, colorMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void listPagesRootsInTheDatabaseThenFetchesCodesForOnlyThatPage() {
    PageRequest pageable = PageRequest.of(1, 2);
    Color color = Color.create(TENANT_ID, "NAVY", "Navy", "#1F2A44");
    color.setId(COLOR_ID);
    ColorPartnerRef first = ref(UUID.randomUUID(), "FIRST");
    ColorPartnerRef second = ref(UUID.randomUUID(), "SECOND");
    when(colorRepository.findByTenantIdAndId(TENANT_ID, COLOR_ID)).thenReturn(Optional.of(color));
    when(refRepository.findByTenantIdAndColorId(TENANT_ID, COLOR_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(first, second), pageable, 7));
    when(refRepository.findWithCodesByTenantIdAndIdIn(
            TENANT_ID, List.of(first.getId(), second.getId())))
        .thenReturn(List.of(second, first));

    var result = queryService.list(COLOR_ID, pageable);

    assertThat(result.getContent()).containsExactly(first, second);
    assertThat(result.getTotalElements()).isEqualTo(7);
    verify(refRepository)
        .findWithCodesByTenantIdAndIdIn(TENANT_ID, List.of(first.getId(), second.getId()));
  }

  @Test
  void emptyRootPageDoesNotIssueTheCollectionFetchQuery() {
    PageRequest pageable = PageRequest.of(0, 20);
    Color color = Color.create(TENANT_ID, "EMPTY", "Empty", null);
    color.setId(COLOR_ID);
    when(colorRepository.findByTenantIdAndId(TENANT_ID, COLOR_ID)).thenReturn(Optional.of(color));
    when(refRepository.findByTenantIdAndColorId(TENANT_ID, COLOR_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    assertThat(queryService.list(COLOR_ID, pageable)).isEmpty();
    verify(refRepository, never()).findWithCodesByTenantIdAndIdIn(any(), any());
  }

  private ColorPartnerRef ref(UUID id, String externalCode) {
    ColorPartnerRef ref =
        ColorPartnerRef.create(
            TENANT_ID, COLOR_ID, PARTNER_ID, PartnerRole.CUSTOMER, null, externalCode, null);
    ref.setId(id);
    ref.primaryCode().setId(UUID.randomUUID());
    return ref;
  }
}
