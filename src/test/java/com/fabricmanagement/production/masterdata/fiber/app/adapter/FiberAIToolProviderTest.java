package com.fabricmanagement.production.masterdata.fiber.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberAIToolProviderTest {

  @Mock private FiberFacade fiberFacade;
  @Mock private AIQueryNormalizer queryNormalizer;

  @InjectMocks private FiberAIToolProvider fiberAIToolProvider;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
  }

  @Test
  void shouldSupportFiberTools() {
    assertThat(fiberAIToolProvider.getSupportedTools())
        .containsExactlyInAnyOrder(
            "search_fibers", "get_fiber_info", "list_fiber_categories", "create_fiber");
  }

  @Test
  void shouldSearchFibersWithNormalization() {
    // Given
    String query = "pamuk";
    String normalized = "cotton";
    when(queryNormalizer.normalizeFiberQuery(query)).thenReturn(normalized);

    FiberDto fiber = FiberDto.builder().fiberName("Cotton (100%)").uid("FIB-001").build();
    when(fiberFacade.findByNameContaining("cotton")).thenReturn(List.of(fiber));

    // When
    String result = fiberAIToolProvider.execute(tenantId, "search_fibers", Map.of("query", query));

    // Then
    assertThat(result).contains("Found 1 fiber(s)").contains("Cotton (100%)").contains("FIB-001");
    verify(queryNormalizer).normalizeFiberQuery(query);
    verify(fiberFacade).findByNameContaining("cotton");
  }

  @Test
  void shouldReturnAllCategories() {
    // Given
    FiberCategoryDto category =
        FiberCategoryDto.builder()
            .categoryName("Natural")
            .categoryCode("NATURAL")
            .id(UUID.randomUUID())
            .build();
    when(fiberFacade.listActiveCategories()).thenReturn(List.of(category));

    // When
    String result =
        fiberAIToolProvider.execute(tenantId, "list_fiber_categories", Collections.emptyMap());

    // Then
    assertThat(result).contains("Natural").contains("NATURAL");
    verify(fiberFacade).listActiveCategories();
  }

  @Test
  void shouldGetFiberInfoByUid() {
    // Given
    String uid = "FIB-123";
    FiberDto fiber = FiberDto.builder().fiberName("Organic Hemp").uid(uid).build();
    when(fiberFacade.findAll()).thenReturn(List.of(fiber));

    // When
    String result = fiberAIToolProvider.execute(tenantId, "get_fiber_info", Map.of("fiberId", uid));

    // Then
    assertThat(result).contains("Organic Hemp").contains(uid);
  }
}
