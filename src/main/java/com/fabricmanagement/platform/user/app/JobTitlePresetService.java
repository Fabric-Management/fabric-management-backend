package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.JobTitlePreset;
import com.fabricmanagement.platform.user.domain.port.JobTitlePresetRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobTitlePresetService {

  private final JobTitlePresetRepository jobTitlePresetRepository;

  @Transactional(readOnly = true)
  public List<JobTitlePreset> findAllActive() {
    return jobTitlePresetRepository.findByTenantIdAndIsActiveTrue(
        TenantContext.getCurrentTenantId());
  }

  @Transactional
  public JobTitlePreset createCustom(
      String name, String description, String roleCode, String departmentCode) {
    String code = JobTitlePreset.generateCode(name);
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (jobTitlePresetRepository.existsByTenantIdAndJobTitleCode(tenantId, code)) {
      throw new IllegalArgumentException(
          "Job title with code '" + code + "' already exists for this tenant.");
    }
    JobTitlePreset preset =
        JobTitlePreset.createCustom(name, description, roleCode, departmentCode);
    return jobTitlePresetRepository.save(preset);
  }

  @Transactional
  public JobTitlePreset updateName(UUID id, String newName) {
    JobTitlePreset preset = getById(id);
    validateCodeUniqueness(id, newName);
    preset.updateName(newName);
    return jobTitlePresetRepository.save(preset);
  }

  @Transactional
  public JobTitlePreset updateFull(
      UUID id,
      String newName,
      String newDescription,
      String newRoleCode,
      String newDepartmentCode) {
    JobTitlePreset preset = getById(id);
    validateCodeUniqueness(id, newName);
    preset.updateFull(newName, newDescription, newDepartmentCode, newRoleCode);
    return jobTitlePresetRepository.save(preset);
  }

  @Transactional
  public void deactivate(UUID id) {
    JobTitlePreset preset = getById(id);
    preset.delete();
    jobTitlePresetRepository.save(preset);
  }

  @Transactional
  public void reactivate(UUID id) {
    JobTitlePreset preset = getById(id);
    preset.activate();
    jobTitlePresetRepository.save(preset);
  }

  @Transactional
  public void deleteCustom(UUID id) {
    JobTitlePreset preset = getById(id);
    preset.validateHardDeletion();
    jobTitlePresetRepository.delete(preset);
  }

  // ─── Private Helpers ───

  private JobTitlePreset getById(UUID id) {
    return jobTitlePresetRepository
        .findByTenantIdAndId(TenantContext.getCurrentTenantId(), id)
        .orElseThrow(() -> new EntityNotFoundException("JobTitlePreset not found: " + id));
  }

  /**
   * Ensures the new code generated from the name does not collide with another preset in the same
   * tenant.
   */
  private void validateCodeUniqueness(UUID currentPresetId, String newName) {
    String newCode = JobTitlePreset.generateCode(newName);
    UUID tenantId = TenantContext.getCurrentTenantId();
    jobTitlePresetRepository
        .findByTenantIdAndJobTitleCode(tenantId, newCode)
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(currentPresetId)) {
                throw new IllegalArgumentException(
                    "A job title with code '" + newCode + "' already exists.");
              }
            });
  }
}
