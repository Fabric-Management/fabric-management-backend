package com.fabricmanagement.human.compliance.localization.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.InternalEndpoint;
import com.fabricmanagement.human.compliance.localization.app.HrCountryPackMappingService;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackCommandService;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackMapper;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackResolver;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.dto.AssignCountryPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.CreateHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.HrCountryPackMappingResponse;
import com.fabricmanagement.human.compliance.localization.dto.HrInheritanceModeDto;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackLineageResponse;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackResponse;
import com.fabricmanagement.human.compliance.localization.dto.PublishHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.RetireHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.UpdateHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.ValidateHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.ValidateHrPolicyPackResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/internal/hr/policy-packs")
@RequiredArgsConstructor
@Validated
@Slf4j
public class HrPolicyPackController {

    private final HrPolicyPackCommandService commandService;
    private final HrPolicyPackService policyPackService;
    private final HrPolicyPackResolver policyPackResolver;
    private final HrCountryPackMappingService countryPackMappingService;

    @PostMapping
    @InternalEndpoint(description = "Create HR policy pack draft", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrPolicyPackResponse>> createDraft(
        @Valid @RequestBody CreateHrPolicyPackRequest request) {
        log.info("Creating HR policy pack draft: packCode={}, country={}", request.packCode(), request.countryCode());
        HrPolicyPackResponse response = commandService.createDraft(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{packCode}/draft")
    @InternalEndpoint(description = "Update HR policy pack draft", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrPolicyPackResponse>> updateDraft(
        @PathVariable String packCode,
        @Valid @RequestBody UpdateHrPolicyPackRequest request) {
        log.info("Updating HR policy pack draft: packCode={}", packCode);
        HrPolicyPackResponse response = commandService.updateDraft(packCode, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{packCode}/validate")
    @InternalEndpoint(description = "Validate HR policy pack payload", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<ValidateHrPolicyPackResponse>> validateDraftPayload(
        @PathVariable String packCode,
        @Valid @RequestBody ValidateHrPolicyPackRequest request) {
        log.debug("Validating HR policy pack payload: packCode={}", packCode);
        ValidateHrPolicyPackResponse response = commandService.validatePayload(request.payload());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{packCode}/publish")
    @InternalEndpoint(description = "Publish HR policy pack version", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrPolicyPackResponse>> publish(
        @PathVariable String packCode,
        @Valid @RequestBody PublishHrPolicyPackRequest request) {
        log.info("Publishing HR policy pack: packCode={}, effectiveFrom={}", packCode, request.effectiveFrom());
        HrPolicyPackResponse response = commandService.publish(packCode, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{packCode}/retire")
    @InternalEndpoint(description = "Retire HR policy pack version", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrPolicyPackResponse>> retire(
        @PathVariable String packCode,
        @Valid @RequestBody RetireHrPolicyPackRequest request) {
        log.info("Retiring HR policy pack: packCode={}", packCode);
        HrPolicyPackResponse response = commandService.retire(packCode, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @InternalEndpoint(description = "List HR policy packs", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<List<HrPolicyPackResponse>>> list(
        @RequestParam(required = false) String countryCode,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) HrPolicyPackStatus status) {
        HrPolicyPackStatus resolvedStatus = status;
        String normalizedCountry = countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : null;
        String normalizedRegion = regionCode != null ? regionCode.toUpperCase(Locale.ROOT) : null;
        List<HrPolicyPackResponse> packs = HrPolicyPackMapper.toResponseList(
            policyPackService.listPacks(TenantContext.getCurrentTenantId(), normalizedCountry, normalizedRegion, resolvedStatus)
        );
        return ResponseEntity.ok(ApiResponse.success(packs));
    }

    @GetMapping("/{packCode}/history")
    @InternalEndpoint(description = "Get HR policy pack history", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<List<HrPolicyPackResponse>>> history(@PathVariable String packCode) {
        List<HrPolicyPackResponse> history = HrPolicyPackMapper.toResponseList(
            policyPackService.getHistory(TenantContext.getCurrentTenantId(), packCode.toUpperCase(Locale.ROOT))
        );
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{packCode}/lineage")
    @InternalEndpoint(description = "Inspect HR policy pack lineage", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrPolicyPackLineageResponse>> lineage(
        @PathVariable String packCode,
        @RequestParam(required = false) Integer packVersion) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String normalizedCode = packCode.toUpperCase(Locale.ROOT);

        var pack = (packVersion != null
            ? policyPackService.findByPackCodeAndVersion(tenantId, normalizedCode, packVersion)
            : policyPackService.findActiveByPackCode(tenantId, normalizedCode)
                .or(() -> policyPackService.findLatestByPackCode(tenantId, normalizedCode)))
            .orElse(null);

        if (pack == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("POLICY_PACK_NOT_FOUND",
                    "No policy pack found for code %s (version %s)".formatted(normalizedCode,
                        packVersion != null ? packVersion : "latest")));
        }

        var resolved = policyPackResolver.resolve(tenantId, pack);
        HrPolicyPackLineageResponse response = new HrPolicyPackLineageResponse(
            pack.getPackCode(),
            pack.getPackVersion(),
            pack.getCountryCode(),
            pack.getRegionCode(),
            pack.getParentPackCode(),
            pack.getInheritanceMode() != null ? HrInheritanceModeDto.valueOf(pack.getInheritanceMode().name()) : null,
            resolved.lineageCodes(),
            resolved.resolvedPayload()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/mappings")
    @InternalEndpoint(description = "Assign country to policy pack", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<HrCountryPackMappingResponse>> assignCountryPack(
        @Valid @RequestBody AssignCountryPackRequest request) {
        var mapping = countryPackMappingService.assign(request.countryCode(), request.packCode());
        HrCountryPackMappingResponse response = new HrCountryPackMappingResponse(
            mapping.getId(),
            mapping.getCountryCode(),
            mapping.getPackCode()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/mappings")
    @InternalEndpoint(description = "List country-pack mappings", calledBy = {"hr-admin-ui"})
    public ResponseEntity<ApiResponse<List<HrCountryPackMappingResponse>>> listMappings() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<HrCountryPackMappingResponse> responses = countryPackMappingService.listMappings(tenantId).stream()
            .map(mapping -> new HrCountryPackMappingResponse(mapping.getId(), mapping.getCountryCode(), mapping.getPackCode()))
            .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}

