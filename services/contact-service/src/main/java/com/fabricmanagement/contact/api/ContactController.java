package com.fabricmanagement.contact.api;

import com.fabricmanagement.contact.api.dto.request.*;
import com.fabricmanagement.contact.api.dto.response.*;
import com.fabricmanagement.contact.application.service.ContactService;
import com.fabricmanagement.contact.application.service.AddressService;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.security.annotation.InternalEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Slf4j
public class ContactController {
    
    private final ContactService contactService;
    private final AddressService addressService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> listAllContacts() {
        log.debug("Listing all contacts (ADMIN operation)");
        
        List<ContactResponse> contacts = contactService.listAllContacts();
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
    
    @InternalEndpoint(
        description = "Create contact during tenant onboarding or by authenticated user",
        calledBy = {"user-service"},
        critical = true
    )
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(
            @Valid @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating contact for owner: {} (type: {})", request.getOwnerId(), request.getOwnerType());
        
        if (!hasAccess(ctx, request.getOwnerId())) {
            return forbiddenResponse();
        }
        
        ContactResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, ServiceConstants.MSG_CONTACT_CREATED));
    }
    
    @InternalEndpoint(
        description = "Get contacts by owner - used in profile composites",
        calledBy = {"user-service", "company-service"},
        critical = false
    )
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(
            @PathVariable UUID ownerId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting contacts for owner: {}", ownerId);
        
        if (!hasAccess(ctx, ownerId.toString())) {
            return forbiddenResponse();
        }
        
        List<ContactResponse> contacts = contactService.getContactsByOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
    
    @InternalEndpoint(
        description = "Get verified contacts only - used for notification targeting",
        calledBy = {"user-service", "notification-service"},
        critical = false
    )
    @GetMapping("/owner/{ownerId}/verified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getVerifiedContacts(
            @PathVariable UUID ownerId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting verified contacts for owner: {}", ownerId);
        
        if (!hasAccess(ctx, ownerId.toString())) {
            return forbiddenResponse();
        }
        
        List<ContactResponse> contacts = contactService.getVerifiedContacts(ownerId);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }
    
    @GetMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> getContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting contact: {}", contactId);
        
        ContactResponse contact = contactService.getContact(contactId);
        
        if (!hasAccess(ctx, contact.getOwnerId())) {
            return forbiddenResponse();
        }
        
        return ResponseEntity.ok(ApiResponse.success(contact));
    }
    
    @PutMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating contact: {}", contactId);
        
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!hasAccess(ctx, existingContact.getOwnerId())) {
            return forbiddenResponse();
        }
        
        contactService.updateContact(contactId, request);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_CONTACT_UPDATED));
    }
    
    @InternalEndpoint(
        description = "Delete contact - used when deleting user with cleanup",
        calledBy = {"user-service"},
        critical = false
    )
    @DeleteMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting contact: {}", contactId);
        
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!hasAccess(ctx, existingContact.getOwnerId())) {
            return forbiddenResponse();
        }
        
        contactService.deleteContact(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_CONTACT_DELETED));
    }
    
    @PutMapping("/{contactId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setPrimaryContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Setting contact as primary: {}", contactId);
        
        ContactResponse existingContact = contactService.getContact(contactId);
        if (!hasAccess(ctx, existingContact.getOwnerId())) {
            return forbiddenResponse();
        }
        
        contactService.setPrimaryContact(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_CONTACT_SET_PRIMARY));
    }
    
    @InternalEndpoint(
        description = "Send verification code during tenant onboarding",
        calledBy = {"user-service"},
        critical = true
    )
    @PostMapping("/{contactId}/send-verification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Sending verification code to contact: {}", contactId);

        ContactResponse existingContact = contactService.getContact(contactId);
        if (!hasAccess(ctx, existingContact.getOwnerId())) {
            return forbiddenResponse();
        }

        contactService.sendVerificationCode(contactId);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_VERIFICATION_CODE_SENT));
    }

    @InternalEndpoint(
        description = "Verify contact with code - used by orchestration flows",
        calledBy = {"user-service"},
        critical = true
    )
    @PutMapping("/{contactId}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyContact(
            @PathVariable UUID contactId,
            @RequestParam String code) {
        
        log.info("Verifying contact: {}", contactId);
        
        // Security: contactId + code combination is sufficient
        // - code is 6-digit random (expires in 15 minutes)
        // - brute force protected (max 5 attempts)
        // - no authentication needed (user hasn't set password yet)
        
        contactService.verifyContact(contactId, code);
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_CONTACT_VERIFIED));
    }
    
    @PostMapping("/public/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerificationCode(
            @RequestParam String contactValue) {
        
        log.info("Resending verification code for contact: {}", contactValue);
        
        // Security: 
        // - Rate limited by Gateway (3 requests/15min per contact)
        // - Only sends if contact exists and unverified
        // - Code expires in 15 minutes
        
        Optional<ContactResponse> optionalContact = contactService.findByContactValue(contactValue);
        
        if (optionalContact.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Contact not found in system"));
        }
        
        ContactResponse contact = optionalContact.get();
        
        if (contact.isVerified()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Contact is already verified"));
        }
        
        contactService.sendVerificationCode(contact.getId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_VERIFICATION_CODE_SENT));
    }
    
    @InternalEndpoint(
        description = "Get primary contact - used by other services",
        calledBy = {"user-service", "company-service"},
        critical = false
    )
    @GetMapping("/owner/{ownerId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ContactResponse>> getPrimaryContact(
            @PathVariable UUID ownerId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting primary contact for owner: {}", ownerId);
        
        if (!hasAccess(ctx, ownerId.toString())) {
            return forbiddenResponse();
        }
        
        ContactResponse contact = contactService.getPrimaryContact(ownerId);
        return ResponseEntity.ok(ApiResponse.success(contact));
    }
    
    @InternalEndpoint(
        description = "Check if email/phone is available - used by auth validation",
        calledBy = {"user-service"},
        critical = true
    )
    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponse<Boolean>> checkAvailability(@RequestParam String contactValue) {
        log.debug("Checking availability for contact value: {}", contactValue);

        boolean available = contactService.checkAvailability(contactValue);
        return ResponseEntity.ok(ApiResponse.success(available));
    }
    
    @InternalEndpoint(
        description = "Check email domain uniqueness across all tenants",
        calledBy = {"user-service"},
        critical = true
    )
    @GetMapping("/check-domain")
    public ResponseEntity<ApiResponse<List<UUID>>> checkEmailDomain(@RequestParam String domain) {
        log.debug("Checking email domain: @{}", domain);

        List<UUID> ownerIds = contactService.checkEmailDomain(domain);
        return ResponseEntity.ok(ApiResponse.success(ownerIds));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> searchContacts(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String contactType,
            @AuthenticationPrincipal SecurityContext ctx) {

        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);

        if (!hasAccess(ctx, ownerId.toString())) {
            return forbiddenResponse();
        }

        List<ContactResponse> contacts = contactService.searchContacts(ownerId, contactType);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @InternalEndpoint(
        description = "Find contact by email/phone - used by auth flows",
        calledBy = {"user-service"},
        critical = true
    )
    @GetMapping("/find-by-value")
    public ResponseEntity<ApiResponse<ContactResponse>> findByContactValue(
            @RequestParam String contactValue) {

        log.debug("Finding contact by value: {}", contactValue);

        Optional<ContactResponse> contact = contactService.findByContactValue(contactValue);
        return contact
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(
                            ServiceConstants.MSG_CONTACT_NOT_FOUND, 
                            ServiceConstants.ERROR_CODE_CONTACT_NOT_FOUND)));
    }
    
    @InternalEndpoint(
        description = "Batch fetch contacts for multiple owners - N+1 query optimization",
        calledBy = {"user-service", "company-service"},
        critical = false
    )
    @PostMapping("/batch/by-owners")
    public ResponseEntity<ApiResponse<java.util.Map<String, List<ContactResponse>>>> getContactsByOwnersBatch(
            @RequestBody List<UUID> ownerIds) {
        
        log.info("Batch fetching contacts for {} owners", ownerIds != null ? ownerIds.size() : 0);
        
        if (ownerIds == null || ownerIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(java.util.Collections.emptyMap()));
        }
        
        java.util.Map<UUID, List<ContactResponse>> contactsMap = 
                contactService.getContactsByOwnersBatch(ownerIds);
        
        java.util.Map<String, List<ContactResponse>> stringKeyMap = contactsMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        java.util.Map.Entry::getValue
                ));
        
        return ResponseEntity.ok(ApiResponse.success(stringKeyMap, 
                ServiceConstants.MSG_BATCH_CONTACTS_RETRIEVED + " for " + ownerIds.size() + " owners"));
    }

    // =============================================================================
    // ADDRESS ENDPOINTS
    // =============================================================================
    
    @InternalEndpoint(
        description = "Create address - used during company/user creation flows",
        calledBy = {"user-service", "company-service"},
        critical = true
    )
    @PostMapping("/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Creating address for owner: {} ({})", request.getOwnerId(), request.getOwnerType());
        
        if (!hasAccess(ctx, request.getOwnerId())) {
            return forbiddenResponse();
        }
        
        AddressResponse response = addressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Address created successfully"));
    }
    
    @InternalEndpoint(
        description = "Get addresses by owner - used in profile/dashboard composites",
        calledBy = {"user-service", "company-service"},
        critical = false
    )
    @GetMapping("/addresses/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddressesByOwner(
            @PathVariable UUID ownerId,
            @RequestParam String ownerType,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting addresses for owner: {} ({})", ownerId, ownerType);
        
        if (!hasAccess(ctx, ownerId.toString())) {
            return forbiddenResponse();
        }
        
        List<AddressResponse> addresses = addressService.getAddressesByOwner(ownerId, ownerType);
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }
    
    @GetMapping("/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Getting address: {}", addressId);
        
        AddressResponse address = addressService.getAddress(addressId);
        
        if (!hasAccess(ctx, address.getOwnerId())) {
            return forbiddenResponse();
        }
        
        return ResponseEntity.ok(ApiResponse.success(address));
    }
    
    @PutMapping("/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Updating address: {}", addressId);
        
        // Check access to existing address
        AddressResponse existing = addressService.getAddress(addressId);
        if (!hasAccess(ctx, existing.getOwnerId())) {
            return forbiddenResponse();
        }
        
        AddressResponse response = addressService.updateAddress(addressId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Address updated successfully"));
    }
    
    @InternalEndpoint(
        description = "Delete address - used when deleting user/company with cleanup",
        calledBy = {"user-service", "company-service"},
        critical = false
    )
    @DeleteMapping("/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Deleting address: {}", addressId);
        
        // Check access
        AddressResponse existing = addressService.getAddress(addressId);
        if (!hasAccess(ctx, existing.getOwnerId())) {
            return forbiddenResponse();
        }
        
        addressService.deleteAddress(addressId);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted successfully"));
    }
    
    @PutMapping("/addresses/{addressId}/set-primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setAddressAsPrimary(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.info("Setting address as primary: {}", addressId);
        
        // Check access
        AddressResponse existing = addressService.getAddress(addressId);
        if (!hasAccess(ctx, existing.getOwnerId())) {
            return forbiddenResponse();
        }
        
        addressService.setAsPrimary(addressId);
        return ResponseEntity.ok(ApiResponse.success(null, "Address set as primary"));
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================
    
    private boolean hasAccess(SecurityContext ctx, String ownerId) {
        // Allow internal service calls (service-to-service communication)
        if (SecurityConstants.INTERNAL_SERVICE_PRINCIPAL.equals(ctx.getUserId())) {
            return true;
        }

        boolean isAdmin = ctx.hasRole(SecurityConstants.ROLE_SUPER_ADMIN) ||
                         ctx.hasRole(SecurityConstants.ROLE_ADMIN) ||
                         ctx.hasRole(SecurityConstants.ROLE_COMPANY_MANAGER);
        boolean isOwner = ctx.getUserId().equals(ownerId);
        return isAdmin || isOwner;
    }

    private <T> ResponseEntity<ApiResponse<T>> forbiddenResponse() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(SecurityConstants.MSG_INSUFFICIENT_PERMISSIONS, 
                        SecurityConstants.ERROR_CODE_FORBIDDEN));
    }
}
