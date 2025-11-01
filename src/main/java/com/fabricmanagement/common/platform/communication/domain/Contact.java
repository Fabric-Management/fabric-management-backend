package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Contact entity - Generic contact information for User and Company.
 *
 * <p>Represents any communication channel (email, phone, WhatsApp, etc.)
 * that can be associated with either a User or a Company.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multi-channel support (EMAIL, PHONE, WHATSAPP, FAX, WEBSITE, etc.)</li>
 *   <li>✅ Verification status tracking</li>
 *   <li>✅ Primary contact flag</li>
 *   <li>✅ Label for categorization (e.g., "Home", "Work", "Mobile")</li>
 *   <li>✅ Parent contact link for extensions</li>
 *   <li>✅ Personal vs. company-provided distinction</li>
 * </ul>
 *
 * <h2>Special Cases:</h2>
 * <ul>
 *   <li><b>PHONE_EXTENSION:</b> contactValue = extension number (e.g., "101"),
 *       parentContactId must reference a PHONE contact</li>
 *   <li><b>WHATSAPP:</b> Used for Priority 1 verification and notifications</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // User's personal email
 * Contact personalEmail = Contact.builder()
 *     .contactValue("john.doe@gmail.com")
 *     .contactType(ContactType.EMAIL)
 *     .isVerified(true)
 *     .isPrimary(true)
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
@Table(name = "common_contact", schema = "common_communication",
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
     * <p>Format depends on contactType:
     * <ul>
     *   <li>EMAIL: "user@example.com"</li>
     *   <li>PHONE: "+905551234567" (E.164)</li>
     *   <li>PHONE_EXTENSION: "101" (extension number)</li>
     *   <li>WEBSITE: "https://www.example.com"</li>
     *   <li>WHATSAPP: "+905551234567" (E.164)</li>
     * </ul>
     */
    @Column(name = "contact_value", nullable = false, length = 255)
    private String contactValue;

    /**
     * Contact type (EMAIL, PHONE, PHONE_EXTENSION, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 50)
    private ContactType contactType;

    /**
     * Verification status
     * <p>true = contact has been verified (e.g., email confirmation, SMS code)</p>
     * <p>false = contact not yet verified</p>
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Primary contact flag
     * <p>true = primary contact for this owner (User or Company)</p>
     * <p>Multiple contacts can have isPrimary = true (one per type)</p>
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    /**
     * Label for categorization
     * <p>Examples: "Home", "Work", "Mobile", "Extension 101", "Main Office"</p>
     */
    @Column(name = "label", length = 100)
    private String label;

    /**
     * Parent contact ID (for PHONE_EXTENSION)
     * <p>For PHONE_EXTENSION type, this references the parent PHONE contact</p>
     * <p>Example: Extension "101" → parentContactId = company's main phone contact ID</p>
     */
    @Column(name = "parent_contact_id")
    private UUID parentContactId;

    /**
     * Personal vs. company-provided flag
     * <p>true = User's personal contact (owned by user)</p>
     * <p>false = Company-provided contact (e.g., work email, company phone extension)</p>
     */
    @Column(name = "is_personal", nullable = false)
    @Builder.Default
    private Boolean isPersonal = true;

    /**
     * Mark contact as verified
     */
    public void verify() {
        this.isVerified = true;
    }

    /**
     * Mark contact as primary
     */
    public void setAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Remove primary flag
     */
    public void removePrimary() {
        this.isPrimary = false;
    }

    /**
     * Check if this is a phone extension
     */
    public boolean isExtension() {
        return ContactType.PHONE_EXTENSION.equals(this.contactType);
    }

    /**
     * Check if this is verified and can be used for authentication
     */
    public boolean canBeUsedForAuthentication() {
        return this.isVerified && (ContactType.EMAIL.equals(this.contactType) || 
                                   ContactType.PHONE.equals(this.contactType));
    }

    @Override
    protected String getModuleCode() {
        return "CONT";
    }
}

