package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.dto.ContactDto;
import com.fabricmanagement.common.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.common.platform.communication.dto.WhatsAppCapabilityDto;
import com.fabricmanagement.common.platform.communication.infra.client.WhatsAppClient;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

  private final ContactService contactService;
  private final WhatsAppClient whatsAppClient;

  @PostMapping
  public ResponseEntity<ApiResponse<ContactDto>> createContact(
      @Valid @RequestBody CreateContactRequest request) {
    log.info("Creating contact: type={}", request.getContactType());

    Contact contact =
        contactService.createContact(
            request.getContactValue(),
            request.getContactType(),
            request.getLabel(),
            request.getIsPersonal(),
            request.getParentContactId());

    return ResponseEntity.ok(
        ApiResponse.success(ContactDto.from(contact), "Contact created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ContactDto>> getContact(@PathVariable UUID id) {
    log.debug("Getting contact: id={}", id);

    Contact contact =
        contactService
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

    return ResponseEntity.ok(ApiResponse.success(ContactDto.from(contact)));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<ContactDto>>> getContactsByType(
      @PathVariable String type) {
    log.debug("Getting contacts by type: type={}", type);

    ContactType contactType = ContactType.valueOf(type.toUpperCase());
    List<ContactDto> contacts =
        contactService.findByType(contactType).stream()
            .map(ContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @PutMapping("/{id}/verify")
  public ResponseEntity<ApiResponse<ContactDto>> verifyContact(@PathVariable UUID id) {
    log.info("Verifying contact: id={}", id);

    Contact contact = contactService.verifyContact(id);

    return ResponseEntity.ok(
        ApiResponse.success(ContactDto.from(contact), "Contact verified successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID id) {
    log.info("Deleting contact: id={}", id);

    contactService.deleteContact(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
  }

  /**
   * Check if phone number has WhatsApp capability.
   *
   * <p>Uses WhatsApp Business API to verify if recipient can receive WhatsApp messages.
   *
   * <p><b>Purpose:</b> Enable frontend to show WhatsApp indicator and prioritize WhatsApp
   * verification.
   *
   * @param phoneNumber E.164 format phone number (e.g., +14155551234)
   * @return WhatsApp capability information
   */
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
