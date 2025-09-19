package com.fabricmanagement.identity.infrastructure.persistence.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.*;
import com.fabricmanagement.identity.infrastructure.persistence.entity.UserContactEntity;
import com.fabricmanagement.identity.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of UserRepository using JPA.
 * Simple mapper between domain and persistence models.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
            .map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username)
            .map(this::toDomain);
    }

    @Override
    public Optional<User> findByContact(String contactValue) {
        return jpaRepository.findByContactValue(contactValue)
            .map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByContact(String contactValue) {
        return jpaRepository.existsByContactValue(contactValue);
    }

    @Override
    public void deleteById(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }

    // Simple mapping - not using MapStruct to keep it minimal

    private UserEntity toEntity(User user) {
        UserEntity entity = UserEntity.builder()
            .tenantId(user.getTenantId())
            .username(user.getUsername())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .role(user.getRole())
            .status(user.getStatus())
            .failedLoginAttempts(user.getFailedLoginAttempts())
            .lockedUntil(user.getLockedUntil())
            .lastLoginAt(user.getLastLoginAt())
            .lastLoginIp(user.getLastLoginIp())
            .twoFactorEnabled(user.isTwoFactorEnabled())
            .twoFactorSecret(user.getTwoFactorSecret())
            .passwordMustChange(user.isPasswordMustChange())
            .primaryContactId(user.getPrimaryContactId() != null ? user.getPrimaryContactId().getValue() : null)
            .build();

        // Set ID if exists
        if (user.getId() != null) {
            entity.setId(user.getId().getValue());
        }

        // Set credentials
        if (user.getCredentials() != null) {
            entity.setPasswordHash(user.getCredentials().getPasswordHash());
            entity.setPasswordCreatedAt(user.getCredentials().getCreatedAt());
            entity.setPasswordChangedAt(user.getCredentials().getLastChangedAt());
        }

        // Set audit fields from domain model's AuditInfo
        if (user.getAuditInfo() != null) {
            entity.setCreatedAt(user.getAuditInfo().getCreatedAt());
            entity.setUpdatedAt(user.getAuditInfo().getUpdatedAt());
            entity.setCreatedBy(user.getAuditInfo().getCreatedBy());
            entity.setUpdatedBy(user.getAuditInfo().getUpdatedBy());
        }

        // Map contacts
        for (UserContact contact : user.getContacts()) {
            UserContactEntity contactEntity = UserContactEntity.builder()
                .id(contact.getId().getValue())
                .user(entity)
                .contactType(contact.getType())
                .contactValue(contact.getValue())
                .verified(contact.isVerified())
                .verifiedAt(contact.getVerifiedAt())
                .isPrimary(contact.isPrimary())
                .lastUsedAt(contact.getLastUsedAt())
                .createdAt(contact.getCreatedAt())
                .build();
            entity.getContacts().add(contactEntity);
        }

        return entity;
    }

    private User toDomain(UserEntity entity) {
        User user = User.builder()
            .id(new UserId(entity.getId()))
            .tenantId(entity.getTenantId())
            .username(entity.getUsername())
            .firstName(entity.getFirstName())
            .lastName(entity.getLastName())
            .role(entity.getRole())
            .status(entity.getStatus())
            .credentials(entity.getPasswordHash() != null ?
                Credentials.of(entity.getPasswordHash()) : null)
            .failedLoginAttempts(entity.getFailedLoginAttempts() != null ? entity.getFailedLoginAttempts() : 0)
            .lockedUntil(entity.getLockedUntil())
            .lastLoginAt(entity.getLastLoginAt())
            .lastLoginIp(entity.getLastLoginIp())
            .twoFactorEnabled(entity.getTwoFactorEnabled() != null && entity.getTwoFactorEnabled())
            .twoFactorSecret(entity.getTwoFactorSecret())
            .passwordMustChange(entity.getPasswordMustChange() != null && entity.getPasswordMustChange())
            .auditInfo(AuditInfo.builder()
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .deleted(entity.getDeleted())
                .build())
            .build();

        // Map contacts directly to the list
        for (UserContactEntity contactEntity : entity.getContacts()) {
            UserContact contact = UserContact.builder()
                .id(new ContactId(contactEntity.getId()))
                .userId(new UserId(entity.getId()))
                .type(contactEntity.getContactType())
                .value(contactEntity.getContactValue())
                .verified(contactEntity.getVerified() != null && contactEntity.getVerified())
                .verifiedAt(contactEntity.getVerifiedAt())
                .isPrimary(contactEntity.getIsPrimary() != null && contactEntity.getIsPrimary())
                .lastUsedAt(contactEntity.getLastUsedAt())
                .createdAt(contactEntity.getCreatedAt())
                .build();

            user.getContacts().add(contact);

            if (contactEntity.getIsPrimary() != null && contactEntity.getIsPrimary()) {
                user.setPrimaryContactId(new ContactId(contactEntity.getId()));
            }
        }

        return user;
    }
}