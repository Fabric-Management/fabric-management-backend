package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyWithContactRequest;
import com.fabricmanagement.common.platform.company.dto.CreateInitialSubscriptionsResult;
import com.fabricmanagement.common.platform.company.dto.CreateTenantCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.UpdateCompanyRequest;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company Service — facade and lifecycle.
 *
 * <p>Implements {@link CompanyFacade} for cross-module communication. Delegates creation to {@link
 * CompanyCreationService}, queries to {@link CompanyQueryService}, hierarchy validation to {@link
 * CompanyHierarchyService}. Keeps {@link #updateCompany} and {@link #deactivateCompany} for
 * lifecycle operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService implements CompanyFacade {

  private final CompanyCreationService companyCreationService;
  private final CompanyQueryService companyQueryService;
  private final CompanyHierarchyService hierarchyService;
  private final CompanyRepository companyRepository;
  private final DepartmentRepository departmentRepository;
  private final SubscriptionService subscriptionService;
  private final TenantSeedService tenantSeedService;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public CompanyDto createCompany(CreateCompanyRequest request) {
    return companyCreationService.createCompany(request);
  }

  @Transactional
  public CompanyDto createCompanyWithContact(CreateCompanyWithContactRequest request) {
    return companyCreationService.createCompanyWithContact(request);
  }

  @Override
  @Transactional
  public CompanyDto createTenantCompany(CreateTenantCompanyRequest request) {
    return companyCreationService.createTenantCompany(request);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findDepartmentIdByName(
      UUID tenantId, UUID companyId, String departmentName) {
    if (departmentName == null || departmentName.isBlank()) {
      return Optional.empty();
    }
    return departmentRepository
        .findByTenantIdAndCompanyIdAndDepartmentName(tenantId, companyId, departmentName)
        .map(d -> d.getId());
  }

  @Override
  @Transactional
  public CreateInitialSubscriptionsResult createInitialSubscriptions(
      UUID tenantId, List<String> selectedOS, int trialDays) {
    return subscriptionService.createInitialSubscriptions(tenantId, selectedOS, trialDays);
  }

  @Override
  @Transactional
  public void seedDepartmentsAndPositions(UUID tenantId, UUID companyId) {
    tenantSeedService.seedDepartmentsAndPositions(tenantId, companyId);
  }

  @Override
  @Transactional
  public void assignCompanyAddressAndContact(
      UUID companyId,
      UUID tenantId,
      String address,
      String city,
      String country,
      String phoneNumber,
      String email) {
    companyCreationService.addCompanyAddressAndContactFlat(
        companyId, tenantId, address, city, country, phoneNumber, email);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CompanyDto> findById(UUID tenantId, UUID companyId) {
    return companyQueryService.findById(tenantId, companyId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CompanyDto> findByTenant(UUID tenantId) {
    return companyQueryService.findByTenant(tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID companyId) {
    return companyQueryService.exists(tenantId, companyId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByTaxId(String taxId) {
    return companyRepository.existsByTaxId(taxId);
  }

  @Transactional(readOnly = true)
  public CompanyDto getCompany(UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyQueryService
        .findById(tenantId, companyId)
        .orElseThrow(() -> new IllegalArgumentException("Company not found"));
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> getAllCompanies() {
    return companyQueryService.findByTenant(TenantContext.getCurrentTenantId());
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> getOtherCompanies() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyQueryService.findTenantCompaniesExcludingRoot(tenantId);
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> getTenantCompanies() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyQueryService.findTenantCompanies(tenantId);
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> getCompaniesByType(CompanyType companyType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyQueryService.findByType(tenantId, companyType);
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> getCompaniesByCategory(
      com.fabricmanagement.common.platform.company.domain.CompanyCategory category) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyQueryService.findByCategory(tenantId, category);
  }

  @Transactional
  public CompanyDto updateCompany(UUID companyId, UpdateCompanyRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating company: tenantId={}, companyId={}", tenantId, companyId);

    Company company =
        companyRepository
            .findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

    boolean isRootCompany = company.getId().equals(company.getTenantId());
    if (isRootCompany && !company.getTaxId().equals(request.getTaxId())) {
      throw new DomainException(
          "Primary company tax ID cannot be changed. It was set during registration.");
    }

    if (!company.getTaxId().equals(request.getTaxId())) {
      if (companyRepository.existsByTenantIdAndTaxId(tenantId, request.getTaxId())) {
        throw new TaxIdAlreadyExistsException(
            "Company with this tax ID already exists in your organization");
      }
    }

    if (request.getParentCompanyId() != null) {
      hierarchyService.validateParent(request.getParentCompanyId());
      hierarchyService.validateNoCircularReference(companyId, request.getParentCompanyId());
      Company parent =
          companyRepository
              .findById(request.getParentCompanyId())
              .orElseThrow(() -> new IllegalArgumentException("Parent company not found"));
      if (!parent.getTenantId().equals(tenantId)) {
        throw new IllegalArgumentException("Parent company must belong to the same tenant");
      }
      if (!parent.getIsActive()) {
        throw new IllegalArgumentException("Parent company must be active");
      }
    }

    company.update(request.getCompanyName(), request.getTaxId());
    company.setParent(request.getParentCompanyId());
    Company saved = companyRepository.save(company);

    eventPublisher.publish(
        new CompanyUpdatedEvent(saved.getTenantId(), saved.getId(), saved.getCompanyName()));
    log.info("Company updated: id={}, uid={}", saved.getId(), saved.getUid());
    return CompanyDto.from(saved);
  }

  @Transactional
  public void deactivateCompany(UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Deactivating company: tenantId={}, companyId={}", tenantId, companyId);

    Company company =
        companyRepository
            .findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

    company.delete();
    companyRepository.save(company);
    log.info("Company deactivated: id={}, uid={}", company.getId(), company.getUid());
  }
}
