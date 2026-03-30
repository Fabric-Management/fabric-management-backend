package com.fabricmanagement.common.infrastructure.ai;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for modules to provide tools (functions) to the AI assistant. Decouples platform/ai
 * from domain modules.
 */
public interface AIToolProvider {

  /** Set of tool names supported by this provider. Example: {"search_fibers", "get_fiber_info"} */
  Set<String> getSupportedTools();

  /**
   * Executes the specified tool with given parameters.
   *
   * @param tenantId The current tenant context
   * @param toolName Name of the tool to execute
   * @param parameters Parameters passed from the AI
   * @return String result to be sent back to AI
   * @throws IllegalArgumentException if toolName is not supported
   */
  String execute(UUID tenantId, String toolName, Map<String, Object> parameters);
}
