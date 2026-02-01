package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.exception.CircularHierarchyException;
import com.fabricmanagement.common.platform.company.domain.exception.HierarchyDepthExceededException;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company hierarchy operations: parent validation, depth limit, circular reference check, children
 * and ancestors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyHierarchyService {

  private static final int MAX_HIERARCHY_DEPTH = 3;

  private final CompanyRepository companyRepository;

  /**
   * Validates that the given company can be used as parent: same tenant, active, depth and circular
   * checks.
   *
   * @param parentCompanyId candidate parent company ID
   * @throws IllegalArgumentException if parent not found or wrong tenant/inactive
   * @throws HierarchyDepthExceededException if depth would exceed max
   */
  public void validateParent(UUID parentCompanyId) {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context must be set");
    }

    Company parent =
        companyRepository
            .findById(parentCompanyId)
            .orElseThrow(() -> new IllegalArgumentException("Parent company not found"));

    if (!parent.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Parent company belongs to a different tenant");
    }

    if (!parent.getIsActive()) {
      throw new IllegalArgumentException("Parent company must be active");
    }

    int depth = calculateDepth(parentCompanyId);
    if (depth >= MAX_HIERARCHY_DEPTH) {
      throw new HierarchyDepthExceededException(
          "Maximum hierarchy depth (" + MAX_HIERARCHY_DEPTH + ") exceeded");
    }
  }

  /**
   * Validates that setting newParentId as parent of companyId would not create a circular
   * reference.
   *
   * @param companyId company whose parent is being set
   * @param newParentId candidate new parent
   */
  public void validateNoCircularReference(UUID companyId, UUID newParentId) {
    if (companyId.equals(newParentId)) {
      throw new CircularHierarchyException("Company cannot be its own parent");
    }
    if (isDescendantOf(newParentId, companyId)) {
      throw new CircularHierarchyException("Cannot set parent: would create circular reference");
    }
  }

  /** Depth of the chain starting at companyId (1 = company itself, 2 = company + parent, …). */
  public int calculateDepth(UUID companyId) {
    int depth = 0;
    UUID currentId = companyId;
    int guard = 0;
    while (currentId != null && guard <= MAX_HIERARCHY_DEPTH + 2) {
      Company company =
          companyRepository
              .findById(currentId)
              .orElseThrow(() -> new IllegalArgumentException("Company not found"));
      currentId = company.getParentCompanyId();
      depth++;
      guard++;
    }
    return depth;
  }

  /** Direct children of the company. */
  @Transactional(readOnly = true)
  public List<CompanyDto> getChildren(UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyRepository.findByTenantIdAndParentCompanyId(tenantId, companyId).stream()
        .map(CompanyDto::from)
        .toList();
  }

  /** Ancestors from immediate parent up to root. */
  @Transactional(readOnly = true)
  public List<CompanyDto> getAncestors(UUID companyId) {
    List<CompanyDto> ancestors = new ArrayList<>();
    UUID tenantId = TenantContext.getCurrentTenantId();
    Company company =
        companyRepository
            .findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    UUID currentId = company.getParentCompanyId();
    int guard = 0;
    while (currentId != null && guard <= MAX_HIERARCHY_DEPTH + 2) {
      Company parent = companyRepository.findByTenantIdAndId(tenantId, currentId).orElse(null);
      if (parent == null) break;
      ancestors.add(CompanyDto.from(parent));
      currentId = parent.getParentCompanyId();
      guard++;
    }
    return ancestors;
  }

  /**
   * True if companyId is a descendant of ancestorId (ancestorId is up the chain from companyId).
   */
  @Transactional(readOnly = true)
  public boolean isDescendantOf(UUID companyId, UUID ancestorId) {
    if (companyId.equals(ancestorId)) return true;
    UUID tenantId = TenantContext.getCurrentTenantId();
    Company company =
        companyRepository
            .findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    UUID currentId = company.getParentCompanyId();
    int guard = 0;
    while (currentId != null && guard <= MAX_HIERARCHY_DEPTH + 2) {
      if (currentId.equals(ancestorId)) return true;
      Company parent = companyRepository.findByTenantIdAndId(tenantId, currentId).orElse(null);
      if (parent == null) break;
      currentId = parent.getParentCompanyId();
      guard++;
    }
    return false;
  }
}
