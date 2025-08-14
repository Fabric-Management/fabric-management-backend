package com.fabricmanagement.user.application.port.out;

import com.fabricmanagement.user.domain.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
    User save(User user);

    boolean existsByUsernameAndTenantId(@NotBlank(message = "Username is required") @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters") String username, @NotNull(message = "Tenant ID is required") UUID uuid);
}