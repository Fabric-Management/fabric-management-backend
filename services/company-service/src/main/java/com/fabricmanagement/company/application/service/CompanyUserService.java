package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.application.dto.AddUserToCompanyRequest;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.MaxUsersLimitException;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.domain.valueobject.CompanyUser;
import com.fabricmanagement.company.infrastructure.client.UserServiceClient;
import com.fabricmanagement.company.infrastructure.client.dto.UserDto;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import com.fabricmanagement.company.infrastructure.repository.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company User Service
 * 
 * Manages user-company relationships via User Service integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyUserService {
    
    private final CompanyRepository companyRepository;
    private final CompanyUserRepository companyUserRepository;
    private final UserServiceClient userServiceClient;
    
    /**
     * Adds a user to a company
     */
    @Transactional
    public void addUserToCompany(UUID companyId, AddUserToCompanyRequest request, 
                                UUID tenantId, String addedBy) {
        UUID userId = request.getUserId();
        log.info("Adding user {} to company {} by user {}", userId, companyId, addedBy);
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Check if company can add more users
        if (!company.canAddUser()) {
            throw new MaxUsersLimitException(companyId, company.getMaxUsers());
        }
        
        // Verify user exists in User Service
        boolean userExists = userServiceClient.userExists(userId);
        if (!userExists) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        
        // Check if relationship already exists
        Optional<CompanyUser> existing = companyUserRepository
            .findByCompanyIdAndUserId(companyId, userId);
        
        if (existing.isPresent()) {
            if (existing.get().isActive()) {
                throw new IllegalArgumentException("User already in company: " + userId);
            } else {
                // Reactivate existing relationship
                existing.get().activate();
                existing.get().updateRole(request.getRole() != null ? request.getRole() : "USER");
                companyUserRepository.save(existing.get());
                log.info("User {} reactivated in company {}", userId, companyId);
                return;
            }
        }
        
        // Create new company-user relationship
        CompanyUser companyUser = CompanyUser.create(
            companyId, 
            userId, 
            request.getRole() != null ? request.getRole() : "USER"
        );
        companyUserRepository.save(companyUser);
        
        // Update company user count
        company.addUser();
        companyRepository.save(company);
        
        log.info("User {} added to company {} successfully", userId, companyId);
    }
    
    /**
     * Removes a user from a company
     */
    @Transactional
    public void removeUserFromCompany(UUID companyId, UUID userId, UUID tenantId, String removedBy) {
        log.info("Removing user {} from company {} by user {}", userId, companyId, removedBy);
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Find and deactivate company-user relationship
        CompanyUser companyUser = companyUserRepository
            .findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found in company: " + userId));
        
        companyUser.deactivate();
        companyUserRepository.save(companyUser);
        
        // Update company user count
        company.removeUser();
        companyRepository.save(company);
        
        log.info("User {} removed from company {} successfully", userId, companyId);
    }
    
    /**
     * Updates a user's role in a company
     */
    @Transactional
    public void updateUserRole(UUID companyId, UUID userId, String role, UUID tenantId, String updatedBy) {
        log.info("Updating role for user {} in company {} to {} by user {}", userId, companyId, role, updatedBy);
        
        // Validate company access
        companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Find and update company-user relationship
        CompanyUser companyUser = companyUserRepository
            .findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found in company: " + userId));
        
        if (!companyUser.isActive()) {
            throw new IllegalArgumentException("User is inactive in company: " + userId);
        }
        
        // Update role
        companyUser.updateRole(role);
        companyUserRepository.save(companyUser);
        
        log.info("Role updated successfully for user {} in company {}", userId, companyId);
    }
    
    /**
     * Gets users for a company
     */
    public List<UserDto> getCompanyUsers(UUID companyId, UUID tenantId) {
        log.debug("Getting users for company: {}", companyId);
        
        // Validate company access
        companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Get users from User Service
        return userServiceClient.getUsersByCompany(companyId);
    }
    
    /**
     * Gets user count for a company
     */
    public int getCompanyUserCount(UUID companyId, UUID tenantId) {
        log.debug("Getting user count for company: {}", companyId);
        
        // Validate company access
        companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Get count from User Service
        return userServiceClient.getUserCountForCompany(companyId);
    }
}
