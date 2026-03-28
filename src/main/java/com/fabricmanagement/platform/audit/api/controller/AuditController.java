package com.fabricmanagement.platform.audit.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.platform.audit.app.AuditService;
import com.fabricmanagement.platform.audit.domain.AuditLog;
import com.fabricmanagement.platform.audit.dto.AuditLogDto;
import com.fabricmanagement.platform.audit.mapper.AuditMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

  private final AuditService auditService;
  private final AuditMapper auditMapper;

  @GetMapping("/logs")
  public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogs(
      @Valid PageRequestDto pageRequest) {
    log.debug("Getting audit logs");

    Page<AuditLog> page = auditService.getAuditLogs(pageRequest.toPageable());
    Page<AuditLogDto> dtoPage = page.map(auditMapper::toDto);

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(dtoPage)));
  }

  @GetMapping("/logs/user/{userId}")
  public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByUser(
      @PathVariable UUID userId, @Valid PageRequestDto pageRequest) {
    log.debug("Getting audit logs for user: {}", userId);

    Page<AuditLog> page = auditService.getAuditLogsByUser(userId, pageRequest.toPageable());
    Page<AuditLogDto> dtoPage = page.map(auditMapper::toDto);

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(dtoPage)));
  }

  @GetMapping("/logs/resource/{resource}")
  public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByResource(
      @PathVariable String resource, @Valid PageRequestDto pageRequest) {
    log.debug("Getting audit logs for resource: {}", resource);

    Page<AuditLog> page = auditService.getAuditLogsByResource(resource, pageRequest.toPageable());
    Page<AuditLogDto> dtoPage = page.map(auditMapper::toDto);

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(dtoPage)));
  }
}
