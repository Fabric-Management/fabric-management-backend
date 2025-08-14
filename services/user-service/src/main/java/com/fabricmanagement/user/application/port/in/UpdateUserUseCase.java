package com.fabricmanagement.user.application.port.in;

import com.fabricmanagement.user.application.dto.command.UpdateUserCommand;
import com.fabricmanagement.user.application.dto.query.UserResponse;

public interface UpdateUserUseCase {
    UserResponse updateUser(UpdateUserCommand command);
}
