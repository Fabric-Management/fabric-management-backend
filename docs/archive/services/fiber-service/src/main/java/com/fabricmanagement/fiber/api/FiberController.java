package com.fabricmanagement.fiber.api;

import com.fabricmanagement.fiber.api.dto.request.*;
import com.fabricmanagement.fiber.api.dto.response.*;
import com.fabricmanagement.fiber.application.service.FiberService;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fibers")
@RequiredArgsConstructor
@Slf4j
public class FiberController {

    private final FiberService fiberService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createFiber(@Valid @RequestBody CreateFiberRequest request) {
        UUID fiberId = fiberService.createFiber(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fiberId, "Fiber created successfully"));
    }

    @PostMapping("/blend")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createBlendFiber(@Valid @RequestBody CreateBlendFiberRequest request) {
        UUID fiberId = fiberService.createBlendFiber(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fiberId, "Blend fiber created successfully"));
    }

    @PatchMapping("/{fiberId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateFiberProperty(
            @PathVariable UUID fiberId,
            @Valid @RequestBody UpdateFiberPropertyRequest request) {
        fiberService.updateFiberProperty(fiberId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Fiber updated successfully"));
    }

    @DeleteMapping("/{fiberId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateFiber(@PathVariable UUID fiberId) {
        fiberService.deactivateFiber(fiberId);
        return ResponseEntity.ok(ApiResponse.success(null, "Fiber deactivated successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<FiberSummaryResponse>> listFibers(Pageable pageable) {
        Page<FiberSummaryResponse> page = fiberService.listFibers(pageable);
        return ResponseEntity.ok(PagedResponse.fromPage(page));
    }

    @GetMapping("/{fiberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FiberResponse>> getFiber(@PathVariable UUID fiberId) {
        FiberResponse fiber = fiberService.getFiber(fiberId);
        return ResponseEntity.ok(ApiResponse.success(fiber));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<List<FiberResponse>>> getDefaultFibers() {
        List<FiberResponse> defaultFibers = fiberService.getDefaultFibers();
        return ResponseEntity.ok(ApiResponse.success(defaultFibers));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FiberSummaryResponse>>> searchFibers(@RequestParam String query) {
        List<FiberSummaryResponse> fibers = fiberService.searchFibers(query);
        return ResponseEntity.ok(ApiResponse.success(fibers));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FiberSummaryResponse>>> getFibersByCategory(@PathVariable String category) {
        List<FiberSummaryResponse> fibers = fiberService.getFibersByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(fibers));
    }

    @PostMapping("/internal/validate")
    public ResponseEntity<ApiResponse<FiberValidationResponse>> validateFiberComposition(@RequestBody List<String> fiberCodes) {
        FiberValidationResponse validation = fiberService.validateComposition(fiberCodes);
        return ResponseEntity.ok(ApiResponse.success(validation));
    }

    @GetMapping("/internal/batch")
    public ResponseEntity<ApiResponse<Map<String, FiberResponse>>> getFibersBatch(@RequestParam List<String> fiberCodes) {
        Map<String, FiberResponse> fibers = fiberService.getFibersBatch(fiberCodes);
        return ResponseEntity.ok(ApiResponse.success(fibers));
    }

    @GetMapping("/internal/exists/{fiberCode}")
    public ResponseEntity<ApiResponse<Boolean>> checkFiberExists(@PathVariable String fiberCode) {
        FiberValidationResponse validation = fiberService.validateComposition(List.of(fiberCode));
        boolean exists = !validation.getActiveFibers().isEmpty();
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}

