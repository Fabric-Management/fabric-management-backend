package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Create User Command
 * 
 * Command for creating a new user in the system
 */
@Data
@Builder
public class CreateUserCommand implements Command {
    
    private UUID tenantId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private Map<String, Object> preferences;
    private Map<String, Object> settings;
}
