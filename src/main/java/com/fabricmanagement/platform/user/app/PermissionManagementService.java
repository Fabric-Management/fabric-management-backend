package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PermissionRegistry;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.dto.CreatePermissionOverrideRequest;
import com.fabricmanagement.platform.user.dto.CreatePermissionTemplateRequest;
import com.fabricmanagement.platform.user.dto.PermissionOverrideDto;
import com.fabricmanagement.platform.user.dto.PermissionTemplateDto;
import com.fabricmanagement.platform.user.dto.UpdatePermissionTemplateRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionManagementService {

  private final PermissionTemplateRepository templateRepository;
  private final PermissionOverrideRepository overrideRepository;
  private final PermissionEvaluator permissionEvaluator;
  private final UserQueryService userQueryService;

  @Transactional(readOnly = true)
  public List<PermissionTemplateDto> getTemplates(
      UUID tenantId, String roleCode, String departmentCode) {
    List<PermissionTemplate> rawTemplates =
        templateRepository.findTemplatesList(tenantId, roleCode, departmentCode);

    // Filter: If tenant-specific exists for same definition, drop system-default
    // Using LinkedHashMap since query results are ordered by tenantId NULLS LAST,
    // so tenant-specific will overwrite system-default later in the processing loop cleanly.
    Map<String, PermissionTemplate> mergedMap = new LinkedHashMap<>();

    for (PermissionTemplate pt : rawTemplates) {
      String key =
          String.format(
              "%s_%s_%s_%s",
              pt.getRoleCode(),
              pt.getDepartmentCode() == null ? "NULL" : pt.getDepartmentCode(),
              pt.getResource(),
              pt.getAction());
      mergedMap.putIfAbsent(key, pt);
    }

    return mergedMap.values().stream().map(PermissionTemplateDto::from).toList();
  }

  @Transactional
  public PermissionTemplateDto createTemplate(
      UUID tenantId, CreatePermissionTemplateRequest request) {
    validateResourceAction(request.getResource(), request.getAction());

    PermissionTemplate template =
        PermissionTemplate.builder()
            .roleCode(request.getRoleCode())
            .departmentCode(request.getDepartmentCode())
            .resource(request.getResource())
            .action(request.getAction())
            .dataScope(request.getDataScope())
            .build();

    template.setTenantId(tenantId);
    template.setIsActive(true);

    return PermissionTemplateDto.from(templateRepository.save(template));
  }

  @Transactional
  public PermissionTemplateDto updateTemplate(
      UUID tenantId, UUID id, UpdatePermissionTemplateRequest request) {
    PermissionTemplate template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new PlatformDomainException("Template not found", "NOT_FOUND", 404));

    if (template.getTenantId() == null) {
      // Copy-On-Write for system defaults
      PermissionTemplate copy =
          PermissionTemplate.builder()
              .roleCode(template.getRoleCode())
              .departmentCode(template.getDepartmentCode())
              .resource(template.getResource())
              .action(template.getAction())
              .dataScope(request.getDataScope())
              .build();

      copy.setTenantId(tenantId);
      copy.setIsActive(request.getIsActive());

      return PermissionTemplateDto.from(templateRepository.save(copy));
    }

    // Direct update for tenant-specific templates
    if (!template.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException("Unauthorized access to template", "FORBIDDEN", 403);
    }

    template.setDataScope(request.getDataScope());
    template.setIsActive(request.getIsActive());

    return PermissionTemplateDto.from(templateRepository.save(template));
  }

  @Transactional
  public void deleteTemplate(UUID tenantId, UUID id) {
    PermissionTemplate template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new PlatformDomainException("Template not found", "NOT_FOUND", 404));

    if (template.getTenantId() == null) {
      throw new PlatformDomainException(
          "Cannot delete system default templates", "BAD_REQUEST", 400);
    }

    if (!template.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException("Unauthorized access to template", "FORBIDDEN", 403);
    }

    template.setIsActive(false);
    templateRepository.save(template);
  }

  @Transactional(readOnly = true)
  public List<PermissionOverrideDto> getActiveOverrides(UUID tenantId, UUID userId) {
    return overrideRepository.findActiveOverrides(tenantId, userId).stream()
        .map(PermissionOverrideDto::from)
        .toList();
  }

  @Transactional
  public PermissionOverrideDto createOverride(
      UUID tenantId, UUID grantedBy, CreatePermissionOverrideRequest request) {
    validateResourceAction(request.getResource(), request.getAction());

    PermissionOverride override =
        PermissionOverride.builder()
            .userId(request.getUserId())
            .resource(request.getResource())
            .action(request.getAction())
            .dataScope(request.getDataScope())
            .reason(request.getReason())
            .grantedBy(grantedBy)
            .expiresAt(request.getExpiresAt())
            .build();

    override.setTenantId(tenantId);
    override.setIsActive(true);

    return PermissionOverrideDto.from(overrideRepository.save(override));
  }

  @Transactional
  public void deleteOverride(UUID tenantId, UUID id) {
    PermissionOverride override =
        overrideRepository
            .findById(id)
            .orElseThrow(() -> new PlatformDomainException("Override not found", "NOT_FOUND", 404));

    if (!override.getTenantId().equals(tenantId)) {
      throw new PlatformDomainException("Unauthorized access to override", "FORBIDDEN", 403);
    }

    override.setIsActive(false);
    overrideRepository.save(override);
  }

  @Transactional(readOnly = true)
  public Map<String, Map<String, String>> simulateEvaluator(UUID tenantId, UUID targetUserId) {
    UserDto targetUser =
        userQueryService
            .findById(tenantId, targetUserId)
            .orElseThrow(() -> new PlatformDomainException("User not found", "NOT_FOUND", 404));

    PermissionResult result =
        permissionEvaluator.evaluate(
            tenantId,
            targetUser.getRoleCode(),
            targetUser.getDepartmentCodes(),
            targetUser.getId());

    return result.permissions().entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e ->
                    e.getValue().entrySet().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, ie -> ie.getValue().name()))));
  }

  private void validateResourceAction(String resource, String action) {
    if (!PermissionRegistry.isValidResource(resource)) {
      throw new PlatformDomainException("Invalid resource name: " + resource, "BAD_REQUEST", 400);
    }
    if (!PermissionRegistry.isValidAction(action)) {
      throw new PlatformDomainException("Invalid action name: " + action, "BAD_REQUEST", 400);
    }
  }
}
