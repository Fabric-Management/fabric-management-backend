package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.request.UpdateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import com.fabricmanagement.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between User domain models, entities, and DTOs.
 * Uses MapStruct for compile-time type safety and performance.
 * Focused on user profile information only.
 */
@Component
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    // ========== Domain Model <-> Entity Mappings ==========

    /**
     * Converts a UserEntity to a User domain model.
     */
    @Mapping(target = "status", expression = "java(mapUserStatusToEnum(entity.getStatus()))")
    User toDomain(UserEntity entity);

    /**
     * Converts a User domain model to a UserEntity.
     */
    @Mapping(target = "status", expression = "java(mapUserStatusToEnum(user.getStatus()))")
    UserEntity toEntity(User user);

    // ========== DTO <-> Domain Model Mappings ==========

    /**
     * Converts a CreateUserRequest to a User domain model.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    User toDomain(CreateUserRequest request);

    /**
     * Updates a User domain model from an UpdateUserRequest.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateDomainFromRequest(UpdateUserRequest request, @MappingTarget User user);

    // ========== Domain Model <-> DTO Response Mappings ==========

    /**
     * Converts a User domain model to a UserResponse.
     */
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "status", expression = "java(mapUserStatusToString(user.getStatus()))")
    @Mapping(target = "isActive", expression = "java(user.isActive())")
    UserResponse toResponse(User user);

    /**
     * Converts a User domain model to a UserDetailResponse.
     */
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "status", expression = "java(mapUserStatusToString(user.getStatus()))")
    @Mapping(target = "isActive", expression = "java(user.isActive())")
    UserDetailResponse toDetailResponse(User user);

    // ========== Entity <-> DTO Response Mappings ==========

    /**
     * Converts a UserEntity to a UserResponse.
     */
    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    @Mapping(target = "status", expression = "java(mapUserStatusToString(entity.getStatus()))")
    @Mapping(target = "isActive", expression = "java(entity.isActive())")
    UserResponse toResponseFromEntity(UserEntity entity);

    /**
     * Converts a UserEntity to a UserDetailResponse.
     */
    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    @Mapping(target = "status", expression = "java(mapUserStatusToString(entity.getStatus()))")
    @Mapping(target = "isActive", expression = "java(entity.isActive())")
    UserDetailResponse toDetailResponseFromEntity(UserEntity entity);

    // ========== Helper Methods ==========

    /**
     * Maps UserStatus enum to string.
     */
    default String mapUserStatusToString(UserStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * Maps string to UserStatus enum.
     */
    default UserStatus mapUserStatusToEnum(UserStatus status) {
        return status != null ? status : UserStatus.ACTIVE;
    }

    /**
     * Maps string to UserStatus enum from string.
     */
    default UserStatus mapUserStatusFromString(String status) {
        try {
            return status != null ? UserStatus.valueOf(status.toUpperCase()) : UserStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            return UserStatus.ACTIVE;
        }
    }
}

