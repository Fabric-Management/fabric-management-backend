package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.communication.api.facade.ContactFacade;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.ContactDto;
import com.fabricmanagement.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.platform.communication.dto.WhatsAppCapabilityDto;
import com.fabricmanagement.platform.communication.infra.client.WhatsAppClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/common/contacts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contact", description = "Contact operations")
public class ContactController {

  private final ContactFacade facade;
  private final WhatsAppClient whatsAppClient;

  @PostMapping
  public ResponseEntity<ApiResponse<ContactDto>> createContact(
      @Valid @RequestBody CreateContactRequest request) {
    log.info("Creating contact: type={}", request.getContactType());
    return ResponseEntity.ok(
        ApiResponse.success(facade.createContact(request), "Contact created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ContactDto>> getContact(@PathVariable UUID id) {
    log.debug("Getting contact: id={}", id);
    return ResponseEntity.ok(ApiResponse.success(facade.getContact(id)));
  }

  @GetMapping("/search")
  public ResponseEntity<ApiResponse<List<ContactDto>>> searchContacts(
      @RequestParam @NotBlank(message = "Query is required") String query) {
    log.debug(
        "Searching contacts: query={}", query.length() > 2 ? query.substring(0, 2) + "***" : "***");

    if (query.length() < 2) {
      return ResponseEntity.ok(ApiResponse.success(List.of()));
    }
    return ResponseEntity.ok(ApiResponse.success(facade.searchContacts(query)));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<ContactDto>>> getContactsByType(
      @PathVariable String type) {
    log.debug("Getting contacts by type: type={}", type);
    ContactType contactType = ContactType.valueOf(type.toUpperCase());
    return ResponseEntity.ok(ApiResponse.success(facade.getContactsByType(contactType)));
  }

  @PutMapping("/{id}/verify")
  public ResponseEntity<ApiResponse<ContactDto>> verifyContact(@PathVariable UUID id) {
    log.info("Verifying contact: id={}", id);
    return ResponseEntity.ok(
        ApiResponse.success(facade.verifyContact(id), "Contact verified successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID id) {
    log.info("Deleting contact: id={}", id);
    facade.deleteContact(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
  }

  @GetMapping("/check-whatsapp")
  public ResponseEntity<ApiResponse<WhatsAppCapabilityDto>> checkWhatsAppCapability(
      @RequestParam @NotBlank(message = "Phone number is required") String phoneNumber) {
    log.debug("Checking WhatsApp capability: phone={}", PiiMaskingUtil.maskPhone(phoneNumber));
    boolean hasWhatsApp = whatsAppClient.phoneHasWhatsApp(phoneNumber);
    WhatsAppCapabilityDto dto =
        WhatsAppCapabilityDto.builder()
            .phoneNumber(phoneNumber)
            .hasWhatsApp(hasWhatsApp)
            .canReceiveMessages(hasWhatsApp)
            .build();
    return ResponseEntity.ok(ApiResponse.success(dto));
  }
}
