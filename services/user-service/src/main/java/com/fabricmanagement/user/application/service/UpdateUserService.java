package com.fabricmanagement.user.application.service;

import com.fabricmanagement.common.core.exception.DomainException;
import com.fabricmanagement.user.application.dto.command.UpdateUserCommand;
import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.application.mapper.UserApplicationMapper;
import com.fabricmanagement.user.application.port.in.UpdateUserUseCase;
import com.fabricmanagement.user.application.port.out.UserRepositoryPort;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpdateUserService implements UpdateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final UserApplicationMapper userMapper;

    @Override
    public UserResponse updateUser(UpdateUserCommand command) {
        log.debug("Updating user with id: {} for tenant: {}", command.userId(), command.tenantId());

        // Find existing user
        User user = userRepository.findByIdAndTenantId(command.userId(), command.tenantId())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        // Update user profile
        user.updateProfile(command.firstName(), command.lastName());

        // Save updated user
        User updatedUser = userRepository.save(user);

        log.info("User updated successfully. Id: {}, Username: {}",
                updatedUser.getId(), updatedUser.getUsername());

        return userMapper.toResponse(updatedUser);
    }
}