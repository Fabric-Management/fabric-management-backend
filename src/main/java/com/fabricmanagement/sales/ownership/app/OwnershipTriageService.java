package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.sales.ownership.dto.OwnershipTriageCaseResponse;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnershipTriageService {

  private final OwnershipTriageQueryRepository queryRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Page<OwnershipTriageCaseResponse> list(UUID tenantId, Pageable pageable) {
    Instant now = Instant.now(clock);
    return queryRepository
        .findPage(tenantId, pageable)
        .map(triageCase -> OwnershipTriageCaseResponse.from(triageCase, now));
  }
}
