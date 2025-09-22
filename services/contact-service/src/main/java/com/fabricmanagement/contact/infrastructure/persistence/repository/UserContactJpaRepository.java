package com.fabricmanagement.contact.infrastructure.persistence.repository;

import com.fabricmanagement.contact.domain.model.UserContact;
import com.fabricmanagement.contact.domain.repository.UserContactRepository;
import com.fabricmanagement.contact.infrastructure.persistence.entity.UserContactEntity;
import com.fabricmanagement.contact.application.mapper.UserContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA repository interface for UserContactEntity.
 */
interface UserContactEntityRepository extends JpaRepository<UserContactEntity, UUID> {

    /**
     * Finds a user contact by user ID.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE u.userId = :userId AND u.deleted = false")
    Optional<UserContactEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * Checks if a contact exists for the given user ID.
     */
    @Query("SELECT COUNT(u) > 0 FROM UserContactEntity u WHERE u.userId = :userId AND u.deleted = false")
    boolean existsByUserId(@Param("userId") UUID userId);

    /**
     * Finds all user contacts by tenant ID.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE u.tenantId = :tenantId AND u.deleted = false ORDER BY u.userDisplayName")
    List<UserContactEntity> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Searches user contacts by query string.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE " +
           "(LOWER(u.userDisplayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.personalEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.personalPhone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.city) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND u.tenantId = :tenantId AND u.deleted = false " +
           "ORDER BY u.userDisplayName")
    List<UserContactEntity> searchByQuery(@Param("query") String query, @Param("tenantId") UUID tenantId);

    /**
     * Finds all active user contacts.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE " +
           "u.status = 'ACTIVE' AND u.tenantId = :tenantId AND u.deleted = false " +
           "ORDER BY u.userDisplayName")
    List<UserContactEntity> findActiveContacts(@Param("tenantId") UUID tenantId);

    /**
     * Finds user contacts by preferred contact method.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE " +
           "u.preferredContactMethod = :method AND u.tenantId = :tenantId AND u.deleted = false " +
           "ORDER BY u.userDisplayName")
    List<UserContactEntity> findByPreferredContactMethod(@Param("method") String method, @Param("tenantId") UUID tenantId);

    /**
     * Finds user contacts with public profiles.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE " +
           "u.publicProfile = true AND u.tenantId = :tenantId AND u.deleted = false " +
           "ORDER BY u.userDisplayName")
    List<UserContactEntity> findPublicProfiles(@Param("tenantId") UUID tenantId);

    /**
     * Finds user contacts that allow direct messages.
     */
    @Query("SELECT u FROM UserContactEntity u WHERE " +
           "u.allowDirectMessages = true AND u.tenantId = :tenantId AND u.deleted = false " +
           "ORDER BY u.userDisplayName")
    List<UserContactEntity> findAllowingDirectMessages(@Param("tenantId") UUID tenantId);
}

/**
 * Implementation of UserContactRepository using JPA.
 */
@Repository
@RequiredArgsConstructor
public class UserContactJpaRepository implements UserContactRepository {

    private final UserContactEntityRepository entityRepository;
    private final UserContactMapper userContactMapper;

    @Override
    public UserContact save(UserContact userContact) {
        UserContactEntity entity = userContactMapper.toEntity(userContact);
        UserContactEntity saved = entityRepository.save(entity);
        return userContactMapper.toDomain(saved);
    }

    @Override
    public Optional<UserContact> findById(UUID id) {
        return entityRepository.findById(id)
                .map(userContactMapper::toDomain);
    }

    @Override
    public Optional<UserContact> findByUserId(UUID userId) {
        return entityRepository.findByUserId(userId)
                .map(userContactMapper::toDomain);
    }

    @Override
    public List<UserContact> findByTenantId(UUID tenantId) {
        return entityRepository.findByTenantId(tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return entityRepository.existsByUserId(userId);
    }

    @Override
    public List<UserContact> searchByQuery(String query, UUID tenantId) {
        return entityRepository.searchByQuery(query, tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserContact> findActiveContacts(UUID tenantId) {
        return entityRepository.findActiveContacts(tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserContact> findByPreferredContactMethod(String method, UUID tenantId) {
        return entityRepository.findByPreferredContactMethod(method, tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserContact> findPublicProfiles(UUID tenantId) {
        return entityRepository.findPublicProfiles(tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserContact> findAllowingDirectMessages(UUID tenantId) {
        return entityRepository.findAllowingDirectMessages(tenantId).stream()
                .map(userContactMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        entityRepository.deleteById(id);
    }
}