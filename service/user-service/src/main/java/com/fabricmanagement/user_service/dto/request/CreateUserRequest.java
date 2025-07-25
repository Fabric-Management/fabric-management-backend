package com.fabricmanagement.user_service.dto.request;

import com.fabricmanagement.user_service.entity.enums.Role;
import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank(message = "Kullanıcı adı boş olamaz")
        @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter arasında olmalıdır")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Kullanıcı adı sadece harf, rakam, nokta, alt çizgi ve tire içerebilir")
        String username,

        @NotBlank(message = "Email boş olamaz")
        @Email(message = "Geçerli bir email adresi giriniz")
        @Size(max = 100, message = "Email 100 karakterden uzun olamaz")
        String email,

        @Size(max = 50, message = "Ad 50 karakterden uzun olamaz")
        String firstName,

        @Size(max = 50, message = "Soyad 50 karakterden uzun olamaz")
        String lastName,

        @NotNull(message = "En az bir rol seçilmelidir")
        @NotEmpty(message = "En az bir rol seçilmelidir")
        Set<Role> roles,

        @NotNull(message = "Şirket ID boş olamaz")
        UUID companyId,

        // İlk kullanıcı oluşturulurken şifre zorunlu değil
        // Kullanıcı ilk girişte kendisi belirleyecek
        String password
) {
    // Validation helper methods
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    // Builder-like method for convenience
    public CreateUserRequest withGeneratedUsername(String generatedUsername) {
        return new CreateUserRequest(
                generatedUsername,
                this.email,
                this.firstName,
                this.lastName,
                this.roles,
                this.companyId,
                this.password
        );
    }
}