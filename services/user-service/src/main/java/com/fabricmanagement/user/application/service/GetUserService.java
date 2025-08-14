package com.fabricmanagement.user.application.service;

import com.fabricmanagement.common.core.exception.DomainException;
import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.application.mapper.UserApplicationMapper;
import com.fabricmanagement.user.application.port.in.GetUserUseCase;
import com.fabricmanagement.user.application.port.out.UserRepositoryPort;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserService implements GetUserUseCase {

    private final UserRepositoryPort userRepository;
    private final UserApplicationMapper userMapper;

    @Override
    public UserResponse getUserById(UUID userId, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        return userMapper.toResponse(user);
    }
}