package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Contact entity - Generic contact information for User and Company.
 *
 * <p>Represents any communication channel (email, phone, WhatsApp, etc.) that can be associated
 * with either a User or a Company.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>✅ Multi-channel support (EMAIL, PHONE, FAX, WEBSITE, SOCIAL_MEDIA, etc.)
 *   <li>✅ Verification status tracking
 *   <li>✅ Primary contact flag
 *   <li>✅ Label for categorization (e.g., "Home", "Work", "Mobile")
 *   <li>✅ Parent contact link for extensions
 *   <li>✅ Personal vs. company-provided distinction
 *   <li>✅ WhatsApp capability flag (for PHONE contacts)
 * </ul>
 *
 * <h2>Special Cases:</h2>
 *
 * <ul>
 *   <li><b>PHONE_EXTENSION:</b> contactValue = extension number (e.g., "101"), parentContactId must
 *       reference a PHONE contact
 *   <li><b>PHONE + isWhatsApp:</b> Used for Priority 1 verification and notifications via WhatsApp
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * // User's personal email
 * Contact personalEmail = Contact.builder()
 *     .contactValue("john.doe@gmail.com")
 *     .contactType(ContactType.EMAIL)
 *     .isVerified(true)
 *     .label("Personal Email")
 *     .isPersonal(true)
 *     .build();
 *
 * // Company phone extension
 * Contact extension = Contact.builder()
 *     .contactValue("101")
 *     .contactType(ContactType.PHONE_EXTENSION)
 *     .parentContactId(companyPhoneContactId)
 *     .label("Extension 101")
 *     .isPersonal(false)
 *     .build();
 * }</pre>
 */
@Entity
@Table(
    name = "common_contact",
    schema = "common_communication",
    indexes = {
      @Index(name = "idx_contact_value", columnList = "contact_value"),
      @Index(name = "idx_contact_type", columnList = "contact_type"),
      @Index(name = "idx_contact_parent", columnList = "parent_contact_id"),
      @Index(name = "idx_contact_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contact extends BaseEntity {

  /**
   * Contact value (email address, phone number, URL, etc.)
   *
   * <p>Format depends on contactType:
   *
   * <ul>
   *   <li>EMAIL: "user@example.com"
   *   <li>PHONE: "+905551234567" (E.164) - WhatsApp capability via isWhatsApp flag
   *   <li>PHONE_EXTENSION: "101" (extension number)
   *   <li>WEBSITE: "https://www.example.com"
   *   <li>SOCIAL_MEDIA: "@username"
   * </ul>
   */
  @Column(name = "contact_value", nullable = false, length = 255)
  private String contactValue;

  /** Contact type (EMAIL, PHONE, PHONE_EXTENSION, etc.) */
  @Enumerated(EnumType.STRING)
  @Column(name = "contact_type", nullable = false, length = 50)
  private ContactType contactType;

  /**
   * Verification status
   *
   * <p>true = contact has been verified (e.g., email confirmation, SMS code)
   *
   * <p>false = contact not yet verified
   */
  @Column(name = "is_verified", nullable = false)
  @Builder.Default
  private Boolean isVerified = false;

  /**
   * Label for categorization
   *
   * <p>Examples: "Home", "Work", "Mobile", "Extension 101", "Main Office"
   */
  @Column(name = "label", length = 100)
  private String label;

  /**
   * Parent contact ID (for PHONE_EXTENSION)
   *
   * <p>For PHONE_EXTENSION type, this references the parent PHONE contact
   *
   * <p>Example: Extension "101" → parentContactId = company's main phone contact ID
   */
  @Column(name = "parent_contact_id")
  private UUID parentContactId;

  /**
   * Personal vs. company-provided flag
   *
   * <p>true = User's personal contact (owned by user)
   *
   * <p>false = Company-provided contact (e.g., work email, company phone extension)
   */
  @Column(name = "is_personal", nullable = false)
  @Builder.Default
  private Boolean isPersonal = true;

  /** Mark contact as verified */
  public void verify() {
    this.isVerified = true;
  }

  /** Check if this is a phone extension */
  public boolean isExtension() {
    return ContactType.PHONE_EXTENSION.equals(this.contactType);
  }

  /** Check if this is verified and can be used for authentication */
  public boolean canBeUsedForAuthentication() {
    return this.isVerified
        && (ContactType.EMAIL.equals(this.contactType)
            || (this.contactType != null && this.contactType.isPhone()));
  }

  @Override
  protected String getModuleCode() {
    return "CONT";
  }
}
