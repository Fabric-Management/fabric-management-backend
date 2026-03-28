package com.fabricmanagement.platform.ai.infra.client;

import com.fabricmanagement.platform.ai.dto.ChatRequest;
import com.fabricmanagement.platform.ai.dto.ChatResponse;

/**
 * LLM Client Interface - Provider-agnostic abstraction.
 *
 * <p>Allows switching between different LLM providers (OpenAI, Anthropic, Local) without changing
 * business logic.
 *
 * <p><b>Implementation Strategy:</b>
 *
 * <ul>
 *   <li>OpenAIClient - OpenAI API implementation
 *   <li>AnthropicClient - Claude API implementation (future)
 *   <li>LocalLLMClient - Ollama implementation (future)
 * </ul>
 */
public interface LLMClient {

  /**
   * Send chat request to LLM and get response.
   *
   * @param request chat request with messages
   * @return chat response from LLM
   */
  ChatResponse chat(ChatRequest request);
}
