package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Invite User Command
 * 
 * Command for inviting a new user to the system
 */
@Data
@Builder
public class InviteUserCommand implements Command {
    
    private String tenantId;
    private String invitedByUserId;     // Who is inviting
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String displayName;
    private String role;                 // Role to assign
    private Map<String, Object> metadata;
}
