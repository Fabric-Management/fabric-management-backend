package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.ProfileUpdateRequest;
import com.fabricmanagement.common.platform.user.domain.value.ProfileUpdateRequestStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for profile update request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequestDto {

  private UUID id;
  private UUID userId;
  private String profileCategory;
  private ProfileUpdateRequestStatus status;
  private String requestedChanges;
  private String reason;
  private UUID reviewedBy;
  private String reviewComment;
  private Instant reviewedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public static ProfileUpdateRequestDto from(ProfileUpdateRequest request) {
    if (request == null) {
      return null;
    }

    return ProfileUpdateRequestDto.builder()
        .id(request.getId())
        .userId(request.getUserId())
        .profileCategory(
            request.getProfileCategory() != null ? request.getProfileCategory().name() : null)
        .status(request.getStatus())
        .requestedChanges(request.getRequestedChanges())
        .reason(request.getReason())
        .reviewedBy(request.getReviewedBy())
        .reviewComment(request.getReviewComment())
        .reviewedAt(request.getReviewedAt())
        .createdAt(request.getCreatedAt())
        .updatedAt(request.getUpdatedAt())
        .build();
  }
}
