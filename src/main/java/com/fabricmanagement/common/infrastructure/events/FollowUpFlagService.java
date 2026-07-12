package com.fabricmanagement.common.infrastructure.events;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowUpFlagService {

  private final IncompleteFollowUpFlagRepository flagRepository;

  public List<FollowUpFlagDto> findActiveForRecord(String entityType, UUID entityId) {
    UUID tenantId = TenantContext.requireTenantId();
    return flagRepository
        .findByTenantIdAndEntityTypeAndEntityIdAndStatus(
            tenantId, entityType, entityId, FollowUpFlagStatus.ACTIVE)
        .stream()
        .map(FollowUpFlagDto::from)
        .toList();
  }

  public List<FollowUpFlagDto> findActiveForTenant() {
    UUID tenantId = TenantContext.requireTenantId();
    return flagRepository.findByTenantIdAndStatus(tenantId, FollowUpFlagStatus.ACTIVE).stream()
        .map(FollowUpFlagDto::from)
        .toList();
  }
}
