package com.fabricmanagement.user.application.service.command;

import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.application.port.in.command.CreateUserUseCase;
import com.fabricmanagement.user.application.port.out.UserEventPublisherPort;
import com.fabricmanagement.user.application.port.out.UserRepositoryPort;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for creating user profiles.
 * Single responsibility: Handle user profile creation.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CreateUserService implements CreateUserUseCase {
    
    private final UserRepositoryPort userRepository;
    private final UserMapper userMapper;
    private final UserEventPublisherPort eventPublisher;
    
    @Override
    public UserDetailResponse createUser(CreateUserRequest request) {
        log.info("Creating new user profile for identity: {} with name: {} {}", 
                request.identityId(), request.firstName(), request.lastName());
        
        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException(UserErrorCode.INVALID_TENANT.getMessage());
        }
        
        // Check if user profile already exists for this identity
        if (userRepository.existsByIdentityIdAndTenantId(request.identityId(), tenantId)) {
            throw new IllegalArgumentException(UserErrorCode.DUPLICATE_IDENTITY_ID.getMessage());
        }
        
        User user = userMapper.toDomain(request);
        user.setTenantId(tenantId);
        user.setIdentityId(request.identityId());
        user.setStatus(UserStatus.ACTIVE);
        
        User savedUser = userRepository.save(user);
        
        // Publish domain event
        UserCreatedEvent event = UserCreatedEvent.of(
            savedUser.getId(),
            savedUser.getTenantId(),
            savedUser.getIdentityId(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            savedUser.getDisplayName(),
            savedUser.getJobTitle(),
            savedUser.getDepartment(),
            LocalDateTime.now()
        );
        eventPublisher.publishUserCreatedEvent(event);
        
        log.info("User profile created successfully with ID: {}", savedUser.getId());
        return userMapper.toDetailResponse(savedUser);
    }
}
