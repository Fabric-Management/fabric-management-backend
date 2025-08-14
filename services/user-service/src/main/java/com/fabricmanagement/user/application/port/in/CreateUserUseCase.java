package com.fabricmanagement.user.application.port.in;

import com.fabricmanagement.user.application.dto.command.CreateUserCommand;
import com.fabricmanagement.user.application.dto.query.UserResponse;

public interface CreateUserUseCase {
    UserResponse createUser(CreateUserCommand command);
}