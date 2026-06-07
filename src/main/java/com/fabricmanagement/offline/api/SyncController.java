package com.fabricmanagement.offline.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.offline.app.SyncService;
import com.fabricmanagement.offline.dto.SyncPullRequest;
import com.fabricmanagement.offline.dto.SyncPullResponse;
import com.fabricmanagement.offline.dto.SyncPushRequest;
import com.fabricmanagement.offline.dto.SyncPushResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for mobile offline synchronization operations.
 *
 * <p>CR-11-11: Provides push (mobile → backend) and pull (backend → mobile) endpoints for offline
 * data synchronization.
 *
 * <h2>Push Flow</h2>
 *
 * <pre>
 * Mobile → POST /api/v1/sync/push → SyncService → Conflict Detection → SyncPushResponse
 * </pre>
 *
 * <h2>Pull Flow</h2>
 *
 * <pre>
 * Mobile → GET /api/v1/sync/pull?lastSyncTimestamp=... → Delta data since timestamp
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Offline Sync", description = "Mobile Offline Synchronization API")
@Slf4j
public class SyncController {

  private final SyncService syncService;

  /**
   * Push offline-created entities from mobile to backend. Each item in the list is processed
   * individually. Returns a list of results (SYNCED or CONFLICT per item).
   */
  @Operation(summary = "Push offline data to backend")
  @PostMapping("/push")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<SyncPushResponse>> pushOfflineData(
      @Valid @RequestBody List<SyncPushRequest> requests) {

    log.info("Received sync push with {} items", requests.size());

    List<SyncPushResponse> results =
        requests.stream()
            .map(
                req ->
                    syncService.processPush(
                        req.getOfflineId(),
                        req.getDeviceId(),
                        req.getOfflineCreatedAt(),
                        req.getEntityType(),
                        req.getPayload()))
            .toList();

    return ResponseEntity.ok(results);
  }

  /**
   * Pull reference data changes since the given timestamp. Used by mobile on reconnection to
   * download only the delta.
   */
  @Operation(summary = "Pull reference data updates for mobile caching")
  @GetMapping("/pull")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SyncPullResponse> pullReferenceData(@Valid SyncPullRequest request) {
    log.info("Received sync pull request: lastSync={}", request.getLastSyncTimestamp());

    SyncPullResponse response = syncService.processPull(TenantContext.requireTenantId(), request);

    return ResponseEntity.ok(response);
  }
}
