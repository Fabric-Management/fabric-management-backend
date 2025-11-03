package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.UpdateCompanyRequest;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.CompanyContactService;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.CompanyAddressService;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.util.PiiMaskingUtil;
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
    
    // USER-FRIENDLY: Auto-create Contact/Address services
    private final ContactService contactService;
    private final CompanyContactService companyContactService;
    private final AddressService addressService;
    private final CompanyAddressService companyAddressService;

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

        company.setParent(request.getParentCompanyId());

        Company saved = companyRepository.save(company);

        // USER-FRIENDLY: Auto-create Contact and Address if provided
        // This reduces user errors and simplifies workflow (one form instead of multiple steps)
        autoCreateCompanyContactAndAddress(saved.getId(), saved.getTenantId(), request);

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
    public CompanyDto updateCompany(UUID companyId, UpdateCompanyRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating company: tenantId={}, companyId={}", tenantId, companyId);

        Company company = companyRepository.findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        // Tax ID uniqueness check (if changed)
        if (!company.getTaxId().equals(request.getTaxId())) {
            if (companyRepository.existsByTenantIdAndTaxId(tenantId, request.getTaxId())) {
                throw new IllegalArgumentException(
                    "Company with this tax ID already exists in your organization"
                );
            }
        }

        // Parent company validation
        if (request.getParentCompanyId() != null) {
            Company parent = companyRepository.findById(request.getParentCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Parent company not found"));

            if (!parent.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException(
                    "Parent company must belong to the same tenant"
                );
            }

            if (!parent.getIsActive()) {
                throw new IllegalArgumentException("Parent company must be active");
            }

            // Prevent circular reference (company cannot be its own parent)
            if (parent.getId().equals(companyId)) {
                throw new IllegalArgumentException("Company cannot be its own parent");
            }

            // Prevent setting a child as parent (would create circular reference)
            // Check if requested parent has this company as its parent
            if (parent.getParentCompanyId() != null && parent.getParentCompanyId().equals(companyId)) {
                throw new IllegalArgumentException(
                    "Cannot set parent: Would create circular reference"
                );
            }
        }

        // Update company information
        company.update(request.getCompanyName(), request.getTaxId());
        if (request.getParentCompanyId() != null) {
            company.setParent(request.getParentCompanyId());
        } else {
            // Allow clearing parent company
            company.setParent(null);
        }

        Company saved = companyRepository.save(company);

        eventPublisher.publish(new CompanyUpdatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getCompanyName()
        ));

        log.info("Company updated: id={}, uid={}", saved.getId(), saved.getUid());

        return CompanyDto.from(saved);
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

    /**
     * USER-FRIENDLY: Auto-create Contact and Address for Company if provided.
     * 
     * <p>Reduces user errors by automatically creating Contact/Address entities when
     * email/phone/address information is provided in the request.</p>
     * 
     * <p>Benefits:
     * <ul>
     *   <li>One form instead of multiple steps</li>
     *   <li>Cleaner data (no missing contacts/addresses)</li>
     *   <li>Faster workflow</li>
     * </ul>
     */
    private void autoCreateCompanyContactAndAddress(UUID companyId, UUID tenantId, 
                                                     CreateCompanyRequest request) {
        UUID originalTenantId = TenantContext.getCurrentTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            
            // Auto-create Email Contact if provided
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                try {
                    com.fabricmanagement.common.platform.communication.domain.Contact emailContact = 
                        contactService.createContact(
                            request.getEmail(),
                            ContactType.EMAIL,
                            "Main Email",
                            false, // isPersonal (company contact)
                            null   // parentContactId
                        );
                    
                    companyContactService.assignContact(
                        companyId,
                        emailContact.getId(),
                        true,  // isDefault (first contact = default)
                        null   // department (company-wide)
                    );
                    
                    log.info("✅ Company email contact auto-created: companyId={}, email={}", 
                        companyId, PiiMaskingUtil.maskEmail(request.getEmail()));
                } catch (Exception e) {
                    log.warn("Failed to auto-create company email contact: companyId={}, error={}", 
                        companyId, e.getMessage());
                    // Continue - contact creation is not critical
                }
            }
            
            // Auto-create Phone Contact if provided
            if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                try {
                    com.fabricmanagement.common.platform.communication.domain.Contact phoneContact = 
                        contactService.createContact(
                            request.getPhoneNumber(),
                            ContactType.PHONE,
                            "Main Phone",
                            false, // isPersonal (company contact)
                            null   // parentContactId
                        );
                    
                    // Set as default only if email wasn't provided
                    boolean isDefault = request.getEmail() == null || request.getEmail().isBlank();
                    companyContactService.assignContact(
                        companyId,
                        phoneContact.getId(),
                        isDefault,
                        null   // department
                    );
                    
                    log.info("✅ Company phone contact auto-created: companyId={}", companyId);
                } catch (Exception e) {
                    log.warn("Failed to auto-create company phone contact: companyId={}, error={}", 
                        companyId, e.getMessage());
                    // Continue
                }
            }
            
            // Auto-create Address if provided
            if (hasAddressInfo(request)) {
                try {
                    com.fabricmanagement.common.platform.communication.domain.Address address = 
                        addressService.createAddress(
                            request.getAddress() != null ? request.getAddress() : "",
                            request.getCity() != null ? request.getCity() : "",
                            null, // state
                            null, // postalCode
                            request.getCountry() != null ? request.getCountry() : "",
                            AddressType.HEADQUARTERS,
                            "Headquarters"
                        );
                    
                    companyAddressService.assignAddress(
                        companyId,
                        address.getId(),
                        true,  // isPrimary
                        true   // isHeadquarters
                    );
                    
                    log.info("✅ Company address auto-created: companyId={}, city={}", 
                        companyId, request.getCity());
                } catch (Exception e) {
                    log.warn("Failed to auto-create company address: companyId={}, error={}", 
                        companyId, e.getMessage());
                    // Continue
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto-create company contact/address: companyId={}, error={}", 
                companyId, e.getMessage());
            // Continue - contact/address creation is not critical for company creation
        } finally {
            if (originalTenantId != null) {
                TenantContext.setCurrentTenantId(originalTenantId);
            }
        }
    }

    /**
     * Check if request has address information.
     */
    private boolean hasAddressInfo(CreateCompanyRequest request) {
        return (request.getAddress() != null && !request.getAddress().isBlank()) ||
               (request.getCity() != null && !request.getCity().isBlank()) ||
               (request.getCountry() != null && !request.getCountry().isBlank());
    }
}

