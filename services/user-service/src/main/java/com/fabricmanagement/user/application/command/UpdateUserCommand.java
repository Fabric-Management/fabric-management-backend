package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Update User Command
 * 
 * Command for updating an existing user's profile
 */
@Data
@Builder
public class UpdateUserCommand implements Command {
    
    private UUID userId;
    private String firstName;
    private String lastName;
    private String displayName;
    private Map<String, Object> preferences;
    private Map<String, Object> settings;
}
