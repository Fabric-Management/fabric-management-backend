package com.fabricmanagement.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tradingpartner.app.PartnerContactService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerContactRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerContactDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/common/trading-partners/{partnerId}/contacts")
@RequiredArgsConstructor
@Tag(name = "Partner Contacts", description = "Lightweight contacts for trading partners")
public class PartnerContactController {

  private final PartnerContactService partnerContactService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List trading partner contacts")
  public ResponseEntity<ApiResponse<List<PartnerContactDto>>> listContacts(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Parameter(description = "Optional contact role filter") @RequestParam(required = false)
          PartnerContactRole role) {
    List<PartnerContactDto> contacts = partnerContactService.listContacts(partnerId, role);
    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a trading partner contact")
  public ResponseEntity<ApiResponse<PartnerContactDto>> createContact(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Valid @RequestBody CreatePartnerContactRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(partnerContactService.createContact(partnerId, request)));
  }
}
