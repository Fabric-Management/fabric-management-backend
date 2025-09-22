package com.fabricmanagement.contact.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.application.service.ContactApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "Contact management endpoints")
public class ContactController {
    private final ContactApplicationService contactService;

    @PostMapping
    @Operation(summary = "Create contact")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> createContact(
            @Valid @RequestBody CreateContactRequest request) {
        ContactDetailResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Contact created successfully"));
    }

    @GetMapping("/{contactId}")
    @Operation(summary = "Get contact by ID")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> getContactById(@PathVariable UUID contactId) {
        ContactDetailResponse response = contactService.getContactById(contactId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{contactId}")
    @Operation(summary = "Update contact")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> updateContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest request) {
        ContactDetailResponse response = contactService.updateContact(contactId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Contact updated successfully"));
    }

    @DeleteMapping("/{contactId}")
    @Operation(summary = "Delete contact")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID contactId) {
        contactService.deleteContact(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
    }

    @GetMapping
    @Operation(summary = "List contacts")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> listContacts() {
        List<ContactResponse> contacts = contactService.listContacts();
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
}
