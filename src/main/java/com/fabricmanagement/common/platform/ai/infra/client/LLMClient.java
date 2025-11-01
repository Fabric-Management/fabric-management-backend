package com.fabricmanagement.common.platform.ai.infra.client;

import com.fabricmanagement.common.platform.ai.dto.ChatRequest;
import com.fabricmanagement.common.platform.ai.dto.ChatResponse;

/**
 * LLM Client Interface - Provider-agnostic abstraction.
 *
 * <p>Allows switching between different LLM providers (OpenAI, Anthropic, Local) without changing business logic.</p>
 *
 * <p><b>Implementation Strategy:</b></p>
 * <ul>
 *   <li>OpenAIClient - OpenAI API implementation</li>
 *   <li>AnthropicClient - Claude API implementation (future)</li>
 *   <li>LocalLLMClient - Ollama implementation (future)</li>
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

