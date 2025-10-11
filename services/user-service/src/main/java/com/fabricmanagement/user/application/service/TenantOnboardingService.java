package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.TenantRegistrationException;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.user.api.dto.request.TenantRegistrationRequest;
import com.fabricmanagement.user.api.dto.response.TenantOnboardingResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.CompanyServiceClient;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.client.dto.CreateCompanyDto;
import com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingService {
    
    private final CompanyServiceClient companyServiceClient;
    private final ContactServiceClient contactServiceClient;
    private final UserRepository userRepository;
    
    @Transactional
    public TenantOnboardingResponse registerTenant(TenantRegistrationRequest request) {
        log.info("Starting tenant registration for company: {}, email: {}", 
            request.getCompanyName(), request.getEmail());
        
        UUID companyId = null;
        UUID userId = null;
        
        try {
            validateEmailUniqueness(request.getEmail());
            
            companyId = createCompany(request);
            
            userId = createTenantAdminUser(request, companyId);
            
            UUID contactId = createEmailContact(request.getEmail(), userId);
            
            sendVerificationEmail(contactId);
            
            log.info("Tenant registration completed successfully. Company: {}, User: {}", 
                companyId, userId);
            
            return TenantOnboardingResponse.builder()
                    .companyId(companyId)
                    .userId(userId)
                    .email(request.getEmail())
                    .message("Registration successful")
                    .nextStep("Please check your email to verify your account")
                    .build();
                    
        } catch (Exception e) {
            log.error("Tenant registration failed. Rolling back. Company: {}, User: {}", 
                companyId, userId, e);
            
            performRollback(companyId, userId);
            
            throw new TenantRegistrationException("Tenant registration failed: " + e.getMessage());
        }
    }
    
    private void validateEmailUniqueness(String email) {
        ApiResponse<Boolean> availabilityResponse = contactServiceClient.checkAvailability(email);
        
        if (availabilityResponse.getData() != null && !availabilityResponse.getData()) {
            throw new TenantRegistrationException("Email already registered: " + email);
        }
    }
    
    private UUID createCompany(TenantRegistrationRequest request) {
        CreateCompanyDto companyDto = CreateCompanyDto.builder()
                .name(request.getCompanyName())
                .legalName(request.getLegalName())
                .taxId(request.getTaxId())
                .registrationNumber(request.getRegistrationNumber())
                .type(request.getCompanyType())
                .industry(request.getIndustry())
                .description(request.getDescription())
                .website(request.getWebsite())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .district(request.getDistrict())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .build();
        
        ApiResponse<UUID> response = companyServiceClient.createCompany(companyDto);
        
        if (response.getData() == null) {
            throw new TenantRegistrationException("Failed to create company");
        }
        
        return response.getData();
    }
    
    private UUID createTenantAdminUser(TenantRegistrationRequest request, UUID tenantId) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .displayName(request.getFirstName() + " " + request.getLastName())
                .role(SystemRole.TENANT_ADMIN)
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.SELF_REGISTRATION)
                .passwordHash(null)
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .deleted(false)
                .version(0L)
                .build();
        
        user = userRepository.save(user);
        
        return user.getId();
    }
    
    private UUID createEmailContact(String email, UUID userId) {
        CreateContactDto contactDto = CreateContactDto.builder()
                .ownerId(userId.toString())
                .ownerType("USER")
                .contactType("EMAIL")
                .contactValue(email)
                .isPrimary(true)
                .isVerified(false)
                .build();
        
        ApiResponse<ContactDto> response = contactServiceClient.createContact(contactDto);
        
        if (response.getData() == null) {
            throw new TenantRegistrationException("Failed to create email contact");
        }
        
        return response.getData().getId();
    }
    
    private void sendVerificationEmail(UUID contactId) {
        if (contactId == null) {
            log.warn("Cannot send verification email, contactId is null");
            return;
        }
        
        try {
            contactServiceClient.sendVerificationCode(contactId);
            log.info("Verification email sent to contact: {}", contactId);
        } catch (Exception e) {
            log.error("Failed to send verification email, but registration continues", e);
        }
    }
    
    private void performRollback(UUID companyId, UUID userId) {
        try {
            if (userId != null) {
                userRepository.deleteById(userId);
                log.info("Rolled back user creation: {}", userId);
            }
            
            if (companyId != null) {
                log.warn("Cannot rollback company creation via Feign. Manual cleanup may be required: {}", 
                    companyId);
            }
        } catch (Exception rollbackEx) {
            log.error("Rollback failed", rollbackEx);
        }
    }
}

