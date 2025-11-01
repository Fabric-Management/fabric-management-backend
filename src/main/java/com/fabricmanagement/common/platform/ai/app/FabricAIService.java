package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.ai.config.AIProperties;
import com.fabricmanagement.common.platform.ai.dto.ChatRequest;
import com.fabricmanagement.common.platform.ai.dto.ChatResponse;
import com.fabricmanagement.common.platform.ai.infra.client.LLMClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FabricAI Service - Business logic for AI assistant.
 *
 * <p>Handles user interactions with FabricAI, manages prompts, and coordinates LLM calls.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FabricAIService {

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final AIProperties aiProperties;
    private final UserFacade userFacade;
    private final AIFunctionCaller functionCaller;
    private final ObjectMapper objectMapper;
    private final ConversationStore conversationStore;

    @Transactional(readOnly = true)
    public ChatResponse chat(String userMessage, UUID userId, UUID conversationId) {
        if (!aiProperties.getEnabled()) {
            throw new IllegalStateException("AI features are disabled");
        }

        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("FabricAI chat request: tenantId={}, userId={}, conversationId={}, messageLength={}", 
            tenantId, userId, conversationId, userMessage.length());

        // Get user context for personalization (optional for testing)
        UserDto user = null;
        if (userId != null) {
            user = userFacade.findById(tenantId, userId).orElse(null);
        }

        // Get conversation history if conversationId provided
        List<Map<String, Object>> conversationHistory = conversationStore.getHistory(conversationId);
        
        // Build prompt with history
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Add system message (only once, at the start)
        if (conversationHistory.isEmpty()) {
            // New conversation - add system prompt
            List<Map<String, String>> promptMessages = promptBuilder.buildPrompt("", user);
            for (Map<String, String> msg : promptMessages) {
                if ("system".equals(msg.get("role"))) {
                    Map<String, Object> msgObj = new HashMap<>(msg);
                    messages.add(msgObj);
                    break; // Only add system message
                }
            }
        } else {
            // Existing conversation - reuse system message from history
            // Find first system message
            for (Map<String, Object> msg : conversationHistory) {
                if ("system".equals(msg.get("role"))) {
                    messages.add(msg);
                    break;
                }
            }
            // Add conversation history (excluding system message)
            conversationHistory.stream()
                .filter(msg -> !"system".equals(msg.get("role")))
                .forEach(messages::add);
        }

        // Add current user message
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Add OpenAI function calling tools
        List<Map<String, Object>> tools = AIToolBuilder.getAvailableTools();

        // Create chat request with function calling tools
        ChatRequest chatRequest = ChatRequest.builder()
            .messages(messages)
            .model(aiProperties.getModel())
            .temperature(aiProperties.getTemperature())
            .maxTokens(aiProperties.getMaxTokens())
            .tools(tools)
            .toolChoice("auto")
            .build();

        // Call LLM - may return tool calls or direct response
        ChatResponse response = llmClient.chat(chatRequest);
        
        // Handle function calling loop (max 3 iterations)
        for (int iteration = 0; iteration < 3; iteration++) {
            // Check if response contains tool calls
            if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                log.info("AI requested {} function calls", response.getToolCalls().size());
                
                // Add assistant message with tool calls
                Map<String, Object> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", response.getMessage());
                
                // Add tool_calls to message
                List<Map<String, Object>> toolCalls = response.getToolCalls().stream()
                    .map(tc -> {
                        Map<String, Object> toolCall = new HashMap<>();
                        toolCall.put("id", tc.getId());
                        toolCall.put("type", "function");
                        Map<String, Object> function = new HashMap<>();
                        function.put("name", tc.getFunctionName());
                        function.put("arguments", tc.getArguments());
                        toolCall.put("function", function);
                        return toolCall;
                    })
                    .toList();
                assistantMsg.put("tool_calls", toolCalls);
                messages.add(assistantMsg);
                
                // Execute all tool calls
                for (ChatResponse.ToolCall toolCall : response.getToolCalls()) {
                    try {
                        // Parse arguments JSON
                        Map<String, Object> parameters = parseJsonArguments(toolCall.getArguments());
                        
                        // Execute function
                        String functionResult = functionCaller.executeFunction(
                            toolCall.getFunctionName(),
                            parameters
                        );
                        
                        // Add tool result to messages
                        Map<String, Object> toolResultMsg = new HashMap<>();
                        toolResultMsg.put("role", "tool");
                        toolResultMsg.put("content", functionResult);
                        toolResultMsg.put("tool_call_id", toolCall.getId());
                        messages.add(toolResultMsg);
                        
                        log.info("Executed function: {} with result length: {}", 
                            toolCall.getFunctionName(), functionResult.length());
                    } catch (Exception e) {
                        log.error("Error executing function: {}", toolCall.getFunctionName(), e);
                        // Add error message
                        Map<String, Object> errorMsg = new HashMap<>();
                        errorMsg.put("role", "tool");
                        errorMsg.put("content", "Error: " + e.getMessage());
                        errorMsg.put("tool_call_id", toolCall.getId());
                        messages.add(errorMsg);
                    }
                }
                
                // Call LLM again with function results
                ChatRequest followUpRequest = ChatRequest.builder()
                    .messages(messages)
                    .model(aiProperties.getModel())
                    .temperature(aiProperties.getTemperature())
                    .maxTokens(aiProperties.getMaxTokens())
                    .tools(tools)
                    .toolChoice("auto")
                    .build();
                
                response = llmClient.chat(followUpRequest);
            } else {
                // No more tool calls, break
                break;
            }
        }

        // Store conversation history
        UUID finalConversationId = conversationStore.addMessage(
            conversationId,
            "user",
            userMessage,
            null
        );
        
        // Store assistant response
        List<Map<String, Object>> assistantToolCalls = null;
        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
            assistantToolCalls = response.getToolCalls().stream()
                .map(tc -> {
                    Map<String, Object> toolCall = new HashMap<>();
                    toolCall.put("id", tc.getId());
                    toolCall.put("type", "function");
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", tc.getFunctionName());
                    function.put("arguments", tc.getArguments());
                    toolCall.put("function", function);
                    return toolCall;
                })
                .toList();
        }
        
        conversationStore.addMessage(
            finalConversationId,
            "assistant",
            response.getMessage(),
            assistantToolCalls
        );

        log.info("FabricAI response: tenantId={}, userId={}, conversationId={}, tokens={}", 
            tenantId, userId, finalConversationId,
            response.getTokenUsage() != null ? response.getTokenUsage().getTotalTokens() : 0);

        // Set conversationId in response
        response.setConversationId(finalConversationId);
        
        return response;
    }

    /**
     * Parse JSON arguments from OpenAI tool call.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse function arguments JSON: {}", argumentsJson, e);
            return Map.of();
        }
    }
}

