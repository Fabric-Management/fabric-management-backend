
package com.fabricmanagement.contact.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.valueobject.ContactStatus;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base contact entity that can be associated with users, companies, or other entities.
 * This entity serves as the parent for all contact information.
 */
@Entity
@Table(name = "contacts", indexes = {
    @Index(name = "idx_contact_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_contact_type", columnList = "contact_type"),
    @Index(name = "idx_contact_status", columnList = "status"),
    @Index(name = "idx_contact_deleted", columnList = "deleted")
})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "entity_type", discriminatorType = DiscriminatorType.STRING)
@SQLDelete(sql = "UPDATE contacts SET deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@ToString(callSuper = true, exclude = {"emails", "phones", "addresses"})
@EqualsAndHashCode(callSuper = true, exclude = {"emails", "phones", "addresses"})
public abstract class ContactEntity extends BaseEntity {

    @NotNull(message = "Tenant ID is required")
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull(message = "Contact type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 30)
    private ContactType contactType;

    @NotNull(message = "Contact status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContactStatus status = ContactStatus.ACTIVE;

    // Basic identity fields
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    @Column(name = "last_name", length = 100)
    private String lastName;

    @Size(max = 200, message = "Display name cannot exceed 200 characters")
    @Column(name = "display_name", length = 200)
    private String displayName;

    @Size(max = 5000, message = "Notes cannot exceed 5000 characters")
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, createdAt ASC")
    private Set<ContactEmailEntity> emails = new HashSet<>();

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, createdAt ASC")
    private Set<ContactPhoneEntity> phones = new HashSet<>();

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, createdAt ASC")
    private Set<ContactAddressEntity> addresses = new HashSet<>();

    /**
     * Adds an email to this contact.
     */
    public void addEmail(ContactEmailEntity email) {
        emails.add(email);
        email.setContact(this);
    }

    /**
     * Removes an email from this contact.
     */
    public void removeEmail(ContactEmailEntity email) {
        emails.remove(email);
        email.setContact(null);
    }

    /**
     * Adds a phone to this contact.
     */
    public void addPhone(ContactPhoneEntity phone) {
        phones.add(phone);
        phone.setContact(this);
    }

    /**
     * Removes a phone from this contact.
     */
    public void removePhone(ContactPhoneEntity phone) {
        phones.remove(phone);
        phone.setContact(null);
    }

    /**
     * Adds an address to this contact.
     */
    public void addAddress(ContactAddressEntity address) {
        addresses.add(address);
        address.setContact(this);
    }

    /**
     * Removes an address from this contact.
     */
    public void removeAddress(ContactAddressEntity address) {
        addresses.remove(address);
        address.setContact(null);
    }

    /**
     * Gets the primary email if exists.
     */
    public ContactEmailEntity getPrimaryEmail() {
        return emails.stream()
            .filter(ContactEmailEntity::getIsPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the primary phone if exists.
     */
    public ContactPhoneEntity getPrimaryPhone() {
        return phones.stream()
            .filter(ContactPhoneEntity::getIsPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the primary address if exists.
     */
    public ContactAddressEntity getPrimaryAddress() {
        return addresses.stream()
            .filter(ContactAddressEntity::getIsPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Marks this contact as deleted (soft delete).
     * Overrides BaseEntity to also update contact status.
     */
    @Override
    public void markAsDeleted() {
        super.markAsDeleted(); // Call parent's implementation
        this.status = ContactStatus.INACTIVE;
    }

    @PrePersist
    protected void prePersist() {
        if (status == null) {
            status = ContactStatus.ACTIVE;
        }
    }
}