package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.domain.model.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactStatus;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.persistence.entity.CompanyContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactAddressEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactEmailEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactPhoneEntity;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Contact domain models, entities, and DTOs.
 * Uses MapStruct for compile-time type safety and performance.
 *
 * This mapper follows clean architecture principles:
 * - Domain models are framework-agnostic
 * - Entities handle persistence concerns
 * - DTOs handle web/API concerns
 */
@Component
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ContactMapper {

    // ========== Domain Model <-> Entity Mappings ==========

    // Removed toEntity method since ContactEntity is abstract
    // Use toCompanyEntity for concrete implementation

    /**
     * Converts a CompanyContactEntity to a Contact domain model.
     * Using CompanyContactEntity as concrete implementation since ContactEntity is abstract.
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    Contact toDomain(CompanyContactEntity entity);

    /**
     * Converts a Contact domain model to a CompanyContactEntity.
     * Used for company-specific contact operations.
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "emails", ignore = true)
    @Mapping(target = "phones", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "contactType", expression = "java(mapContactType(contact.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatus(contact.getStatus()))")
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "industry", ignore = true)
    @Mapping(target = "companySize", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "taxId", ignore = true)
    @Mapping(target = "registrationNumber", ignore = true)
    @Mapping(target = "foundedYear", ignore = true)
    @Mapping(target = "annualRevenue", ignore = true)
    @Mapping(target = "currencyCode", ignore = true)
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "businessUnit", ignore = true)
    @Mapping(target = "mainContactPerson", ignore = true)
    @Mapping(target = "mainContactEmail", ignore = true)
    @Mapping(target = "mainContactPhone", ignore = true)
    @Mapping(target = "businessHours", ignore = true)
    @Mapping(target = "paymentTerms", ignore = true)
    @Mapping(target = "creditLimit", ignore = true)
    CompanyContactEntity toEntity(Contact contact);

    /**
     * Updates an existing Contact domain model from another Contact.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDomain(Contact source, @MappingTarget Contact target);

    // ========== Request DTO -> Domain Model Mappings ==========

    /**
     * Creates a new Contact domain model from a CreateContactRequest.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "contactType", source = "contactType", defaultValue = "USER")
    Contact toDomain(CreateContactRequest request);

    /**
     * Updates an existing Contact domain model from an UpdateContactRequest.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "contactType", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateDomainFromRequest(UpdateContactRequest request, @MappingTarget Contact contact);

    // ========== Domain Model -> Response DTO Mappings ==========

    /**
     * Converts a Contact domain model to a ContactResponse DTO.
     */
    @Mapping(target = "primaryEmail", ignore = true)
    @Mapping(target = "primaryPhone", ignore = true)
    @Mapping(target = "primaryAddress", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "timeZone", ignore = true)
    @Mapping(target = "languagePreference", ignore = true)
    @Mapping(target = "preferredContactMethod", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "industry", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "position", ignore = true)
    ContactResponse toResponse(Contact contact);

    /**
     * Converts a Contact domain model to a ContactDetailResponse DTO.
     * Since ContactDetailResponse has Object types for contactType and status,
     * we need to explicitly cast them as Strings.
     */
    @Mapping(target = "contactType", expression = "java((Object)contact.getContactType())")
    @Mapping(target = "status", expression = "java((Object)contact.getStatus())")
    @Mapping(target = "emails", ignore = true)
    @Mapping(target = "phones", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "timeZone", ignore = true)
    @Mapping(target = "languagePreference", ignore = true)
    @Mapping(target = "preferredContactMethod", ignore = true)
    @Mapping(target = "emergencyContactName", ignore = true)
    @Mapping(target = "emergencyContactPhone", ignore = true)
    @Mapping(target = "emergencyContactRelationship", ignore = true)
    @Mapping(target = "linkedinUrl", ignore = true)
    @Mapping(target = "twitterHandle", ignore = true)
    @Mapping(target = "isActive", expression = "java(\"ACTIVE\".equals(contact.getStatus()))")
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "industry", ignore = true)
    @Mapping(target = "companySize", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "taxId", ignore = true)
    @Mapping(target = "registrationNumber", ignore = true)
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "businessUnit", ignore = true)
    @Mapping(target = "mainContactPerson", ignore = true)
    @Mapping(target = "mainContactEmail", ignore = true)
    @Mapping(target = "mainContactPhone", ignore = true)
    ContactDetailResponse toDetailResponse(Contact contact);

    // ========== Entity -> Response DTO Mappings (for enriched responses) ==========

    /**
     * Converts a ContactEntity to a ContactResponse with enriched data.
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    @Mapping(target = "primaryEmail", expression = "java(mapPrimaryEmail(entity.getEmails()))")
    @Mapping(target = "primaryPhone", expression = "java(mapPrimaryPhone(entity.getPhones()))")
    @Mapping(target = "primaryAddress", expression = "java(mapPrimaryAddress(entity.getAddresses()))")
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "timeZone", ignore = true)
    @Mapping(target = "languagePreference", ignore = true)
    @Mapping(target = "preferredContactMethod", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "industry", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "position", ignore = true)
    ContactResponse toResponse(ContactEntity entity);

    /**
     * Converts a CompanyContactEntity to a ContactResponse with company-specific data.
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    @Mapping(target = "primaryEmail", expression = "java(mapPrimaryEmail(entity.getEmails()))")
    @Mapping(target = "primaryPhone", expression = "java(mapPrimaryPhone(entity.getPhones()))")
    @Mapping(target = "primaryAddress", expression = "java(mapPrimaryAddress(entity.getAddresses()))")
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "timeZone", ignore = true)
    @Mapping(target = "languagePreference", ignore = true)
    @Mapping(target = "preferredContactMethod", ignore = true)
    ContactResponse toResponse(CompanyContactEntity entity);

    /**
     * Converts a CompanyContactEntity to a ContactDetailResponse with full details.
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    @Mapping(target = "emails", expression = "java(mapEmails(entity.getEmails()))")
    @Mapping(target = "phones", expression = "java(mapPhones(entity.getPhones()))")
    @Mapping(target = "addresses", expression = "java(mapAddresses(entity.getAddresses()))")
    @Mapping(target = "isActive", expression = "java(ContactStatus.ACTIVE.equals(entity.getStatus()))")
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "timeZone", ignore = true)
    @Mapping(target = "languagePreference", ignore = true)
    @Mapping(target = "preferredContactMethod", ignore = true)
    @Mapping(target = "emergencyContactName", ignore = true)
    @Mapping(target = "emergencyContactPhone", ignore = true)
    @Mapping(target = "emergencyContactRelationship", ignore = true)
    @Mapping(target = "linkedinUrl", ignore = true)
    @Mapping(target = "twitterHandle", ignore = true)
    ContactDetailResponse toDetailResponse(CompanyContactEntity entity);

    // ========== Collection Mappings ==========

    /**
     * Converts a list of Contact domain models to ContactResponse DTOs.
     */
    List<ContactResponse> toResponseList(List<Contact> contacts);

    /**
     * Converts a list of ContactEntity to ContactResponse DTOs.
     */
    List<ContactResponse> toEntityResponseList(List<ContactEntity> entities);

    // ========== Helper Methods ==========

    /**
     * Maps ContactType enum to string.
     */
    default String mapContactTypeToString(ContactType contactType) {
        return contactType != null ? contactType.name() : null;
    }

    /**
     * Maps string to ContactType enum.
     */
    default ContactType mapContactType(String contactType) {
        if (contactType == null || contactType.isEmpty()) {
            return ContactType.USER;
        }
        try {
            return ContactType.valueOf(contactType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ContactType.USER;
        }
    }

    /**
     * Maps ContactStatus enum to string.
     */
    default String mapContactStatusToString(ContactStatus status) {
        return status != null ? status.name() : "ACTIVE";
    }

    /**
     * Maps string to ContactStatus enum.
     */
    default ContactStatus mapContactStatus(String status) {
        if (status == null || status.isEmpty()) {
            return ContactStatus.ACTIVE;
        }
        try {
            return ContactStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ContactStatus.ACTIVE;
        }
    }

    /**
     * Extracts primary email from email collection.
     */
    default String mapPrimaryEmail(Set<ContactEmailEntity> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
            .filter(email -> email != null && Boolean.TRUE.equals(email.getIsPrimary()))
            .map(ContactEmailEntity::getEmail)
            .findFirst()
            .orElseGet(() -> emails.stream()
                .filter(email -> email != null && email.getEmail() != null)
                .map(ContactEmailEntity::getEmail)
                .findFirst()
                .orElse(null));
    }

    /**
     * Extracts primary phone from phone collection.
     */
    default String mapPrimaryPhone(Set<ContactPhoneEntity> phones) {
        if (phones == null || phones.isEmpty()) {
            return null;
        }
        return phones.stream()
            .filter(phone -> phone != null && Boolean.TRUE.equals(phone.getIsPrimary()))
            .map(ContactPhoneEntity::getPhoneNumber)
            .findFirst()
            .orElseGet(() -> phones.stream()
                .filter(phone -> phone != null && phone.getPhoneNumber() != null)
                .map(ContactPhoneEntity::getPhoneNumber)
                .findFirst()
                .orElse(null));
    }

    /**
     * Extracts primary address from address collection.
     */
    default String mapPrimaryAddress(Set<ContactAddressEntity> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        return addresses.stream()
            .filter(address -> address != null && Boolean.TRUE.equals(address.getIsPrimary()))
            .map(ContactAddressEntity::getFullAddress)
            .findFirst()
            .orElseGet(() -> addresses.stream()
                .filter(address -> address != null && address.getFullAddress() != null)
                .map(ContactAddressEntity::getFullAddress)
                .findFirst()
                .orElse(null));
    }

    /**
     * Maps email entities to ContactEmail value objects for detail response.
     */
    default List<com.fabricmanagement.contact.domain.valueobject.ContactEmail> mapEmails(Set<ContactEmailEntity> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return emails.stream()
            .filter(email -> email != null && email.getEmail() != null)
            .map(email -> com.fabricmanagement.contact.domain.valueobject.ContactEmail.builder()
                .id(email.getId())
                .email(email.getEmail())
                .type(email.getEmailType() != null ? email.getEmailType().name() : "WORK")
                .isPrimary(Boolean.TRUE.equals(email.getIsPrimary()))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Maps phone entities to ContactPhone value objects for detail response.
     */
    default List<com.fabricmanagement.contact.domain.valueobject.ContactPhone> mapPhones(Set<ContactPhoneEntity> phones) {
        if (phones == null || phones.isEmpty()) {
            return List.of();
        }
        return phones.stream()
            .filter(phone -> phone != null && phone.getPhoneNumber() != null)
            .map(phone -> com.fabricmanagement.contact.domain.valueobject.ContactPhone.builder()
                .id(phone.getId())
                .phoneNumber(phone.getPhoneNumber())
                .fullPhoneNumber(phone.getFullPhoneNumber())
                .type(phone.getPhoneType() != null ? phone.getPhoneType().name() : "WORK")
                .isPrimary(Boolean.TRUE.equals(phone.getIsPrimary()))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Maps address entities to ContactAddress value objects for detail response.
     */
    default List<com.fabricmanagement.contact.domain.valueobject.ContactAddress> mapAddresses(Set<ContactAddressEntity> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return List.of();
        }
        return addresses.stream()
            .filter(address -> address != null)
            .map(address -> com.fabricmanagement.contact.domain.valueobject.ContactAddress.builder()
                .id(address.getId())
                .address(address.getStreetAddress1())
                .fullAddress(address.getFullAddress())
                .city(address.getCity())
                .state(address.getStateProvince())
                .country(address.getCountry())
                .postalCode(address.getPostalCode())
                .type(address.getAddressType() != null ? address.getAddressType().name() : "WORK")
                .isPrimary(Boolean.TRUE.equals(address.getIsPrimary()))
                .build())
            .collect(Collectors.toList());
    }
}