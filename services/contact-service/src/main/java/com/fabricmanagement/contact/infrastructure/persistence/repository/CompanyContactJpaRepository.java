package com.fabricmanagement.contact.infrastructure.persistence.repository;

import com.fabricmanagement.contact.domain.model.CompanyContact;
import com.fabricmanagement.contact.domain.repository.CompanyContactRepository;
import com.fabricmanagement.contact.domain.valueobject.Industry;
import com.fabricmanagement.contact.infrastructure.persistence.entity.CompanyContactEntity;
import com.fabricmanagement.contact.application.mapper.ContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA repository interface for CompanyContactEntity.
 */
interface CompanyContactEntityRepository extends JpaRepository<CompanyContactEntity, UUID> {

    /**
     * Finds a company contact by company ID.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.companyId = :companyId AND c.deleted = false")
    Optional<CompanyContactEntity> findByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Checks if a contact exists for the given company ID.
     */
    @Query("SELECT COUNT(c) > 0 FROM CompanyContactEntity c WHERE c.companyId = :companyId AND c.deleted = false")
    boolean existsByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Finds company contacts by industry.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.industry = :industry AND c.deleted = false")
    Page<CompanyContactEntity> findByIndustry(@Param("industry") com.fabricmanagement.contact.domain.valueobject.Industry industry, Pageable pageable);

    /**
     * Finds company contacts by industry and tenant.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.industry = :industry AND c.tenantId = :tenantId AND c.deleted = false")
    List<CompanyContactEntity> findByIndustryAndTenantId(@Param("industry") com.fabricmanagement.contact.domain.valueobject.Industry industry, @Param("tenantId") UUID tenantId);

    /**
     * Finds all company contacts by tenant ID.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.tenantId = :tenantId AND c.deleted = false ORDER BY c.companyName")
    List<CompanyContactEntity> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Searches company contacts by query string.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.mainContactPerson) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.registrationNumber) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND c.tenantId = :tenantId AND c.deleted = false " +
           "ORDER BY c.companyName")
    List<CompanyContactEntity> searchByQuery(@Param("query") String query, @Param("tenantId") UUID tenantId);

    /**
     * Finds company contacts with credit limit above the specified amount.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE " +
           "c.creditLimit > :amount AND c.tenantId = :tenantId AND c.deleted = false " +
           "ORDER BY c.creditLimit DESC")
    List<CompanyContactEntity> findByCreditLimitGreaterThan(@Param("amount") Long amount, @Param("tenantId") UUID tenantId);

    /**
     * Finds all active company contacts.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE " +
           "c.status = 'ACTIVE' AND c.tenantId = :tenantId AND c.deleted = false " +
           "ORDER BY c.companyName")
    List<CompanyContactEntity> findActiveContacts(@Param("tenantId") UUID tenantId);
}

/**
 * Implementation of CompanyContactRepository using JPA.
 */
@Repository
@RequiredArgsConstructor
public class CompanyContactJpaRepository implements CompanyContactRepository {

    private final CompanyContactEntityRepository entityRepository;
    private final ContactMapper contactMapper;

    @Override
    public CompanyContact save(CompanyContact companyContact) {
        CompanyContactEntity entity = contactMapper.toEntity(companyContact);
        CompanyContactEntity saved = entityRepository.save(entity);
        return contactMapper.toCompanyContactDomain(saved);
    }

    @Override
    public Optional<CompanyContact> findById(UUID id) {
        return entityRepository.findById(id)
                .map(contactMapper::toCompanyContactDomain);
    }

    @Override
    public Optional<CompanyContact> findByCompanyId(UUID companyId) {
        return entityRepository.findByCompanyId(companyId)
                .map(contactMapper::toCompanyContactDomain);
    }

    @Override
    public List<CompanyContact> findByTenantId(UUID tenantId) {
        return entityRepository.findByTenantId(tenantId).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyContact> findByIndustry(Industry industry) {
        // For this method, we'll fetch all and let the service layer handle pagination if needed
        return entityRepository.findByIndustry(industry, Pageable.unpaged()).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyContact> findByIndustryAndTenantId(Industry industry, UUID tenantId) {
        return entityRepository.findByIndustryAndTenantId(industry, tenantId).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCompanyId(UUID companyId) {
        return entityRepository.existsByCompanyId(companyId);
    }

    @Override
    public List<CompanyContact> searchByQuery(String query, UUID tenantId) {
        return entityRepository.searchByQuery(query, tenantId).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyContact> findByCreditLimitGreaterThan(Long amount, UUID tenantId) {
        return entityRepository.findByCreditLimitGreaterThan(amount, tenantId).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyContact> findActiveContacts(UUID tenantId) {
        return entityRepository.findActiveContacts(tenantId).stream()
                .map(contactMapper::toCompanyContactDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        entityRepository.deleteById(id);
    }
}