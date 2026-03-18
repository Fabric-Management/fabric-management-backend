package com.fabricmanagement.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectRequestDto {
  @NotBlank private String reason;
}
