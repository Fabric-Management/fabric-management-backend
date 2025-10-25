package com.fabricmanagement.common.platform.audit.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.platform.audit.app.AuditService;
import com.fabricmanagement.common.platform.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/common/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAuditLogs(Pageable pageable) {
        log.debug("Getting audit logs");

        Page<AuditLog> page = auditService.getAuditLogs(pageable);

        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/logs/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAuditLogsByUser(
            @PathVariable UUID userId, Pageable pageable) {
        log.debug("Getting audit logs for user: {}", userId);

        Page<AuditLog> page = auditService.getAuditLogsByUser(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/logs/resource/{resource}")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getAuditLogsByResource(
            @PathVariable String resource, Pageable pageable) {
        log.debug("Getting audit logs for resource: {}", resource);

        Page<AuditLog> page = auditService.getAuditLogsByResource(resource, pageable);

        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }
}

