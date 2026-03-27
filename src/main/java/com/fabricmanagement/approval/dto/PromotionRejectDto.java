package com.fabricmanagement.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromotionRejectDto {
  @Size(max = 2000)
  private String reason;
}
