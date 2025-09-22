package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.application.dto.contact.request.CreateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.*;
import com.fabricmanagement.contact.domain.model.CompanyContact;
import com.fabricmanagement.contact.domain.valueobject.ContactStatus;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.persistence.entity.CompanyContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactAddressEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactEmailEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactPhoneEntity;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dedicated mapper for CompanyContact operations.
 * Handles ONLY contact information - NO user profile or company business data.
 */
@Component
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface CompanyContactMapper {

    // ========== Request to Domain ==========

    /**
     * Converts CreateCompanyContactRequest to CompanyContact domain model.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "contactType", constant = "COMPANY")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "displayName", source = "companyName")
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    CompanyContact toDomain(CreateCompanyContactRequest request);

    /**
     * Updates CompanyContact domain model from UpdateCompanyContactRequest.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "contactType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    void updateDomain(UpdateCompanyContactRequest request, @MappingTarget CompanyContact domain);

    // ========== Domain to Entity ==========

    /**
     * Converts CompanyContact domain to CompanyContactEntity.
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "emails", ignore = true)
    @Mapping(target = "phones", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "contactType", expression = "java(mapContactType(domain.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatus(domain.getStatus()))")
    CompanyContactEntity toEntity(CompanyContact domain);

    /**
     * Converts CompanyContactEntity to CompanyContact domain.
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    CompanyContact toDomain(CompanyContactEntity entity);

    // ========== Entity to Response DTOs ==========

    /**
     * Converts CompanyContactEntity to CompanyContactResponse (detailed).
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    @Mapping(target = "isActive", expression = "java(isActiveStatus(entity.getStatus()))")
    @Mapping(target = "emails", expression = "java(mapEmails(entity.getEmails()))")
    @Mapping(target = "phones", expression = "java(mapPhones(entity.getPhones()))")
    @Mapping(target = "addresses", expression = "java(mapAddresses(entity.getAddresses()))")
    CompanyContactResponse toDetailResponse(CompanyContactEntity entity);

    /**
     * Converts CompanyContactEntity to ContactListResponse (summary).
     */
    @Mapping(target = "contactType", expression = "java(mapContactTypeToString(entity.getContactType()))")
    @Mapping(target = "status", expression = "java(mapContactStatusToString(entity.getStatus()))")
    @Mapping(target = "isActive", expression = "java(isActiveStatus(entity.getStatus()))")
    @Mapping(target = "primaryEmail", expression = "java(getPrimaryEmail(entity))")
    @Mapping(target = "primaryPhone", expression = "java(getPrimaryPhone(entity))")
    @Mapping(target = "primaryAddress", expression = "java(getPrimaryAddress(entity))")
    ContactListResponse toListResponse(CompanyContactEntity entity);

    // ========== Contact Info Mappings ==========

    /**
     * Maps ContactEmailEntity to EmailDto.
     */
    @Mapping(target = "isPrimary", source = "isPrimary")
    EmailDto toEmailDto(ContactEmailEntity entity);

    /**
     * Maps ContactPhoneEntity to PhoneDto.
     */
    @Mapping(target = "isPrimary", source = "isPrimary")
    PhoneDto toPhoneDto(ContactPhoneEntity entity);

    /**
     * Maps ContactAddressEntity to AddressDto.
     */
    @Mapping(target = "isPrimary", source = "isPrimary")
    AddressDto toAddressDto(ContactAddressEntity entity);

    // ========== Helper Methods ==========

    /**
     * Maps set of emails to list of DTOs.
     */
    default List<EmailDto> mapEmails(Set<ContactEmailEntity> emails) {
        if (emails == null) return List.of();
        return emails.stream()
            .map(this::toEmailDto)
            .collect(Collectors.toList());
    }

    /**
     * Maps set of phones to list of DTOs.
     */
    default List<PhoneDto> mapPhones(Set<ContactPhoneEntity> phones) {
        if (phones == null) return List.of();
        return phones.stream()
            .map(this::toPhoneDto)
            .collect(Collectors.toList());
    }

    /**
     * Maps set of addresses to list of DTOs.
     */
    default List<AddressDto> mapAddresses(Set<ContactAddressEntity> addresses) {
        if (addresses == null) return List.of();
        return addresses.stream()
            .map(this::toAddressDto)
            .collect(Collectors.toList());
    }

    /**
     * Maps ContactType enum to string.
     */
    default String mapContactTypeToString(ContactType contactType) {
        return contactType != null ? contactType.name() : null;
    }

    /**
     * Maps ContactStatus enum to string.
     */
    default String mapContactStatusToString(ContactStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * Maps string to ContactType enum.
     */
    default ContactType mapContactType(String contactType) {
        try {
            return contactType != null ? ContactType.valueOf(contactType.toUpperCase()) : ContactType.COMPANY;
        } catch (IllegalArgumentException e) {
            return ContactType.COMPANY;
        }
    }

    /**
     * Maps string to ContactStatus enum.
     */
    default ContactStatus mapContactStatus(String status) {
        try {
            return status != null ? ContactStatus.valueOf(status.toUpperCase()) : ContactStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            return ContactStatus.ACTIVE;
        }
    }

    /**
     * Checks if status is active.
     */
    default boolean isActiveStatus(ContactStatus status) {
        return status == ContactStatus.ACTIVE;
    }

    /**
     * Gets primary email from entity.
     */
    default String getPrimaryEmail(CompanyContactEntity entity) {
        if (entity.getMainContactEmail() != null) {
            return entity.getMainContactEmail();
        }
        if (entity.getEmails() != null) {
            return entity.getEmails().stream()
                .filter(ContactEmailEntity::getIsPrimary)
                .map(ContactEmailEntity::getEmail)
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    /**
     * Gets primary phone from entity.
     */
    default String getPrimaryPhone(CompanyContactEntity entity) {
        if (entity.getMainContactPhone() != null) {
            return entity.getMainContactPhone();
        }
        if (entity.getPhones() != null) {
            return entity.getPhones().stream()
                .filter(ContactPhoneEntity::getIsPrimary)
                .map(ContactPhoneEntity::getFullPhoneNumber)
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    /**
     * Gets primary address from entity.
     */
    default String getPrimaryAddress(CompanyContactEntity entity) {
        if (entity.getAddresses() != null) {
            return entity.getAddresses().stream()
                .filter(ContactAddressEntity::getIsPrimary)
                .map(ContactAddressEntity::getFullAddress)
                .findFirst()
                .orElse(null);
        }
        return null;
    }
}