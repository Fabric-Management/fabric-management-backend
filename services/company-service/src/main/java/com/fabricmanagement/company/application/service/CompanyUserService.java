package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.CompanyNotFoundException;
import com.fabricmanagement.company.domain.exception.MaxUsersLimitException;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.client.UserServiceClient;
import com.fabricmanagement.company.infrastructure.client.dto.UserDto;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final UserServiceClient userServiceClient;
    
    /**
     * Adds a user to a company
     */
    @Transactional
    public void addUserToCompany(UUID companyId, UUID userId, UUID tenantId) {
        log.info("Adding user {} to company {}", userId, companyId);
        
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
        
        // Add user to company
        company.addUser();
        
        companyRepository.save(company);
        
        log.info("User {} added to company {} successfully", userId, companyId);
    }
    
    /**
     * Removes a user from a company
     */
    @Transactional
    public void removeUserFromCompany(UUID companyId, UUID userId, UUID tenantId) {
        log.info("Removing user {} from company {}", userId, companyId);
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        // Remove user from company
        company.removeUser();
        
        companyRepository.save(company);
        
        log.info("User {} removed from company {} successfully", userId, companyId);
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
    
    /**
     * Syncs user count from User Service
     */
    @Transactional
    public void syncUserCount(UUID companyId, UUID tenantId) {
        log.info("Syncing user count for company: {}", companyId);
        
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(companyId, tenantId));
        
        int actualUserCount = userServiceClient.getUserCountForCompany(companyId);
        int currentCount = company.getCurrentUsers();
        
        // Adjust if there's a mismatch
        if (actualUserCount != currentCount) {
            log.warn("User count mismatch for company {}. Expected: {}, Actual: {}", 
                companyId, currentCount, actualUserCount);
            
            // Update the count (this is a simplified approach)
            while (company.getCurrentUsers() < actualUserCount) {
                company.addUser();
            }
            while (company.getCurrentUsers() > actualUserCount) {
                company.removeUser();
            }
            
            companyRepository.save(company);
            log.info("User count synced for company {}: {}", companyId, actualUserCount);
        }
    }
}

