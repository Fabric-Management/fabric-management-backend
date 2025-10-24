package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserEventMapper {
    
    public UserCreatedEvent toCreatedEvent(User user, String email) {
        return UserCreatedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(email)
                .status(user.getStatus().name())
                .registrationType(user.getRegistrationType().name())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public UserUpdatedEvent toUpdatedEvent(User user) {
        return UserUpdatedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus().name())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public UserDeletedEvent toDeletedEvent(User user) {
        return UserDeletedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString())
                .timestamp(LocalDateTime.now())
                .build();
    }
}

