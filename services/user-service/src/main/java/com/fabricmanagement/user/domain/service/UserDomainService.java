package com.fabricmanagement.user.domain.service;

import com.fabricmanagement.common.core.exception.DomainException;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.repository.UserRepository;
import com.fabricmanagement.user.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;

// NOT: Domain Service'ler Spring annotation kullanmamalı (Pure domain)
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;

    public void validateUniqueUsername(String username, TenantId tenantId) {
        if (userRepository.existsByUsernameAndTenantId(username, tenantId.getValue())) {
            throw new DomainException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }

    public User createUser(String firstName, String lastName, String username, TenantId tenantId) {
        validateUniqueUsername(username, tenantId);
        return User.create(firstName, lastName, username, tenantId);
    }
}