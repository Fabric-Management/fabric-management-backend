package com.fabricmanagement.user_service.dto.request;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record UserSearchRequest(
        UUID companyId,

        String search,

        List<Role> roles,

        List<UserStatus> statuses,

        Boolean emailVerified,

        Boolean hasPassword,

        @Min(value = 0, message = "Sayfa numarası 0'dan küçük olamaz")
        Integer page,

        @Min(value = 1, message = "Sayfa boyutu 1'den küçük olamaz")
        @Max(value = 100, message = "Sayfa boyutu 100'den büyük olamaz")
        Integer size,

        @Pattern(regexp = "^(username|email|firstName|lastName|createdAt|lastLoginAt)$",
                message = "Geçersiz sıralama alanı")
        String sortBy,

        @Pattern(regexp = "^(ASC|DESC)$", message = "Sıralama yönü ASC veya DESC olmalıdır")
        String sortDirection
) {
    // Default values
    public UserSearchRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
        if (sortBy == null) sortBy = "createdAt";
        if (sortDirection == null) sortDirection = "DESC";
    }

    public boolean hasFilters() {
        return search != null ||
                (roles != null && !roles.isEmpty()) ||
                (statuses != null && !statuses.isEmpty()) ||
                emailVerified != null ||
                hasPassword != null;
    }
}