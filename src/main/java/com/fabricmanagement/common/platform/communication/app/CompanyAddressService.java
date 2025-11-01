package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.CompanyAddress;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.CompanyAddressRepository;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Address Service - Business logic for company-address assignments.
 *
 * <p>Handles Many-to-Many relationship between Company and Address.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Assign addresses to companies</li>
 *   <li>Remove company-address assignments</li>
 *   <li>Manage primary address</li>
 *   <li>Manage headquarters designation</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyAddressService {

    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;
    private final CompanyAddressRepository companyAddressRepository;

    @Transactional(readOnly = true)
    public List<CompanyAddress> getCompanyAddresses(UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding company addresses: tenantId={}, companyId={}", tenantId, companyId);

        return companyAddressRepository.findByTenantIdAndCompanyId(tenantId, companyId);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyAddress> getPrimaryAddress(UUID companyId) {
        log.trace("Finding primary address: companyId={}", companyId);
        return companyAddressRepository.findPrimaryByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyAddress> getHeadquarters(UUID companyId) {
        log.trace("Finding headquarters: companyId={}", companyId);
        return companyAddressRepository.findHeadquartersByCompanyId(companyId);
    }

    @Transactional
    public CompanyAddress assignAddress(UUID companyId, UUID addressId, Boolean isPrimary, Boolean isHeadquarters) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Assigning address to company: tenantId={}, companyId={}, addressId={}, isPrimary={}, isHQ={}",
            tenantId, companyId, addressId, isPrimary, isHeadquarters);

        // Validate company exists
        companyRepository.findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        var address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (!address.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Address does not belong to current tenant");
        }

        if (companyAddressRepository.findByCompanyIdAndAddressId(companyId, addressId).isPresent()) {
            throw new IllegalArgumentException("Address is already assigned to this company");
        }

        // Set primary: remove primary flag from other addresses
        if (Boolean.TRUE.equals(isPrimary)) {
            companyAddressRepository.findPrimaryByCompanyId(companyId)
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    companyAddressRepository.save(existing);
                });
        }

        // Set headquarters: remove headquarters flag from other addresses
        if (Boolean.TRUE.equals(isHeadquarters)) {
            companyAddressRepository.findHeadquartersByCompanyId(companyId)
                .ifPresent(existing -> {
                    existing.setIsHeadquarters(false);
                    companyAddressRepository.save(existing);
                });
        }

        CompanyAddress companyAddress = CompanyAddress.builder()
            .companyId(companyId)
            .addressId(addressId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .isHeadquarters(isHeadquarters != null ? isHeadquarters : false)
            .build();

        return companyAddressRepository.save(companyAddress);
    }

    @Transactional
    public void removeAddress(UUID companyId, UUID addressId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing address from company: tenantId={}, companyId={}, addressId={}", 
            tenantId, companyId, addressId);

        CompanyAddress companyAddress = companyAddressRepository.findByCompanyIdAndAddressId(companyId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));

        companyAddressRepository.delete(companyAddress);
    }

    @Transactional
    public CompanyAddress setAsPrimary(UUID companyId, UUID addressId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting primary address: tenantId={}, companyId={}, addressId={}", tenantId, companyId, addressId);

        CompanyAddress companyAddress = companyAddressRepository.findByCompanyIdAndAddressId(companyId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));

        // Remove primary from others
        companyAddressRepository.findPrimaryByCompanyId(companyId)
            .ifPresent(existing -> {
                if (!existing.getAddressId().equals(addressId)) {
                    existing.setIsPrimary(false);
                    companyAddressRepository.save(existing);
                }
            });

        companyAddress.setAsPrimary();
        return companyAddressRepository.save(companyAddress);
    }

    @Transactional
    public CompanyAddress setAsHeadquarters(UUID companyId, UUID addressId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting headquarters: tenantId={}, companyId={}, addressId={}", tenantId, companyId, addressId);

        CompanyAddress companyAddress = companyAddressRepository.findByCompanyIdAndAddressId(companyId, addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address assignment not found"));

        // Remove headquarters from others
        companyAddressRepository.findHeadquartersByCompanyId(companyId)
            .ifPresent(existing -> {
                if (!existing.getAddressId().equals(addressId)) {
                    existing.setIsHeadquarters(false);
                    companyAddressRepository.save(existing);
                }
            });

        companyAddress.setAsHeadquarters();
        return companyAddressRepository.save(companyAddress);
    }
}

