package com.fabricmanagement.user_service.dto.request;

import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter arasında olmalıdır")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Kullanıcı adı sadece harf, rakam, nokta, alt çizgi ve tire içerebilir")
        String username,

        @Email(message = "Geçerli bir email adresi giriniz")
        @Size(max = 100, message = "Email 100 karakterden uzun olamaz")
        String email,

        @Size(max = 50, message = "Ad 50 karakterden uzun olamaz")
        String firstName,

        @Size(max = 50, message = "Soyad 50 karakterden uzun olamaz")
        String lastName,

        Set<Role> roles,

        UserStatus status
) {
    // Check if any field is provided for update
    public boolean hasUpdates() {
        return username != null ||
                email != null ||
                firstName != null ||
                lastName != null ||
                roles != null ||
                status != null;
    }
}