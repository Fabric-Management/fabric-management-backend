package com.fabricmanagement.contact.infrastructure.messaging;

import com.fabricmanagement.contact.api.dto.request.CreateAddressRequest;
import com.fabricmanagement.contact.api.dto.request.CreateContactRequest;
import com.fabricmanagement.contact.application.service.AddressService;
import com.fabricmanagement.contact.application.service.ContactService;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.domain.valueobject.AddressType;
import com.fabricmanagement.shared.domain.event.tenant.TenantRegisteredEvent;
import static com.fabricmanagement.shared.infrastructure.constants.ServiceConstants.TOPIC_TENANT_EVENTS;
import static com.fabricmanagement.shared.infrastructure.constants.ServiceConstants.GROUP_CONTACT_SERVICE_TENANT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Event Listener - Tenant Registration Events
 * 
 * Listens to tenant-events topic and handles post-registration tasks.
 * 
 * Responsibilities:
 * - Create company address (from tenant registration data)
 * - Create admin phone contact (if provided)
 * - (future) Create default company contacts
 * 
 * Event-Driven Benefits:
 * ✅ Loose coupling - Contact Service doesn't need to be called directly
 * ✅ Async processing - Non-blocking tenant onboarding
 * ✅ Retry/DLQ - Automatic error handling via Kafka
 * ✅ Scalability - Independent scaling of services
 * 
 * @since 3.1.0 - Event-Driven Refactor (Oct 13, 2025)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEventListener {
    
    private final AddressService addressService;
    private final ContactService contactService;
    
    /**
     * Handle TenantRegisteredEvent
     * 
     * Creates:
     * 1. Company address (from registration data)
     * 2. Admin phone contact (if phone provided)
     * 
     * NOTE: This is idempotent - duplicate events won't create duplicate records
     * (Address/Contact services handle uniqueness)
     */
    @KafkaListener(
        topics = TOPIC_TENANT_EVENTS,
        groupId = GROUP_CONTACT_SERVICE_TENANT,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTenantRegistered(
            @Payload TenantRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("📩 Received TenantRegisteredEvent | Tenant: {} | Company: {} | Topic: {} | Partition: {} | Offset: {}",
                event.getTenantId(), event.getCompanyId(), topic, partition, offset);
        
        try {
            // Step 1: Create company address
            createCompanyAddress(event);
            
            // Step 2: Create admin phone contact (if phone provided)
            if (event.getAdminPhone() != null && !event.getAdminPhone().isBlank()) {
                createAdminPhoneContact(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("✅ TenantRegisteredEvent processed successfully | Tenant: {}", event.getTenantId());
            
        } catch (Exception e) {
            log.error("❌ Failed to process TenantRegisteredEvent | Tenant: {} | Error: {}",
                    event.getTenantId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be retried
            // After max retries, will go to DLQ (tenant-events.DLT)
            throw new RuntimeException("Failed to process tenant registration event", e);
        }
    }
    
    /**
     * Create company address from tenant registration data
     */
    private void createCompanyAddress(TenantRegisteredEvent event) {
        log.debug("Creating company address | Company: {}", event.getCompanyId());
        
        CreateAddressRequest addressRequest = CreateAddressRequest.builder()
                .ownerId(event.getCompanyId().toString())
                .ownerType(Contact.OwnerType.COMPANY.name())
                .addressLine1(event.getAddressLine1())
                .addressLine2(event.getAddressLine2())
                .city(event.getCity())
                .district(event.getDistrict())
                .postalCode(event.getPostalCode())
                .country(event.getCountry())
                .addressType(AddressType.WORK.name())
                .isPrimary(true)
                .build();
        
        try {
            addressService.createAddress(addressRequest);
            log.info("✅ Company address created | Company: {}", event.getCompanyId());
        } catch (IllegalArgumentException e) {
            // Address already exists (duplicate event) - that's OK, log and continue
            if (e.getMessage() != null && e.getMessage().contains("already")) {
                log.warn("⚠️ Company address already exists | Company: {} | Skipping", event.getCompanyId());
            } else {
                throw e; // Re-throw if it's a different validation error
            }
        }
    }
    
    /**
     * Create admin phone contact
     */
    private void createAdminPhoneContact(TenantRegisteredEvent event) {
        log.debug("Creating admin phone contact | User: {}", event.getUserId());
        
        CreateContactRequest contactRequest = CreateContactRequest.builder()
                .ownerId(event.getUserId().toString())
                .ownerType(Contact.OwnerType.USER.name())
                .contactType(ContactType.PHONE.name())
                .contactValue(event.getAdminPhone())
                .isPrimary(false)  // Email is primary, phone is secondary
                .autoVerified(true)  // Phone auto-verified during onboarding
                .build();
        
        try {
            contactService.createContact(contactRequest);
            log.info("✅ Admin phone contact created | User: {}", event.getUserId());
        } catch (IllegalArgumentException e) {
            // Phone already exists (duplicate event) - that's OK, log and continue
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.warn("⚠️ Admin phone contact already exists | User: {} | Skipping", event.getUserId());
            } else {
                throw e; // Re-throw if it's a different validation error
            }
        }
    }
}

