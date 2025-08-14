package com.fabricmanagement.user.application.service;

import com.fabricmanagement.common.core.exception.DomainException;
import com.fabricmanagement.user.application.dto.command.CreateUserCommand;
import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.application.mapper.UserApplicationMapper;
import com.fabricmanagement.user.application.port.in.CreateUserUseCase;
import com.fabricmanagement.user.application.port.out.UserRepositoryPort;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateUserService implements CreateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final UserApplicationMapper userMapper;

    @Override
    public UserResponse createUser(CreateUserCommand command) {
        // Check if username already exists
        if (userRepository.existsByUsernameAndTenantId(command.username(), command.tenantId())) {
            throw new DomainException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }

        // Create new user
        User user = User.create(
                command.firstName(),
                command.lastName(),
                command.username(),
                TenantId.of(command.tenantId())
        );

        // Save and return
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }
}