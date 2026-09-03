package com.fabricmanagement.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.platform.ai.app.adapter.SmartSearchAIToolProvider;
import com.fabricmanagement.product.core.app.adapter.ProductAIToolProvider;
import com.fabricmanagement.product.fiber.app.adapter.FiberAIToolProvider;
import com.fabricmanagement.product.yarn.app.adapter.YarnAIToolProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AIToolRegistryContractTest {

  private static final Set<String> YARN_TOOLS =
      Set.of("search_yarns", "get_yarn_info", "list_yarn_vocabularies", "create_yarn_article");

  @Test
  void builderDefinitionsMatchProviderToolsWithoutCollisions() {
    List<AIToolProvider> providers =
        List.of(
            new ProductAIToolProvider(null, null, null),
            new FiberAIToolProvider(null, null),
            new YarnAIToolProvider(null, null, null, null, null, null),
            new SmartSearchAIToolProvider(null));
    Set<String> providerTools =
        providers.stream()
            .flatMap(provider -> provider.getSupportedTools().stream())
            .collect(Collectors.toSet());
    int declaredToolCount =
        providers.stream().mapToInt(provider -> provider.getSupportedTools().size()).sum();

    assertThat(providerTools).hasSize(declaredToolCount);
    assertThat(providerTools).isEqualTo(builderToolNames());
    assertThat(AIToolBuilder.getAvailableTools()).hasSize(providerTools.size());
    assertThat(providerTools).containsAll(YARN_TOOLS);
  }

  @Test
  void registryDispatchesEveryYarnToolToYarnProvider() {
    YarnAIToolProvider yarnProvider =
        spy(new YarnAIToolProvider(null, null, null, null, null, null));
    AIToolRegistry registry = new AIToolRegistry(List.of(yarnProvider));
    UUID tenantId = UUID.randomUUID();

    YARN_TOOLS.forEach(
        toolName -> {
          doReturn("yarn:" + toolName).when(yarnProvider).execute(tenantId, toolName, Map.of());
          assertThat(registry.execute(tenantId, toolName, Map.of())).isEqualTo("yarn:" + toolName);
        });
    assertThat(registry.getRegisteredTools()).containsExactlyInAnyOrderElementsOf(YARN_TOOLS);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createYarnDefinitionExposesOnlyDraftShellInputs() {
    Map<String, Object> function =
        AIToolBuilder.getAvailableTools().stream()
            .map(tool -> (Map<String, Object>) tool.get("function"))
            .filter(candidate -> "create_yarn_article".equals(candidate.get("name")))
            .findFirst()
            .orElseThrow();
    Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
    Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

    assertThat(properties.keySet())
        .containsExactlyInAnyOrder("name", "productId", "unit", "sourceDesignation");
    assertThat((List<String>) parameters.get("required")).containsExactly("name");
    assertThat(function.get("description").toString())
        .contains("DRAFT", "verbatim", "never interpreted");
  }

  @SuppressWarnings("unchecked")
  private Set<String> builderToolNames() {
    Set<String> names = new HashSet<>();
    AIToolBuilder.getAvailableTools()
        .forEach(
            tool -> {
              Map<String, Object> function = (Map<String, Object>) tool.get("function");
              names.add((String) function.get("name"));
            });
    return names;
  }
}
