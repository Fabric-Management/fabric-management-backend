package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyCategory;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only company queries with optional caching. Used by CompanyFacade and controllers. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyQueryService {

  private final CompanyRepository companyRepository;

  @Transactional(readOnly = true)
  public Optional<CompanyDto> findById(UUID tenantId, UUID companyId) {
    log.debug("Finding company: tenantId={}, companyId={}", tenantId, companyId);
    return companyRepository.findByTenantIdAndId(tenantId, companyId).map(CompanyDto::from);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "companies-by-tenant", key = "#tenantId.toString()")
  public List<CompanyDto> findByTenant(UUID tenantId) {
    log.debug("Finding companies by tenant: tenantId={}", tenantId);
    return companyRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(CompanyDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> findByType(UUID tenantId, CompanyType type) {
    log.debug("Finding companies by type: tenantId={}, type={}", tenantId, type);
    return companyRepository.findByTenantIdAndCompanyType(tenantId, type).stream()
        .map(CompanyDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CompanyDto> findByCategory(UUID tenantId, CompanyCategory category) {
    log.debug("Finding companies by category: tenantId={}, category={}", tenantId, category);
    return companyRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .filter(c -> c.getCompanyType().getCategory() == category)
        .map(CompanyDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID companyId) {
    return companyRepository.existsByTenantIdAndId(tenantId, companyId);
  }

  /** Companies in tenant excluding root (id = tenantId). */
  @Transactional(readOnly = true)
  public List<CompanyDto> findTenantCompaniesExcludingRoot(UUID tenantId) {
    log.debug("Finding companies excluding root: tenantId={}", tenantId);
    return companyRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .filter(c -> !c.getId().equals(c.getTenantId()))
        .map(CompanyDto::from)
        .toList();
  }

  /** Tenant-type companies only (SPINNER, WEAVER, …). */
  @Transactional(readOnly = true)
  public List<CompanyDto> findTenantCompanies(UUID tenantId) {
    log.debug("Finding tenant-type companies: tenantId={}", tenantId);
    return companyRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .filter(Company::isTenant)
        .map(CompanyDto::from)
        .toList();
  }
}
