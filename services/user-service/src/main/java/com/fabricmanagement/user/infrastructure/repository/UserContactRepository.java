package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.valueobject.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserContact Repository Interface
 * 
 * Provides data access methods for UserContact value objects
 */
@Repository
public interface UserContactRepository extends JpaRepository<UserContact, UUID> {
    
    /**
     * Find contact by contact value
     */
    Optional<UserContact> findByContactValue(String contactValue);
    
    /**
     * Find contacts by user ID
     */
    List<UserContact> findByUserId(UUID userId);
    
    /**
     * Find contacts by contact type
     */
    List<UserContact> findByContactType(UserContact.ContactType contactType);
    
    /**
     * Find verified contacts
     */
    List<UserContact> findByIsVerifiedTrue();
    
    /**
     * Find primary contacts
     */
    List<UserContact> findByIsPrimaryTrue();
    
    /**
     * Find contacts by user ID and contact type
     */
    List<UserContact> findByUserIdAndContactType(UUID userId, UserContact.ContactType contactType);
    
    /**
     * Check if contact value exists
     */
    boolean existsByContactValue(String contactValue);
    
    /**
     * Count contacts by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Find contacts by user ID and verification status
     */
    List<UserContact> findByUserIdAndIsVerified(UUID userId, boolean isVerified);
}
