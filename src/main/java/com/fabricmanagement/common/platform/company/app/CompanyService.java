package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Service - Business logic for company management.
 *
 * <p>Implements CompanyFacade for cross-module communication.</p>
 *
 * <p>Handles:
 * <ul>
 *   <li>Company CRUD operations</li>
 *   <li>Tenant management</li>
 *   <li>Company hierarchy</li>
 *   <li>Domain event publishing</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService implements CompanyFacade {

    private final CompanyRepository companyRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public CompanyDto createCompany(CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getCompanyName());

        // Validate tenant context
        UUID currentTenantId = TenantContext.getCurrentTenantId();
        if (currentTenantId == null) {
            throw new IllegalStateException("Tenant context must be set to create a company");
        }

        // Tax ID uniqueness check (tenant-bazlı)
        if (companyRepository.existsByTenantIdAndTaxId(currentTenantId, request.getTaxId())) {
            throw new IllegalArgumentException("Company with this tax ID already exists in your organization");
        }

        // Parent company validation
        if (request.getParentCompanyId() != null) {
            Company parent = companyRepository.findById(request.getParentCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Parent company not found"));
            
            // Security check: Parent must belong to same tenant
            if (!parent.getTenantId().equals(currentTenantId)) {
                throw new IllegalArgumentException("Parent company must belong to the same tenant");
            }
            
            // Business check: Parent must be active
            if (!parent.getIsActive()) {
                throw new IllegalArgumentException("Parent company must be active");
            }

            // Prevent circular reference (will be checked after save, but log warning)
            log.debug("Creating company with parent: parentId={}", request.getParentCompanyId());
        }

        // Company type validation (warning for tenant types)
        if (request.getCompanyType().isTenant()) {
            log.warn("Creating tenant-type company via normal endpoint. " +
                     "Consider using onboarding endpoints (/api/admin/onboarding/tenant or /api/public/signup) " +
                     "for proper tenant setup.");
        }

        Company company = Company.create(
            request.getCompanyName(),
            request.getTaxId(),
            request.getCompanyType()
        );

        // Address/Contact info should be added via CompanyAddress/CompanyContact services after creation
        company.setParent(request.getParentCompanyId());

        Company saved = companyRepository.save(company);

        // Circular reference check (after save to have company ID)
        if (saved.hasParent()) {
            UUID parentId = saved.getParentCompanyId();
            Company checkParent = companyRepository.findById(parentId).orElse(null);
            if (checkParent != null && saved.getId().equals(checkParent.getParentCompanyId())) {
                // Rollback would be complex, but log error
                log.error("⚠️ Circular reference detected! Company {} and {} are each other's parent. " +
                         "This should not happen.", saved.getId(), parentId);
            }
        }

        eventPublisher.publish(new CompanyCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getCompanyName(),
            saved.getCompanyType().name()
        ));

        log.info("Company created: id={}, uid={}, tenantId={}", 
            saved.getId(), saved.getUid(), saved.getTenantId());

        return CompanyDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyDto> findById(UUID tenantId, UUID companyId) {
        log.debug("Finding company: tenantId={}, companyId={}", tenantId, companyId);

        return companyRepository.findByTenantIdAndId(tenantId, companyId)
            .map(CompanyDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyDto> findByTenant(UUID tenantId) {
        log.debug("Finding companies by tenant: tenantId={}", tenantId);

        return companyRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(CompanyDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, UUID companyId) {
        log.debug("Checking company existence: tenantId={}, companyId={}", tenantId, companyId);

        return companyRepository.existsByTenantIdAndId(tenantId, companyId);
    }

    @Transactional(readOnly = true)
    public CompanyDto getCompany(UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        return findById(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    }

    @Transactional(readOnly = true)
    public List<CompanyDto> getAllCompanies() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return findByTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CompanyDto> getTenantCompanies() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting tenant companies: tenantId={}", tenantId);

        return companyRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .filter(company -> company.isTenant())
            .map(CompanyDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyDto> getCompaniesByType(CompanyType companyType) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting companies by type: tenantId={}, type={}", tenantId, companyType);

        return companyRepository.findByTenantIdAndCompanyType(tenantId, companyType)
            .stream()
            .map(CompanyDto::from)
            .toList();
    }

    @Transactional
    public void deactivateCompany(UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Deactivating company: tenantId={}, companyId={}", tenantId, companyId);

        Company company = companyRepository.findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        company.delete();
        companyRepository.save(company);

        log.info("Company deactivated: id={}, uid={}", company.getId(), company.getUid());
    }
}

