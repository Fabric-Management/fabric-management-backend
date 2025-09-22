package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.application.dto.usercontact.request.CreateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.request.UpdateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactResponse;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactListResponse;
import com.fabricmanagement.contact.domain.model.UserContact;
import com.fabricmanagement.contact.infrastructure.persistence.entity.UserContactEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for UserContact domain conversions.
 * Handles mapping between domain objects, entities, and DTOs.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserContactMapper {

    // Domain to Response mappings
    @Mapping(target = "fullAddress", expression = "java(domain.getFullAddress())")
    UserContactResponse toResponse(UserContact domain);

    UserContactListResponse toListResponse(UserContact domain);

    // Entity to Response mappings
    @Mapping(target = "fullAddress", expression = "java(entity.getFullAddress())")
    UserContactResponse toResponse(UserContactEntity entity);

    UserContactListResponse toListResponse(UserContactEntity entity);

    // Request to Domain mappings
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "contactType", constant = "USER")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "displayName", source = "userDisplayName")
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "preferredContactMethod", defaultValue = "EMAIL")
    @Mapping(target = "publicProfile", defaultValue = "false")
    @Mapping(target = "allowDirectMessages", defaultValue = "true")
    @Mapping(target = "allowNotifications", defaultValue = "true")
    UserContact toDomain(CreateUserContactRequest request);

    // Update Domain from Request
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "contactType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "displayName", source = "userDisplayName")
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateDomainFromRequest(UpdateUserContactRequest request, @MappingTarget UserContact domain);

    // Domain to Entity mappings
    @Mapping(target = "contactType", constant = "USER")
    UserContactEntity toEntity(UserContact domain);

    // Entity to Domain mappings
    UserContact toDomain(UserContactEntity entity);

    // Update Entity from Domain
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntityFromDomain(UserContact domain, @MappingTarget UserContactEntity entity);

}