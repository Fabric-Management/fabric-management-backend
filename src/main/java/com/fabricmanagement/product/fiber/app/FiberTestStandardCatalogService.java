package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.common.exception.ProductDomainException;
import com.fabricmanagement.product.fiber.domain.FiberTestStandard;
import com.fabricmanagement.product.fiber.dto.FiberTestStandardDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberTestStandardRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberTestStandardCatalogService {

  private static final String CODE_REVISION_INDEX = "uq_fiber_test_standard_code_revision";

  private final FiberTestStandardRepository repository;

  public Page<FiberTestStandardDto> search(String search, Pageable pageable) {
    return repository
        .searchActive(TenantContext.requireTenantId(), search.strip(), pageable)
        .map(FiberTestStandardDto::from);
  }

  public FiberTestStandardDto requireActive(UUID id) {
    return repository
        .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), id)
        .map(FiberTestStandardDto::from)
        .orElseThrow(() -> new NotFoundException("Fibre test standard is unavailable"));
  }

  @Transactional
  public FiberTestStandardDto create(String code, String name, String revision) {
    try {
      return FiberTestStandardDto.from(
          repository.saveAndFlush(new FiberTestStandard(code, name, revision)));
    } catch (DataIntegrityViolationException failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && CODE_REVISION_INDEX.equals(violation.getConstraintName())) {
          throw new DuplicateTestStandardException(failure);
        }
      }
      throw failure;
    }
  }

  @Transactional
  public FiberTestStandardDto rename(UUID id, String name) {
    FiberTestStandard standard =
        repository
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Fibre test standard is unavailable"));
    standard.rename(name);
    return FiberTestStandardDto.from(standard);
  }

  @Transactional
  public void deactivate(UUID id) {
    FiberTestStandard standard =
        repository
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .orElseThrow(() -> new NotFoundException("Fibre test standard is unavailable"));
    if (Boolean.TRUE.equals(standard.getIsActive())) {
      standard.delete();
    }
  }

  private static final class DuplicateTestStandardException extends ProductDomainException {
    private DuplicateTestStandardException(Throwable cause) {
      super(
          "This fibre test standard code and revision already exist",
          "FIBER_TEST_STANDARD_DUPLICATE",
          409,
          cause);
    }
  }
}
