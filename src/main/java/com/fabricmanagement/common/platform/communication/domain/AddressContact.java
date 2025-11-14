package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AddressContact junction entity - Links Address to Contact.
 *
 * <p>Represents the relationship between an Address and Contact information.
 * Supports address-specific contacts for both Company and User addresses.</p>
 *
 * <p><b>⚠️ IMPORTANT:</b> This is for <b>address-specific contacts</b> (location-based).
 * For <b>user-level contacts</b> (authentication, notifications), use {@link UserContact}.
 * For <b>company-wide contacts</b> (default, department), use {@link CompanyContact}.</p>
 *
 * <h2>When to Use AddressContact vs Other Contact Types:</h2>
 * <ul>
 *   <li><b>AddressContact:</b> Location-specific contacts (warehouse phone, branch email)</li>
 *   <li><b>UserContact:</b> User's personal/work contacts for authentication</li>
 *   <li><b>CompanyContact:</b> Company-wide default contact, department contacts</li>
 * </ul>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple contacts per address (phone, email, fax, etc.)</li>
 *   <li>✅ Primary contact flag (one per address)</li>
 *   <li>✅ Label for categorization</li>
 *   <li>✅ Works for both company and user addresses</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Link phone contact to company headquarters address
 * AddressContact hqPhone = AddressContact.builder()
 *     .address(hqAddress)
 *     .contact(phoneContact)
 *     .isPrimary(true)
 *     .label("Main Phone")
 *     .build();
 *
 * // Link email contact to warehouse address
 * AddressContact warehouseEmail = AddressContact.builder()
 *     .address(warehouseAddress)
 *     .contact(emailContact)
 *     .isPrimary(true)
 *     .label("Warehouse Email")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_address_contact", schema = "common_communication",
    indexes = {
        @Index(name = "idx_address_contact_address", columnList = "address_id"),
        @Index(name = "idx_address_contact_contact", columnList = "contact_id"),
        @Index(name = "idx_address_contact_tenant", columnList = "tenant_id"),
        @Index(name = "idx_address_contact_primary", columnList = "address_id, is_primary")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AddressContactId.class)
public class AddressContact extends BaseJunctionEntity {

    @Id
    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Id
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", insertable = false, updatable = false)
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private Contact contact;

    /**
     * Primary contact flag
     * <p>true = primary contact for this address (one per address)</p>
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    /**
     * Label for categorization
     * <p>Examples: "Main Phone", "Reception", "Emergency Contact", "Warehouse Email"</p>
     */
    @Column(name = "label", length = 100)
    private String label;

    /**
     * Set as primary contact for this address
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

    @Override
    protected String getModuleCode() {
        return "ADCN";
    }
}

