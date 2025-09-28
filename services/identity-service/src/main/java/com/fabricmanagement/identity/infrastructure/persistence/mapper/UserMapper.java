package com.fabricmanagement.identity.infrastructure.persistence.mapper;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.valueobject.*;
import com.fabricmanagement.identity.infrastructure.persistence.entity.UserContactEntity;
import com.fabricmanagement.identity.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting between User domain model and UserEntity.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "identity", source = ".")
    @Mapping(target = "credentials", source = ".")
    @Mapping(target = "contacts", source = "contacts")
    @Mapping(target = "primaryContactId", ignore = true)
    @Mapping(target = "pendingVerifications", ignore = true)
    @Mapping(target = "domainEvents", ignore = true)
    User toDomain(UserEntity entity);

    @Mapping(target = "id", source = "identity.id.value")
    @Mapping(target = "tenantId", source = "identity.tenantId")
    @Mapping(target = "username", source = "identity.username")
    @Mapping(target = "firstName", source = "identity.firstName")
    @Mapping(target = "lastName", source = "identity.lastName")
    @Mapping(target = "passwordHash", source = "credentials.passwordHash")
    @Mapping(target = "passwordCreatedAt", source = "credentials.createdAt")
    @Mapping(target = "passwordChangedAt", source = "credentials.lastChangedAt")
    @Mapping(target = "contacts", source = "contacts")
    UserEntity toEntity(User user);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    UserIdentity mapToUserIdentity(UserEntity entity);

    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "createdAt", source = "passwordCreatedAt")
    @Mapping(target = "lastChangedAt", source = "passwordChangedAt")
    Credentials mapToCredentials(UserEntity entity);

    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "deleted", source = "deleted")
    AuditInfo mapToAuditInfo(UserEntity entity);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "type", source = "contactType")
    @Mapping(target = "value", source = "contactValue")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "primary", source = "isPrimary")
    @Mapping(target = "verifiedAt", source = "verifiedAt")
    @Mapping(target = "createdAt", source = "createdAt")
    UserContact toDomainContact(UserContactEntity entity);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "contactType", source = "type")
    @Mapping(target = "contactValue", source = "value")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "isPrimary", source = "primary")
    @Mapping(target = "verifiedAt", source = "verifiedAt")
    @Mapping(target = "createdAt", source = "createdAt")
    UserContactEntity toEntityContact(UserContact contact);

    default List<UserContact> mapContacts(List<UserContactEntity> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
            .map(this::toDomainContact)
            .collect(Collectors.toList());
    }

    default List<UserContactEntity> mapContactEntities(List<UserContact> contacts) {
        if (contacts == null) {
            return null;
        }
        return contacts.stream()
            .map(this::toEntityContact)
            .collect(Collectors.toList());
    }

    @AfterMapping
    default void afterMappingToDomain(UserEntity entity, @MappingTarget User user) {
        // Set primary contact ID if exists
        if (entity.getContacts() != null) {
            entity.getContacts().stream()
                .filter(UserContactEntity::getIsPrimary)
                .findFirst()
                .ifPresent(primaryContact -> 
                    user.setPrimaryContactId(ContactId.of(primaryContact.getId())));
        }
    }

    @AfterMapping
    default void afterMappingToEntity(User user, @MappingTarget UserEntity entity) {
        // Set user ID for all contacts
        if (entity.getContacts() != null) {
            entity.getContacts().forEach(contact -> 
                contact.setUserId(entity.getId()));
        }
    }
}
