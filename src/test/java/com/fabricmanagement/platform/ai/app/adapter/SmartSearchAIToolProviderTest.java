package com.fabricmanagement.platform.ai.app.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.ai.app.AIToolRegistry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SmartSearchAIToolProviderTest {

  private AIToolRegistry mockRegistry;
  private ObjectProvider<AIToolRegistry> mockProvider;
  private SmartSearchAIToolProvider provider;
  private UUID tenantId;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    mockRegistry = mock(AIToolRegistry.class);
    mockProvider = mock(ObjectProvider.class);
    when(mockProvider.getObject()).thenReturn(mockRegistry);
    provider = new SmartSearchAIToolProvider(mockProvider);
    tenantId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should support smart_search tool")
  void shouldSupportSmartSearch() {
    assertEquals(Set.of("smart_search"), provider.getSupportedTools());
  }

  @Test
  @DisplayName("Should delegate to search_fibers when FIBER detected")
  void shouldDelegateToSearchFibers() {
    Map<String, Object> params = Map.of("query", "cotton");
    when(mockRegistry.execute(eq(tenantId), eq("search_fibers"), any())).thenReturn("Fiber info");

    String result = provider.execute(tenantId, "smart_search", params);

    assertTrue(result.contains("Fiber info"));
    verify(mockRegistry).execute(eq(tenantId), eq("search_fibers"), eq(params));
  }

  @Test
  @DisplayName("Should delegate to search_materials with YARN type when YARN detected")
  void shouldDelegateToMaterialWithYarnType() {
    Map<String, Object> params = Map.of("query", "30/1 yarn");
    when(mockRegistry.execute(eq(tenantId), eq("search_materials"), any())).thenReturn("Yarn info");

    String result = provider.execute(tenantId, "smart_search", params);

    assertTrue(result.contains("Yarn info"));
    verify(mockRegistry).execute(eq(tenantId), eq("search_materials"), any());
  }

  @Test
  @DisplayName("Should delegate to both if UNKNOWN")
  void shouldDelegateToBothIfUnknown() {
    Map<String, Object> params = Map.of("query", "something");
    when(mockRegistry.execute(eq(tenantId), eq("search_fibers"), any())).thenReturn("Fiber info");
    when(mockRegistry.execute(eq(tenantId), eq("search_materials"), any()))
        .thenReturn("Material info");

    String result = provider.execute(tenantId, "smart_search", params);

    assertTrue(result.contains("Fiber info"));
    assertTrue(result.contains("Material info"));
    verify(mockRegistry, times(1)).execute(eq(tenantId), eq("search_fibers"), any());
    verify(mockRegistry, times(1)).execute(eq(tenantId), eq("search_materials"), any());
  }

  @Test
  @DisplayName("Should return error string if query is blank")
  void shouldReturnErrorIfQueryIsBlank() {
    Map<String, Object> params = Map.of("query", "");
    String result = provider.execute(tenantId, "smart_search", params);
    assertTrue(result.contains("Please provide a search term"));
  }
}
