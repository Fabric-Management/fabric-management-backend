package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressContactRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Address Contact Service - Business logic for address-contact assignments.
 *
 * <p>Handles Many-to-Many relationship between Address and Contact.</p>
 * <p>Supports address-specific contacts for both Company and User addresses.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Assign contacts to addresses</li>
 *   <li>Remove address-contact assignments</li>
 *   <li>Manage primary contact per address</li>
 *   <li>Query contacts for addresses</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressContactService {

    private final AddressRepository addressRepository;
    private final ContactRepository contactRepository;
    private final AddressContactRepository addressContactRepository;

    @Transactional(readOnly = true)
    public List<AddressContact> getAddressContacts(UUID addressId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding address contacts: tenantId={}, addressId={}", tenantId, addressId);

        return addressContactRepository.findByTenantIdAndAddressId(tenantId, addressId);
    }

    @Transactional(readOnly = true)
    public Optional<AddressContact> getPrimaryContact(UUID addressId) {
        log.trace("Finding primary contact: addressId={}", addressId);
        return addressContactRepository.findPrimaryByAddressId(addressId);
    }

    @Transactional(readOnly = true)
    public List<AddressContact> getContactsForAddresses(List<UUID> addressIds) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Batch loading contacts for addresses: tenantId={}, count={}", tenantId, addressIds.size());
        return addressContactRepository.findByTenantIdAndAddressIdIn(tenantId, addressIds);
    }

    @Transactional
    public AddressContact assignContact(UUID addressId, UUID contactId, Boolean isPrimary, String label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Assigning contact to address: tenantId={}, addressId={}, contactId={}, isPrimary={}, label={}",
            tenantId, addressId, contactId, isPrimary, label);

        // Validate address exists
        var address = addressRepository.findById(addressId)
            .orElseThrow(() -> new DomainException("Address not found"));

        if (!address.getTenantId().equals(tenantId)) {
            throw new DomainException("Address does not belong to current tenant");
        }

        // Validate contact exists
        var contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new DomainException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new DomainException("Contact does not belong to current tenant");
        }

        // Check if already assigned
        if (addressContactRepository.findByAddressIdAndContactId(addressId, contactId).isPresent()) {
            throw new DomainException("Contact is already assigned to this address");
        }

        // Set primary: remove primary flag from other contacts for this address
        if (Boolean.TRUE.equals(isPrimary)) {
            addressContactRepository.findPrimaryByAddressId(addressId)
                .ifPresent(existing -> {
                    existing.removePrimary();
                    addressContactRepository.save(existing);
                });
        }

        AddressContact addressContact = AddressContact.builder()
            .addressId(addressId)
            .contactId(contactId)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .label(label)
            .build();

        return addressContactRepository.save(addressContact);
    }

    @Transactional
    public void removeContact(UUID addressId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing contact from address: tenantId={}, addressId={}, contactId={}", 
            tenantId, addressId, contactId);

        AddressContact addressContact = addressContactRepository.findByAddressIdAndContactId(addressId, contactId)
            .orElseThrow(() -> new DomainException("Contact assignment not found"));

        if (!addressContact.getTenantId().equals(tenantId)) {
            throw new DomainException("Address contact does not belong to current tenant");
        }

        addressContactRepository.delete(addressContact);
    }

    @Transactional
    public AddressContact setAsPrimary(UUID addressId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting primary contact: tenantId={}, addressId={}, contactId={}", tenantId, addressId, contactId);

        AddressContact addressContact = addressContactRepository.findByAddressIdAndContactId(addressId, contactId)
            .orElseThrow(() -> new DomainException("Contact assignment not found"));

        if (!addressContact.getTenantId().equals(tenantId)) {
            throw new DomainException("Address contact does not belong to current tenant");
        }

        // Remove primary from other contacts for this address
        addressContactRepository.findPrimaryByAddressId(addressId)
            .ifPresent(existing -> {
                if (!existing.getContactId().equals(contactId)) {
                    existing.removePrimary();
                    addressContactRepository.save(existing);
                }
            });

        addressContact.setAsPrimary();
        return addressContactRepository.save(addressContact);
    }

    @Transactional
    public AddressContact updateLabel(UUID addressId, UUID contactId, String label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Updating contact label: tenantId={}, addressId={}, contactId={}, label={}", 
            tenantId, addressId, contactId, label);

        AddressContact addressContact = addressContactRepository.findByAddressIdAndContactId(addressId, contactId)
            .orElseThrow(() -> new DomainException("Contact assignment not found"));

        if (!addressContact.getTenantId().equals(tenantId)) {
            throw new DomainException("Address contact does not belong to current tenant");
        }

        addressContact.setLabel(label);
        return addressContactRepository.save(addressContact);
    }
}

