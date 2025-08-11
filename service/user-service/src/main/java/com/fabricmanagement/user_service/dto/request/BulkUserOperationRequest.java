package com.fabricmanagement.user_service.dto.request;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record BulkUserOperationRequest(
        @NotNull(message = "Kullanıcı ID listesi boş olamaz")
        @NotEmpty(message = "En az bir kullanıcı ID'si seçilmelidir")
        List<UUID> userIds,

        @NotNull(message = "İşlem tipi boş olamaz")
        OperationType operation,

        // Operation specific data
        UserStatus newStatus,
        Set<Role> rolesToAdd,
        Set<Role> rolesToRemove
) {
    public enum OperationType {
        ACTIVATE,
        DEACTIVATE,
        SUSPEND,
        DELETE,
        UPDATE_STATUS,
        ADD_ROLES,
        REMOVE_ROLES
    }
}