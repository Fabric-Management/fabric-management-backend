package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.platform.user.domain.value.ProfileUpdateRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Profile update request entity.
 *
 * <p>Allows users to request profile changes that require HR/Admin approval.
 *
 * <h2>Workflow:</h2>
 *
 * <ol>
 *   <li>User submits request → Status: PENDING
 *   <li>HR/Admin reviews and approves/rejects → Status: APPROVED/REJECTED
 *   <li>If approved, profile is updated and user is notified
 * </ol>
 *
 * <h2>Multi-Tenancy:</h2>
 *
 * <p>Inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.
 */
@Entity
@Table(
    name = "profile_update_request",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_profile_req_user", columnList = "tenant_id,user_id"),
      @Index(name = "idx_profile_req_status", columnList = "tenant_id,status"),
      @Index(name = "idx_profile_req_created", columnList = "tenant_id,created_at")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  /** Category of profile being updated (WORK_PROFILE or PERSONAL_PROFILE). */
  @Enumerated(EnumType.STRING)
  @Column(name = "profile_category", nullable = false, length = 50)
  private ProfileCategory profileCategory;

  /** Current status of the request. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  @Builder.Default
  private ProfileUpdateRequestStatus status = ProfileUpdateRequestStatus.PENDING;

  /**
   * Requested changes in JSON format. Example: {"firstName": "John", "lastName": "Smith",
   * "personalPhone": "+1234567890"}
   */
  @Column(name = "requested_changes", columnDefinition = "JSONB")
  private String requestedChanges;

  /** Reason provided by user for the request. */
  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  /** ID of the HR/Admin who reviewed the request. */
  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  /** Review comments from HR/Admin. */
  @Column(name = "review_comment", columnDefinition = "TEXT")
  private String reviewComment;

  /** When the request was reviewed (approved/rejected). */
  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  /** Approve this request. */
  public void approve(UUID reviewedBy, String reviewComment) {
    this.status = ProfileUpdateRequestStatus.APPROVED;
    this.reviewedBy = reviewedBy;
    this.reviewComment = reviewComment;
    this.reviewedAt = Instant.now();
  }

  /** Reject this request. */
  public void reject(UUID reviewedBy, String reviewComment) {
    this.status = ProfileUpdateRequestStatus.REJECTED;
    this.reviewedBy = reviewedBy;
    this.reviewComment = reviewComment;
    this.reviewedAt = Instant.now();
  }

  /** Check if request is pending. */
  public boolean isPending() {
    return this.status == ProfileUpdateRequestStatus.PENDING;
  }

  /** Check if request is approved. */
  public boolean isApproved() {
    return this.status == ProfileUpdateRequestStatus.APPROVED;
  }

  /** Check if request is rejected. */
  public boolean isRejected() {
    return this.status == ProfileUpdateRequestStatus.REJECTED;
  }

  @Override
  protected String getModuleCode() {
    return "PRUR"; // Profile Update Request
  }
}
