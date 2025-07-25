package com.fabricmanagement.user_service.service.impl;

import com.fabricmanagement.user_service.constants.MessageKeys;
import com.fabricmanagement.user_service.dto.mapper.UserMapper;
import com.fabricmanagement.user_service.dto.request.*;
import com.fabricmanagement.user_service.dto.response.*;
import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import com.fabricmanagement.user_service.exception.*;
import com.fabricmanagement.user_service.repository.CustomUserRepository;
import com.fabricmanagement.user_service.repository.UserRepository;
import com.fabricmanagement.user_service.service.MessageService;
import com.fabricmanagement.user_service.service.UserService;
import com.fabricmanagement.user_service.util.DateTimeHelper;
import com.fabricmanagement.user_service.util.PasswordHelper;
import com.fabricmanagement.user_service.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomUserRepository customUserRepository;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MessageService messageService;

    // Configuration constants
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final String USER_EXCHANGE = "user.exchange";

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, UUID createdBy) {
        log.info("Creating new user with username: {}", request.username());

        // Validate createdBy
        if (createdBy == null) {
            // In production, this should come from security context
            // For now, throw exception
            throw new BadRequestException(
                    messageService.getMessage(MessageKeys.Validation.REQUIRED_FIELD, "createdBy")
            );
        }

        // Validate uniqueness
        validateUsernameUniqueness(request.username());
        validateEmailUniqueness(request.email());

        // Create user entity
        User user = userMapper.toEntity(request);
        user.setCreatedBy(createdBy);

        // Handle password if provided
        if (request.hasPassword()) {
            user.setPasswordHash(PasswordHelper.encode(request.password()));
            user.setHasPassword(true);
            user.setPasswordChangedAt(DateTimeHelper.now());
            user.setStatus(UserStatus.ACTIVE);
        }

        // Save user
        user = userRepository.save(user);
        log.info("User created successfully with ID: {}", user.getId());

        // Publish event
        publishUserCreatedEvent(user);

        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        User user = getUserEntityById(id);
        return userMapper.toResponse(user);
    }

    @Override
    public User getUserEntityById(UUID id) {
        return userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(
                        messageService.getMessage(MessageKeys.Error.USER_NOT_FOUND)
                ));
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsernameAndNotDeleted(username)
                .orElseThrow(() -> new UserNotFoundException(
                        messageService.getMessage(MessageKeys.Error.USERNAME_NOT_FOUND)
                ));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmailAndNotDeleted(email)
                .orElseThrow(() -> new UserNotFoundException(
                        messageService.getMessage(MessageKeys.Error.EMAIL_NOT_FOUND)
                ));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = getUserEntityById(id);

        // Validate uniqueness if username or email is being updated
        if (request.username() != null && !user.getUsername().equals(request.username())) {
            validateUsernameUniqueness(request.username());
        }

        if (request.email() != null && !user.getEmail().equals(request.email())) {
            validateEmailUniqueness(request.email());
            user.setEmailVerified(false); // Reset verification on email change
        }

        // Update entity
        userMapper.updateEntity(request, user);
        user = userRepository.save(user);

        log.info("User updated successfully with ID: {}", id);

        // Publish event
        publishUserUpdatedEvent(user);

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        log.info("Soft deleting user with ID: {}", id);

        User user = getUserEntityById(id);
        user.softDelete();
        userRepository.save(user);

        log.info("User soft deleted successfully with ID: {}", id);

        // Publish event
        publishUserDeletedEvent(user);
    }

    @Override
    @Transactional
    public void deleteUsers(List<UUID> ids) {
        log.info("Soft deleting {} users", ids.size());

        int deletedCount = userRepository.softDeleteUsers(ids, DateTimeHelper.now());
        log.info("Soft deleted {} users", deletedCount);

        // Publish events for each deleted user
        ids.forEach(id -> publishUserDeletedEvent(User.builder().id(id).build()));
    }

    @Override
    public Page<UserSummaryResponse> searchUsers(UserSearchRequest request) {
        log.debug("Searching users with filters: {}", request);

        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(Sort.Direction.valueOf(request.sortDirection()), request.sortBy())
        );

        Page<User> users;

        if (request.hasFilters()) {
            users = customUserRepository.findUsersWithFilters(
                    request.companyId(),
                    request.roles(),
                    request.statuses(),
                    request.search(),
                    request.emailVerified(),
                    request.hasPassword(),
                    pageable
            );
        } else if (request.companyId() != null) {
            users = userRepository.findByCompanyId(request.companyId(), pageable);
        } else {
            users = userRepository.findAllActive(pageable);
        }

        return users.map(userMapper::toSummaryResponse);
    }

    @Override
    public Page<UserSummaryResponse> getUsersByCompany(UUID companyId, Pageable pageable) {
        Page<User> users = userRepository.findByCompanyId(companyId, pageable);
        return users.map(userMapper::toSummaryResponse);
    }

    @Override
    public List<UserMinimalResponse> getActiveUsersByCompany(UUID companyId) {
        List<User> users = userRepository.findByCompanyId(companyId,
                PageRequest.of(0, 1000, Sort.by("firstName", "lastName"))).getContent();

        return users.stream()
                .filter(User::isActive)
                .map(userMapper::toMinimalResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PasswordCreatedResponse createPassword(UUID userId, CreatePasswordRequest request) {
        log.info("Creating password for user ID: {}", userId);

        User user = getUserEntityById(userId);

        if (user.isHasPassword()) {
            throw new BadRequestException(
                    messageService.getMessage(MessageKeys.Error.USER_HAS_PASSWORD)
            );
        }

        // Validate password strength
        PasswordHelper.ValidationResult validation = PasswordHelper.validate(request.password());
        if (!validation.valid()) {
            throw new BadRequestException(validation.getErrorMessage());
        }

        // Set password
        user.setPasswordHash(PasswordHelper.encode(request.password()));
        user.setHasPassword(true);
        user.setPasswordChangedAt(DateTimeHelper.now());

        // Activate user if pending
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
        log.info("Password created successfully for user ID: {}", userId);

        // Get success message and return response
        String message = messageService.getMessage(MessageKeys.Success.PASSWORD_CREATED);
        return PasswordCreatedResponse.ofSuccess(message);
    }

    @Override
    @Transactional
    public PasswordChangedResponse changePassword(UUID userId, ChangePasswordRequest request) {
        log.info("Changing password for user ID: {}", userId);

        User user = getUserEntityById(userId);

        // Verify current password
        if (!PasswordHelper.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException(
                    messageService.getMessage(MessageKeys.Error.CURRENT_PASSWORD_WRONG)
            );
        }

        // Validate new password
        PasswordHelper.ValidationResult validation = PasswordHelper.validate(request.newPassword());
        if (!validation.valid()) {
            throw new BadRequestException(validation.getErrorMessage());
        }

        // Update password
        user.setPasswordHash(PasswordHelper.encode(request.newPassword()));
        user.setPasswordChangedAt(DateTimeHelper.now());
        userRepository.save(user);

        log.info("Password changed successfully for user ID: {}", userId);

        // Get success message and return response
        String message = messageService.getMessage(MessageKeys.Success.PASSWORD_CHANGED);
        return PasswordChangedResponse.ofSuccess(message, true);
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        log.debug("Recording successful login for user ID: {}", userId);

        userRepository.updateLastLogin(userId, DateTimeHelper.now());
    }

    @Override
    @Transactional
    public void recordFailedLogin(String identifier) {
        log.debug("Recording failed login attempt for: {}", identifier);

        Optional<User> userOpt = StringUtils.isValidEmail(identifier)
                ? userRepository.findByEmailAndNotDeleted(identifier)
                : userRepository.findByUsernameAndNotDeleted(identifier);

        userOpt.ifPresent(user -> {
            user.incrementFailedLoginAttempts();

            // Lock account if max attempts exceeded
            if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
                user.lockAccount(LOCK_DURATION_MINUTES);
                log.warn("User account locked due to failed login attempts: {}", user.getId());
            }

            userRepository.save(user);
        });
    }

    @Override
    public void initiatePasswordReset(ResetPasswordRequest request) {
        log.info("Initiating password reset for: {}", request.identifier());

        // Find user by email or username
        Optional<User> userOpt = StringUtils.isValidEmail(request.identifier())
                ? userRepository.findByEmailAndNotDeleted(request.identifier())
                : userRepository.findByUsernameAndNotDeleted(request.identifier());

        if (userOpt.isEmpty()) {
            // Don't reveal if user exists or not for security
            log.warn("Password reset requested for non-existent user: {}", request.identifier());
            return;
        }

        User user = userOpt.get();

        // TODO: Generate reset token and send email
        // This requires email service implementation
        log.info("Password reset initiated for user: {}", user.getId());
    }

    @Override
    public PasswordChangedResponse confirmPasswordReset(ConfirmResetPasswordRequest request) {
        // TODO: Implement with token service
        throw new UnsupportedOperationException("Password reset confirmation not implemented yet");
    }

    @Override
    public void sendEmailVerification(UUID userId) {
        log.info("Sending email verification for user: {}", userId);

        User user = getUserEntityById(userId);

        if (user.isEmailVerified()) {
            log.warn("Email already verified for user: {}", userId);
            return;
        }

        // TODO: Generate verification token and send email
        // This requires email service implementation
        log.info("Email verification sent for user: {}", userId);
    }

    @Override
    public EmailVerificationResponse verifyEmail(String token) {
        // TODO: Implement with token service
        throw new UnsupportedOperationException("Email verification not implemented yet");
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        User user = getUserEntityById(id);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("User activated with ID: {}", id);
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        User user = getUserEntityById(id);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("User deactivated with ID: {}", id);
    }

    @Override
    @Transactional
    public void suspendUser(UUID id) {
        User user = getUserEntityById(id);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        log.info("User suspended with ID: {}", id);
    }

    @Override
    @Transactional
    public void unlockUser(UUID id) {
        User user = getUserEntityById(id);
        user.setStatus(UserStatus.ACTIVE);
        user.resetFailedLoginAttempts();
        userRepository.save(user);
        log.info("User unlocked with ID: {}", id);
    }

    @Override
    @Transactional
    public BulkOperationResponse bulkOperation(BulkUserOperationRequest request) {
        log.info("Performing bulk operation: {} on {} users", request.operation(), request.userIds().size());

        List<BulkOperationResponse.OperationResult> results = new ArrayList<>();

        for (UUID userId : request.userIds()) {
            try {
                switch (request.operation()) {
                    case ACTIVATE -> activateUser(userId);
                    case DEACTIVATE -> deactivateUser(userId);
                    case SUSPEND -> suspendUser(userId);
                    case DELETE -> deleteUser(userId);
                    case UPDATE_STATUS -> {
                        if (request.newStatus() != null) {
                            User user = getUserEntityById(userId);
                            user.setStatus(request.newStatus());
                            userRepository.save(user);
                        }
                    }
                    case ADD_ROLES -> {
                        if (request.rolesToAdd() != null) {
                            addRoles(userId, new ArrayList<>(request.rolesToAdd()));
                        }
                    }
                    case REMOVE_ROLES -> {
                        if (request.rolesToRemove() != null) {
                            removeRoles(userId, new ArrayList<>(request.rolesToRemove()));
                        }
                    }
                }

                results.add(new BulkOperationResponse.OperationResult(
                        userId,
                        true,
                        messageService.getMessage(MessageKeys.Success.USER_UPDATED)
                ));
            } catch (Exception e) {
                log.error("Failed to perform bulk operation on user: {}", userId, e);
                results.add(new BulkOperationResponse.OperationResult(
                        userId,
                        false,
                        e.getMessage()
                ));
            }
        }

        String summary = messageService.format(
                MessageKeys.Success.BULK_OPERATION_COMPLETED,
                results.size(),
                results.stream().filter(BulkOperationResponse.OperationResult::success).count()
        );
        return BulkOperationResponse.fromResults(results, summary);
    }

    @Override
    @Transactional
    public void addRoles(UUID userId, List<Role> roles) {
        User user = getUserEntityById(userId);
        roles.forEach(user::addRole);
        userRepository.save(user);
        log.info("Added {} roles to user ID: {}", roles.size(), userId);
    }

    @Override
    @Transactional
    public void removeRoles(UUID userId, List<Role> roles) {
        User user = getUserEntityById(userId);
        roles.forEach(user::removeRole);
        userRepository.save(user);
        log.info("Removed {} roles from user ID: {}", roles.size(), userId);
    }

    @Override
    @Transactional
    public void setRoles(UUID userId, List<Role> roles) {
        User user = getUserEntityById(userId);
        user.getRoles().clear();
        user.getRoles().addAll(new HashSet<>(roles));
        userRepository.save(user);
        log.info("Set {} roles for user ID: {}", roles.size(), userId);
    }

    @Override
    public UserValidationResponse validateUser(String identifier) {
        Optional<User> userOpt = StringUtils.isValidEmail(identifier)
                ? userRepository.findByEmailAndNotDeleted(identifier)
                : userRepository.findByUsernameAndNotDeleted(identifier);

        if (userOpt.isEmpty()) {
            return UserValidationResponse.userNotFound();
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return UserValidationResponse.inactive(
                    messageService.getMessage(MessageKeys.Error.ACCOUNT_NOT_ACTIVE)
            );
        }

        if (user.isLocked()) {
            return UserValidationResponse.inactive(
                    messageService.format(MessageKeys.Error.ACCOUNT_LOCKED, LOCK_DURATION_MINUTES)
            );
        }

        return UserValidationResponse.valid();
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        return !existsByUsername(username);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        return !existsByEmail(email);
    }

    @Override
    public UserDashboardStats getDashboardStats(UUID companyId) {
        log.debug("Getting dashboard stats for company: {}", companyId);

        CustomUserRepository.UserStatistics stats = customUserRepository.getUserStatistics(companyId);
        List<CustomUserRepository.RoleCount> roleCounts = customUserRepository.countUsersByRole(companyId);

        // Convert to response DTOs
        List<UserDashboardStats.RoleDistribution> roleDistribution = roleCounts.stream()
                .map(rc -> new UserDashboardStats.RoleDistribution(
                        rc.role(),
                        rc.count(),
                        stats.totalUsers() > 0 ? (double) rc.count() / stats.totalUsers() * 100 : 0
                ))
                .collect(Collectors.toList());

        // TODO: Implement status distribution and daily counts
        List<UserDashboardStats.StatusDistribution> statusDistribution = new ArrayList<>();
        List<UserDashboardStats.DailyUserCount> dailyCounts = new ArrayList<>();

        return new UserDashboardStats(
                stats.totalUsers(),
                stats.activeUsers(),
                stats.verifiedUsers(),
                stats.totalUsers() - stats.verifiedUsers(),
                stats.usersWithPassword(),
                stats.totalUsers() - stats.usersWithPassword(),
                stats.lockedUsers(),
                0, // TODO: Calculate today's new users
                0, // TODO: Calculate this week's new users
                stats.newUsersThisMonth(),
                roleDistribution,
                statusDistribution,
                dailyCounts,
                DateTimeHelper.now()
        );
    }

    @Override
    public CompanyUserStats getCompanyStats(UUID companyId) {
        log.debug("Getting company stats for: {}", companyId);

        long totalUsers = userRepository.countByCompanyId(companyId);
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        List<CustomUserRepository.RoleCount> roleCounts = customUserRepository.countUsersByRole(companyId);

        Map<Role, Long> usersByRole = roleCounts.stream()
                .collect(Collectors.toMap(
                        CustomUserRepository.RoleCount::role,
                        CustomUserRepository.RoleCount::count
                ));

        // TODO: Get company name from company service
        String companyName = "Company " + companyId;

        // TODO: Get date of first and last user
        LocalDateTime lastUserCreated = DateTimeHelper.now();
        LocalDateTime oldestUserCreated = DateTimeHelper.now().minusMonths(6);

        return new CompanyUserStats(
                companyId,
                companyName,
                totalUsers,
                activeUsers,
                usersByRole,
                lastUserCreated,
                oldestUserCreated
        );
    }

    @Override
    public LoginActivityStats getLoginStats(UUID companyId) {
        // TODO: Implement login statistics
        // This requires tracking login events
        return new LoginActivityStats(
                0,
                0,
                0,
                new ArrayList<>(),
                new ArrayList<>(),
                0.0
        );
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user = getUserEntityById(userId);
        UserProfileResponse profile = userMapper.toProfileResponse(user);

        // TODO: Calculate real statistics
        UserStatistics stats = new UserStatistics(
                0, // Total logins
                0, // Active days
                user.getCreatedAt(),
                messageService.format(MessageKeys.Info.MEMBER_SINCE,
                        DateTimeHelper.formatDate(user.getCreatedAt(), Locale.getDefault()))
        );

        return new UserProfileResponse(
                profile.id(),
                profile.username(),
                profile.email(),
                profile.firstName(),
                profile.lastName(),
                profile.fullName(),
                profile.displayName(),
                profile.initials(),
                profile.roles(),
                profile.status(),
                profile.emailVerified(),
                profile.hasPassword(),
                profile.companyId(),
                profile.createdAt(),
                profile.lastLoginAt(),
                profile.passwordChangedAt(),
                profile.greeting(),
                profile.lastLoginText(),
                profile.accountStatusMessage(),
                stats
        );
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(UUID userId, UpdateUserRequest request) {
        // Users can only update their own name fields
        UpdateUserRequest limitedRequest = new UpdateUserRequest(
                null, // cannot change username
                null, // cannot change email via profile
                request.firstName(),
                request.lastName(),
                null, // cannot change roles
                null  // cannot change status
        );

        return updateUser(userId, limitedRequest);
    }

    // Helper methods
    private void validateUsernameUniqueness(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException(
                    messageService.format(MessageKeys.Error.USERNAME_EXISTS, username)
            );
        }
    }

    private void validateEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    messageService.format(MessageKeys.Error.EMAIL_EXISTS, email)
            );
        }
    }

    // Event publishing methods
    private void publishUserCreatedEvent(User user) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_CREATED",
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "companyId", user.getCompanyId(),
                "timestamp", DateTimeHelper.now()
        );

        rabbitTemplate.convertAndSend(USER_EXCHANGE, "user.created", event);
        log.debug("Published USER_CREATED event for user ID: {}", user.getId());
    }

    private void publishUserUpdatedEvent(User user) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_UPDATED",
                "userId", user.getId(),
                "timestamp", DateTimeHelper.now()
        );

        rabbitTemplate.convertAndSend(USER_EXCHANGE, "user.updated", event);
        log.debug("Published USER_UPDATED event for user ID: {}", user.getId());
    }

    private void publishUserDeletedEvent(User user) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_DELETED",
                "userId", user.getId(),
                "timestamp", DateTimeHelper.now()
        );

        rabbitTemplate.convertAndSend(USER_EXCHANGE, "user.deleted", event);
        log.debug("Published USER_DELETED event for user ID: {}", user.getId());
    }
}