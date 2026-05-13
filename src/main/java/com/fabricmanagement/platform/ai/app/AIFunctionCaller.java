package com.fabricmanagement.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI Function Caller - Entry point for FabricAI tool execution.
 *
 * <p>Delegates all tool execution and caching logic to the {@link AIToolRegistry}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AIFunctionCaller {

  private final AIToolRegistry toolRegistry;

  /**
   * Execute function call from AI. Caching is handled by {@link AIToolRegistry}.
   *
   * @param functionName function name (e.g., "check_product_stock")
   * @param parameters function parameters
   * @return function result as string
   */
  public String executeFunction(String functionName, Map<String, Object> parameters) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Executing AI function: functionName={}, tenantId={}", functionName, tenantId);
    return toolRegistry.execute(tenantId, functionName, parameters);
  }
}
