package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.aggregate.CompanyRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Relationship Repository
 * 
 * Manages trust relationships between companies
 */
@Repository
public interface CompanyRelationshipRepository extends JpaRepository<CompanyRelationship, UUID> {
    
    @Query("SELECT r FROM CompanyRelationship r WHERE r.sourceCompanyId = :companyId")
    List<CompanyRelationship> findBySourceCompanyId(@Param("companyId") UUID companyId);
    
    @Query("SELECT r FROM CompanyRelationship r WHERE r.targetCompanyId = :companyId")
    List<CompanyRelationship> findByTargetCompanyId(@Param("companyId") UUID companyId);
    
    @Query("SELECT r FROM CompanyRelationship r WHERE r.sourceCompanyId = :sourceId AND r.targetCompanyId = :targetId")
    Optional<CompanyRelationship> findBySourceAndTarget(@Param("sourceId") UUID sourceId, 
                                                         @Param("targetId") UUID targetId);
    
    @Query("SELECT r FROM CompanyRelationship r WHERE " +
           "(r.sourceCompanyId = :companyId OR r.targetCompanyId = :companyId) AND r.status = 'ACTIVE'")
    List<CompanyRelationship> findActiveRelationships(@Param("companyId") UUID companyId);
}

