package com.fabricmanagement.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tradingpartner.app.PartnerLinkService;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerLinkDto;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/common/trading-partners/{customerId}/partner-links")
@RequiredArgsConstructor
@Tag(
    name = "Partner Links",
    description = "Companies that receive goods for a customer, linked with a role")
public class PartnerLinkController {

  private final PartnerLinkService partnerLinkService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List the companies linked to a customer")
  public ResponseEntity<ApiResponse<List<PartnerLinkDto>>> listLinks(
      @Parameter(description = "Customer partner ID") @PathVariable UUID customerId) {
    return ResponseEntity.ok(ApiResponse.success(partnerLinkService.list(customerId)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      summary = "Link an existing partner to a customer",
      description =
          "Links a tenant partner to the customer in a role. Rejects a link to the customer "
              + "itself (400) and a repeat of an active link (409).")
  public ResponseEntity<ApiResponse<PartnerLinkDto>> linkExisting(
      @Parameter(description = "Customer partner ID") @PathVariable UUID customerId,
      @Valid @RequestBody CreatePartnerLinkRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(partnerLinkService.link(customerId, request)));
  }

  @PostMapping("/quick")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      summary = "Find or create a delivery partner and link it to a customer",
      description =
          "Answers 201 when the company was linked. When similar tenant partners exist and "
              + "createAnyway is not true, answers 200 with created=false and the candidates; "
              + "nothing is written.")
  public ResponseEntity<ApiResponse<QuickCreatePartnerLinkResponse>> quickCreateAndLink(
      @Parameter(description = "Customer partner ID") @PathVariable UUID customerId,
      @Valid @RequestBody QuickCreatePartnerLinkRequest request) {
    QuickCreatePartnerLinkResponse response = partnerLinkService.quickCreate(customerId, request);
    HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(response));
  }

  @DeleteMapping("/{linkId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Remove a link (soft delete)")
  public ResponseEntity<ApiResponse<Void>> unlink(
      @Parameter(description = "Customer partner ID") @PathVariable UUID customerId,
      @Parameter(description = "Link ID") @PathVariable UUID linkId) {
    partnerLinkService.unlink(customerId, linkId);
    return ResponseEntity.ok(ApiResponse.success(null, "Partner link removed"));
  }
}
