package com.fabricmanagement.identity.infrastructure.persistence.entity;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for UserContact persistence.
 */
@Entity
@Table(name = "user_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserContactEntity {

    @Id
    @GeneratedValue(generator = "uuid4")
    @GenericGenerator(name = "uuid4", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

    @Column(name = "contact_value", nullable = false, unique = true)
    private String contactValue;

    @Column(name = "is_verified", nullable = false)
    private Boolean verified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}