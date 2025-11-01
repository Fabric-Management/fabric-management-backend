package com.fabricmanagement.common.platform.ai.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.ai.app.FabricAIService;
import com.fabricmanagement.common.platform.ai.dto.ChatRequestDto;
import com.fabricmanagement.common.platform.ai.dto.ChatResponse;
import com.fabricmanagement.common.platform.ai.dto.ChatResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * FabricAI Controller - REST endpoint for AI assistant.
 */
@RestController
@RequestMapping("/api/common/ai")
@RequiredArgsConstructor
@Slf4j
public class FabricAIController {

    private final FabricAIService fabricAIService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chat(
            @Valid @RequestBody ChatRequestDto request) {
        UUID userId = TenantContext.getCurrentUserId();
        
        if (userId == null) {
            log.error("AI chat request without authenticated user - JWT context missing");
            throw new IllegalStateException("User context required for AI chat");
        }

        log.info("FabricAI chat request: userId={}, conversationId={}, messageLength={}", 
            userId, request.getConversationId(), request.getMessage().length());

        ChatResponse response = fabricAIService.chat(request.getMessage(), userId, request.getConversationId());

        ChatResponseDto responseDto = ChatResponseDto.builder()
            .message(response.getMessage())
            .model(response.getModel())
            .conversationId(response.getConversationId())
            .tokenUsage(response.getTokenUsage() != null 
                ? ChatResponseDto.TokenUsageDto.builder()
                    .promptTokens(response.getTokenUsage().getPromptTokens())
                    .completionTokens(response.getTokenUsage().getCompletionTokens())
                    .totalTokens(response.getTokenUsage().getTotalTokens())
                    .build()
                : null)
            .requiresConfirmation(false) // Action confirmation handled via native function calling
            .build();

        return ResponseEntity.ok(ApiResponse.success(responseDto));
    }
}

