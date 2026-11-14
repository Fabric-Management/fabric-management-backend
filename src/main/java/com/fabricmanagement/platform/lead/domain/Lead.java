package com.fabricmanagement.platform.lead.domain;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Permanent, tenant-independent marketing lead captured at registration. */
@Entity
@Table(name = "common_lead", schema = "common_company")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Lead {

  @Column(name = "signup_id", nullable = false, updatable = false, unique = true)
  private UUID signupId;

  /** Independent linkage survives deletion of the nullable operational tenant FK. */
  @Column(name = "signup_tenant_id")
  private UUID signupTenantId;

  @Column(name = "signup_user_id")
  private UUID signupUserId;

  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "signup_snapshot", columnDefinition = "jsonb")
  private CompanyEvidence signupSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "accepted_snapshot", columnDefinition = "jsonb")
  private CompanyEvidence acceptedSnapshot;

  @Column(name = "accepted_revision", nullable = false)
  private long acceptedRevision;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "uid", nullable = false, unique = true, updatable = false, length = 100)
  private String uid;

  @Column(name = "company_name", nullable = false, length = 255)
  private String companyName;

  @Column(name = "tax_id", length = 50)
  private String taxId;

  @Enumerated(EnumType.STRING)
  @Column(name = "organization_type", nullable = false, length = 50)
  private OrganizationType organizationType;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "work_email", nullable = false, length = 255)
  private String workEmail;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "selected_os", nullable = false, columnDefinition = "jsonb")
  private List<String> selectedOs;

  @Column(name = "signup_intent", nullable = false, length = 50)
  private String signupIntent;

  @Column(name = "trial_tenant_id")
  private UUID trialTenantId;

  /**
   * Null while the signup form has been sent but its e-mail link has not been used (an unverified
   * lead: sales may follow it up, a person decides). Set when the signup is linked to its tenant.
   */
  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version", nullable = false)
  @Builder.Default
  private Long version = 0L;

  public static Lead create(
      String companyName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      String workEmail,
      List<String> selectedOs,
      String signupIntent,
      UUID trialTenantId) {
    return Lead.builder()
        .companyName(companyName)
        .taxId(taxId)
        .organizationType(organizationType)
        .firstName(firstName)
        .lastName(lastName)
        .workEmail(workEmail)
        .selectedOs(selectedOs == null ? List.of() : List.copyOf(selectedOs))
        .signupIntent(signupIntent)
        .trialTenantId(trialTenantId)
        .build();
  }

  /** A lead for a signup form whose e-mail address is not proved yet. */
  public static Lead createUnverified(
      UUID signupId,
      String companyName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      String workEmail,
      List<String> selectedOs,
      String signupIntent) {
    Lead lead =
        create(
            companyName,
            taxId,
            organizationType,
            firstName,
            lastName,
            workEmail,
            selectedOs,
            signupIntent,
            null);
    lead.signupId = signupId;
    return lead;
  }

  /** The same person sent the form again before using the link: keep one lead, latest details. */
  public void refreshUnverified(
      String companyName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      List<String> selectedOs,
      String signupIntent) {
    if (verifiedAt != null || signupTenantId != null)
      throw new com.fabricmanagement.platform.common.exception.PlatformDomainException(
          "Signup evidence is immutable", "ONBOARDING_SIGNUP_IMMUTABLE", 409);
    this.companyName = companyName;
    this.taxId = taxId;
    this.organizationType = organizationType;
    this.firstName = firstName;
    this.lastName = lastName;
    this.selectedOs = selectedOs == null ? List.of() : List.copyOf(selectedOs);
    this.signupIntent = signupIntent;
  }

  public boolean isVerified() {
    return verifiedAt != null;
  }

  public void linkSignup(
      UUID correlation, UUID tenant, UUID user, CompanyEvidence facts, Instant termsAt) {
    if (signupSnapshot != null)
      throw new com.fabricmanagement.platform.common.exception.PlatformDomainException(
          "Signup evidence is immutable", "ONBOARDING_SIGNUP_IMMUTABLE", 409);
    signupId = correlation;
    signupTenantId = tenant;
    signupUserId = user;
    signupSnapshot = facts;
    acceptedSnapshot = facts;
    termsAcceptedAt = termsAt;
    if (verifiedAt == null) verifiedAt = Instant.now();
    if (trialTenantId == null) trialTenantId = tenant;
  }

  public long accept(CompanyEvidence evidence) {
    acceptedSnapshot = evidence;
    return ++acceptedRevision;
  }

  @PrePersist
  void onCreate() {
    if (signupId == null) signupId = UUID.randomUUID();
    if (uid == null || uid.isBlank()) {
      uid = "LEAD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    if (selectedOs == null) {
      selectedOs = List.of();
    }
    if (isActive == null) {
      isActive = true;
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
