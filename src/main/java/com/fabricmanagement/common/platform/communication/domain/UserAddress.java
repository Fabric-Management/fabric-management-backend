package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * UserAddress junction entity - Links User to Address.
 *
 * <p>Represents the relationship between a User and their Address information.
 * Supports multiple addresses per user (home, work, shipping, etc.).</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple addresses per user</li>
 *   <li>✅ Primary address flag</li>
 *   <li>✅ Work address flag (independent from company address)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Link user to home address
 * UserAddress homeAddress = UserAddress.builder()
 *     .user(user)
 *     .address(homeAddressEntity)
 *     .isPrimary(true)
 *     .isWorkAddress(false)
 *     .build();
 *
 * // Link user to work address (independent from company)
 * UserAddress workAddress = UserAddress.builder()
 *     .user(user)
 *     .address(workAddressEntity)
 *     .isPrimary(false)
 *     .isWorkAddress(true)
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_user_address", schema = "common_communication",
    indexes = {
        @Index(name = "idx_user_address_user", columnList = "user_id"),
        @Index(name = "idx_user_address_address", columnList = "address_id"),
        @Index(name = "idx_user_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserAddressId.class)
public class UserAddress extends BaseJunctionEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", insertable = false, updatable = false)
    private Address address;

    /**
     * Primary address flag
     * <p>true = primary address for this user</p>
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    /**
     * Work address flag
     * <p>true = work/office address (independent from company's address)</p>
     * <p>false = personal address (e.g., home)</p>
     */
    @Column(name = "is_work_address", nullable = false)
    @Builder.Default
    private Boolean isWorkAddress = false;

    /**
     * Set as primary address
     */
    public void setAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Mark as work address
     */
    public void setAsWorkAddress() {
        this.isWorkAddress = true;
    }

    @Override
    protected String getModuleCode() {
        return "UADR";
    }
}
