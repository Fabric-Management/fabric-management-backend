package com.fabricmanagement.notification.application.service;

import com.fabricmanagement.notification.api.dto.request.CreateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.request.UpdateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.response.NotificationConfigResponse;
import com.fabricmanagement.notification.application.mapper.NotificationConfigMapper;
import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.infrastructure.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Notification Config Service
 * 
 * Business logic for tenant notification configuration management.
 * 
 * Security:
 * - Tenant isolation enforced
 * - Passwords/API keys encrypted (future: Jasypt/KMS)
 * - Only tenant owner or SUPER_ADMIN can manage
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConfigService {
    
    private final NotificationConfigRepository repository;
    private final NotificationConfigMapper mapper;
    
    /**
     * Create new notification config
     */
    @Transactional
    public NotificationConfigResponse createConfig(
            CreateNotificationConfigRequest request,
            UUID tenantId,
            String userId) {
        
        log.info("Creating notification config: {} for tenant: {}", request.getChannel(), tenantId);
        
        // Map to entity
        NotificationConfig config = mapper.toEntity(request, tenantId);
        config.setCreatedBy(userId);
        config.setUpdatedBy(userId);
        
        // Save
        config = repository.save(config);
        
        log.info("✅ Notification config created: {} (id: {})", config.getChannel(), config.getId());
        
        return mapper.toResponse(config);
    }
    
    /**
     * Get config by ID (tenant-isolated)
     */
    @Transactional(readOnly = true)
    public NotificationConfigResponse getConfig(UUID id, UUID tenantId) {
        log.debug("Getting notification config: {} for tenant: {}", id, tenantId);
        
        NotificationConfig config = repository.findByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Notification config not found: " + id));
        
        return mapper.toResponse(config);
    }
    
    /**
     * List all configs for tenant
     */
    @Transactional(readOnly = true)
    public List<NotificationConfigResponse> listConfigsByTenant(UUID tenantId) {
        log.debug("Listing notification configs for tenant: {}", tenantId);
        
        return repository.findAllByTenant(tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }
    
    /**
     * List enabled configs for tenant
     */
    @Transactional(readOnly = true)
    public List<NotificationConfigResponse> listEnabledConfigsByTenant(UUID tenantId) {
        log.debug("Listing enabled notification configs for tenant: {}", tenantId);
        
        return repository.findEnabledConfigsByTenant(tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }
    
    /**
     * Update config (partial update)
     */
    @Transactional
    public NotificationConfigResponse updateConfig(
            UUID id,
            UpdateNotificationConfigRequest request,
            UUID tenantId,
            String userId) {
        
        log.info("Updating notification config: {} for tenant: {}", id, tenantId);
        
        // Find config (tenant-isolated)
        NotificationConfig config = repository.findByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Notification config not found: " + id));
        
        // Apply partial update
        mapper.applyUpdate(config, request);
        config.setUpdatedBy(userId);
        
        // Save
        config = repository.save(config);
        
        log.info("✅ Notification config updated: {}", id);
        
        return mapper.toResponse(config);
    }
    
    /**
     * Delete config (soft delete)
     */
    @Transactional
    public void deleteConfig(UUID id, UUID tenantId, String userId) {
        log.info("Deleting notification config: {} for tenant: {}", id, tenantId);
        
        // Find config (tenant-isolated)
        NotificationConfig config = repository.findByIdAndTenant(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Notification config not found: " + id));
        
        // Soft delete
        config.markAsDeleted();
        config.setUpdatedBy(userId);
        repository.save(config);
        
        log.info("✅ Notification config deleted: {}", id);
    }
}

