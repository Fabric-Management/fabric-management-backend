package com.fabricmanagement.user.infrastructure.adapter.in.web.facade;

import com.fabricmanagement.user.application.dto.command.CreateUserCommand;
import com.fabricmanagement.user.application.dto.command.UpdateUserCommand;
import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.application.port.in.CreateUserUseCase;
import com.fabricmanagement.user.application.port.in.GetUserUseCase;
import com.fabricmanagement.user.application.port.in.UpdateUserUseCase;
import com.fabricmanagement.user.infrastructure.adapter.in.web.dto.request.CreateUserRequest;
import com.fabricmanagement.user.infrastructure.adapter.in.web.dto.request.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserFacadeService {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;

    public UserResponse createUser(CreateUserRequest request, UUID tenantId) {
        CreateUserCommand command = new CreateUserCommand(
                request.firstName(),
                request.lastName(),
                request.username(),
                tenantId
        );
        return createUserUseCase.createUser(command);
    }

    public UserResponse getUser(UUID userId, UUID tenantId) {
        return getUserUseCase.getUserById(userId, tenantId);
    }

    public UserResponse updateUser(UUID userId, UpdateUserRequest request, UUID tenantId) {
        UpdateUserCommand command = new UpdateUserCommand(
                userId,
                tenantId,
                request.firstName(),
                request.lastName()
        );
        return updateUserUseCase.updateUser(command);
    }
}