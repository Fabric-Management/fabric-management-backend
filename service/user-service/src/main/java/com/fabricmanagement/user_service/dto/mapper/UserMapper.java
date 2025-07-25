package com.fabricmanagement.user_service.dto.mapper;

import com.fabricmanagement.user_service.dto.request.CreateUserRequest;
import com.fabricmanagement.user_service.dto.request.UpdateUserRequest;
import com.fabricmanagement.user_service.dto.response.*;
import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.util.UserHelper;
import org.mapstruct.*;

import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {UserHelper.class, UUID.class}
)
public interface UserMapper {

    // Request to Entity mappings
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", constant = "PENDING_VERIFICATION")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "hasPassword", expression = "java(request.hasPassword())")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", constant = "0")
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    User toEntity(CreateUserRequest request);

    // Update entity from request
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "hasPassword", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);

    // Entity to Response mappings
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "displayName", expression = "java(UserHelper.getDisplayName(user))")
    @Mapping(target = "initials", expression = "java(UserHelper.getInitials(user))")
    @Mapping(target = "accountStatusMessage", expression = "java(UserHelper.getAccountStatusMessage(user))")
    @Mapping(target = "canLogin", expression = "java(UserHelper.canLogin(user))")
    UserResponse toResponse(User user);

    @Mapping(target = "displayName", expression = "java(UserHelper.getDisplayName(user))")
    @Mapping(target = "initials", expression = "java(UserHelper.getInitials(user))")
    UserSummaryResponse toSummaryResponse(User user);

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "displayName", expression = "java(UserHelper.getDisplayName(user))")
    @Mapping(target = "initials", expression = "java(UserHelper.getInitials(user))")
    @Mapping(target = "greeting", expression = "java(UserHelper.getGreeting(user))")
    @Mapping(target = "lastLoginText", expression = "java(UserHelper.getLastLoginText(user))")
    @Mapping(target = "accountStatusMessage", expression = "java(UserHelper.getAccountStatusMessage(user))")
    @Mapping(target = "statistics", ignore = true) // Will be set separately
    UserProfileResponse toProfileResponse(User user);

    @Mapping(target = "displayName", expression = "java(UserHelper.getDisplayName(user))")
    @Mapping(target = "initials", expression = "java(UserHelper.getInitials(user))")
    UserMinimalResponse toMinimalResponse(User user);

    // List mappings
    List<UserResponse> toResponseList(List<User> users);
    List<UserSummaryResponse> toSummaryResponseList(List<User> users);
    List<UserMinimalResponse> toMinimalResponseList(List<User> users);

    // After mapping to set createdBy
    @AfterMapping
    default void setCreatedBy(@MappingTarget User user, UUID createdBy) {
        if (createdBy != null && user.getCreatedBy() == null) {
            user.setCreatedBy(createdBy);
        }
    }
}