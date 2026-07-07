package com.fabricmanagement.production.masterdata.color.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColorQueryServiceTest {

  @Mock private ColorRepository colorRepository;

  @InjectMocks private ColorQueryService colorQueryService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should list only active color cards by default")
  void shouldListOnlyActiveColorCardsByDefault() {
    Color navy = color("NAVY-01", "Navy");
    when(colorRepository.findByTenantIdAndIsActiveTrueOrderByCode(tenantId))
        .thenReturn(List.of(navy));

    List<ColorQueryService.ColorReference> result =
        colorQueryService.findSalesColorReferences(false);

    assertEquals(
        List.of("NAVY-01"), result.stream().map(ColorQueryService.ColorReference::code).toList());
    verify(colorRepository).findByTenantIdAndIsActiveTrueOrderByCode(tenantId);
  }

  @Test
  @DisplayName("Should include inactive color cards when requested")
  void shouldIncludeInactiveColorCardsWhenRequested() {
    Color navy = color("NAVY-01", "Navy");
    when(colorRepository.findByTenantIdOrderByCode(tenantId)).thenReturn(List.of(navy));

    List<ColorQueryService.ColorReference> result =
        colorQueryService.findSalesColorReferences(true);

    assertEquals(
        List.of("NAVY-01"), result.stream().map(ColorQueryService.ColorReference::code).toList());
    verify(colorRepository).findByTenantIdOrderByCode(tenantId);
  }

  private Color color(String code, String name) {
    Color color = Color.create(tenantId, code, name, "#001F3F");
    color.setId(UUID.randomUUID());
    return color;
  }
}
