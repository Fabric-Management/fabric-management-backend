package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.domain.SourceDesignationPolicy;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillLockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnBlankDesignationRemediationService {

  private final YarnBackfillLockRepository lockRepository;
  private final YarnArticleRepository articleRepository;
  private final YarnArticleService articleService;

  @Transactional
  public long remediateTenant(UUID tenantId) {
    if (tenantId == null || !tenantId.equals(TenantContext.requireTenantId())) {
      throw new IllegalArgumentException("Remediation tenant must match TenantContext");
    }
    lockRepository.acquireBlankRemediation(tenantId);
    return articleRepository.findWritableSourceDesignationCandidates(tenantId).stream()
        .filter(row -> SourceDesignationPolicy.isBlank(row.getSourceDesignation()))
        .map(YarnArticleRepository.SourceDesignationCandidate::getId)
        .filter(articleService::remediateBlankSourceDesignation)
        .count();
  }
}
