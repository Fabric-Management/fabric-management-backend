package com.fabricmanagement.common.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Chat Request DTO for REST endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

  @NotBlank(message = "Message is required")
  @Size(max = 2000, message = "Message must not exceed 2000 characters")
  private String message;

  /** Optional conversation ID for multi-turn conversations */
  private UUID conversationId;

  /** Optional context (screen, filters, etc.) */
  private Map<String, Object> context;
}
