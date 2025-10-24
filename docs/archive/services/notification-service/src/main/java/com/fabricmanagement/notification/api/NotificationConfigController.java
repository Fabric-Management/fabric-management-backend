package com.fabricmanagement.notification.api;

import com.fabricmanagement.notification.api.dto.request.CreateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.request.UpdateNotificationConfigRequest;
import com.fabricmanagement.notification.api.dto.response.NotificationConfigResponse;
import com.fabricmanagement.notification.application.service.NotificationConfigService;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Notification Config Controller
 * 
 * REST API for tenant notification configuration management.
 * 
 * Security:
 * - Only TENANT_ADMIN or SUPER_ADMIN can manage configs
 * - Tenant isolation enforced
 * - Passwords/API keys masked in responses
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/notifications/config")
@RequiredArgsConstructor
@Slf4j
public class NotificationConfigController {
    
    private final NotificationConfigService configService;
    
    /**
     * Create notification config
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationConfigResponse>> createConfig(
            @Valid @RequestBody CreateNotificationConfigRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating notification config: {} for tenant: {}", request.getChannel(), ctx.getTenantId());
        
        NotificationConfigResponse response = configService.createConfig(
            request,
            ctx.getTenantId(),
            ctx.getUserId().toString()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Notification config created successfully"));
    }
    
    /**
     * Get config by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationConfigResponse>> getConfig(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting notification config: {}", id);
        
        NotificationConfigResponse response = configService.getConfig(id, ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * List all configs for tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationConfigResponse>>> listConfigs(
            @RequestParam(required = false, defaultValue = "false") boolean enabledOnly,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Listing notification configs for tenant: {} (enabledOnly: {})", ctx.getTenantId(), enabledOnly);
        
        List<NotificationConfigResponse> configs = enabledOnly
            ? configService.listEnabledConfigsByTenant(ctx.getTenantId())
            : configService.listConfigsByTenant(ctx.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.success(configs));
    }
    
    /**
     * Update config
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationConfigResponse>> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNotificationConfigRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating notification config: {}", id);
        
        NotificationConfigResponse response = configService.updateConfig(
            id,
            request,
            ctx.getTenantId(),
            ctx.getUserId().toString()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response, "Notification config updated successfully"));
    }
    
    /**
     * Delete config (soft delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting notification config: {}", id);
        
        configService.deleteConfig(id, ctx.getTenantId(), ctx.getUserId().toString());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Notification config deleted successfully"));
    }
}

