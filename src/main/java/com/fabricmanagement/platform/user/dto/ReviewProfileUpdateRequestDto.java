package com.fabricmanagement.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for reviewing (approving/rejecting) a profile update request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewProfileUpdateRequestDto {

  /** Review comment from HR/Admin. */
  @NotBlank(message = "Review comment is required")
  private String reviewComment;
}
