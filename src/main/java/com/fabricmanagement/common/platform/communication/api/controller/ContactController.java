package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.dto.ContactDto;
import com.fabricmanagement.common.platform.communication.dto.CreateContactRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactDto>> createContact(@Valid @RequestBody CreateContactRequest request) {
        log.info("Creating contact: type={}", request.getContactType());

        Contact contact = contactService.createContact(
            request.getContactValue(),
            request.getContactType(),
            request.getLabel(),
            request.getIsPersonal(),
            request.getParentContactId()
        );

        return ResponseEntity.ok(ApiResponse.success(ContactDto.from(contact), "Contact created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactDto>> getContact(@PathVariable UUID id) {
        log.debug("Getting contact: id={}", id);

        Contact contact = contactService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        return ResponseEntity.ok(ApiResponse.success(ContactDto.from(contact)));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<ContactDto>>> getContactsByType(@PathVariable String type) {
        log.debug("Getting contacts by type: type={}", type);

        ContactType contactType = ContactType.valueOf(type.toUpperCase());
        List<ContactDto> contacts = contactService.findByType(contactType)
            .stream()
            .map(ContactDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<ContactDto>> verifyContact(@PathVariable UUID id) {
        log.info("Verifying contact: id={}", id);

        Contact contact = contactService.verifyContact(id);

        return ResponseEntity.ok(ApiResponse.success(ContactDto.from(contact), "Contact verified successfully"));
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<ApiResponse<ContactDto>> setAsPrimary(@PathVariable UUID id) {
        log.info("Setting contact as primary: id={}", id);

        Contact contact = contactService.setAsPrimary(id);

        return ResponseEntity.ok(ApiResponse.success(ContactDto.from(contact), "Contact set as primary"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID id) {
        log.info("Deleting contact: id={}", id);

        contactService.deleteContact(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
    }
}

