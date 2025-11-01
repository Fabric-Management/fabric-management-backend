package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.CompanyContactService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.CompanyContact;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.dto.AssignContactRequest;
import com.fabricmanagement.common.platform.communication.dto.CompanyContactDto;
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
@RequestMapping("/api/common/companies/{companyId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class CompanyContactController {

    private final CompanyContactService companyContactService;
    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyContactDto>>> getCompanyContacts(@PathVariable UUID companyId) {
        log.debug("Getting company contacts: companyId={}", companyId);

        List<CompanyContactDto> contacts = companyContactService.getCompanyContacts(companyId)
            .stream()
            .map(CompanyContactDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<CompanyContactDto>> getDefaultContact(@PathVariable UUID companyId) {
        log.debug("Getting default contact: companyId={}", companyId);

        CompanyContact defaultContact = companyContactService.getDefaultContact(companyId)
            .orElseThrow(() -> new IllegalArgumentException("No default contact found"));

        return ResponseEntity.ok(ApiResponse.success(CompanyContactDto.from(defaultContact)));
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<ApiResponse<List<CompanyContactDto>>> getDepartmentContacts(
            @PathVariable UUID companyId,
            @PathVariable String department) {
        log.debug("Getting department contacts: companyId={}, department={}", companyId, department);

        List<CompanyContactDto> contacts = companyContactService.getDepartmentContacts(companyId, department)
            .stream()
            .map(CompanyContactDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyContactDto>> assignContact(
            @PathVariable UUID companyId,
            @Valid @RequestBody AssignContactRequest request) {
        log.info("Assigning contact to company: companyId={}, contactId={}, isDefault={}, department={}",
            companyId, request.getContactId(), request.getIsDefault(), request.getDepartment());

        CompanyContact companyContact = companyContactService.assignContact(
            companyId,
            request.getContactId(),
            request.getIsDefault(),
            request.getDepartment()
        );

        return ResponseEntity.ok(ApiResponse.success(
            CompanyContactDto.from(companyContact),
            "Contact assigned successfully"
        ));
    }

    @PostMapping("/create-and-assign")
    public ResponseEntity<ApiResponse<CompanyContactDto>> createAndAssignContact(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateContactRequest createRequest,
            @RequestParam(defaultValue = "false") Boolean isDefault,
            @RequestParam(required = false) String department) {
        log.info("Creating and assigning contact to company: companyId={}, type={}", 
            companyId, createRequest.getContactType());

        // Create contact first
        Contact contact = contactService.createContact(
            createRequest.getContactValue(),
            createRequest.getContactType(),
            createRequest.getLabel(),
            createRequest.getIsPersonal(),
            createRequest.getParentContactId()
        );

        // Assign to company
        CompanyContact companyContact = companyContactService.assignContact(
            companyId,
            contact.getId(),
            isDefault,
            department
        );

        return ResponseEntity.ok(ApiResponse.success(
            CompanyContactDto.from(companyContact),
            "Contact created and assigned successfully"
        ));
    }

    @PutMapping("/{contactId}/default")
    public ResponseEntity<ApiResponse<CompanyContactDto>> setAsDefault(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        log.info("Setting default contact: companyId={}, contactId={}", companyId, contactId);

        CompanyContact companyContact = companyContactService.setAsDefault(companyId, contactId);

        return ResponseEntity.ok(ApiResponse.success(
            CompanyContactDto.from(companyContact),
            "Default contact set successfully"
        ));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<ApiResponse<Void>> removeContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        log.info("Removing contact from company: companyId={}, contactId={}", companyId, contactId);

        companyContactService.removeContact(companyId, contactId);

        return ResponseEntity.ok(ApiResponse.success(null, "Contact removed successfully"));
    }
}

