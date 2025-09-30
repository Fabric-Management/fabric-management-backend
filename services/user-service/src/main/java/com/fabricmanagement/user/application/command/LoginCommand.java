package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Login Command
 * 
 * Command for user authentication using contact info + password
 */
@Data
@Builder
public class LoginCommand implements Command {
    
    private String contactValue;         // email or phone number
    private String password;
    private String tenantId;            // Optional, for multi-tenant login
    private boolean rememberMe;
    private Map<String, Object> deviceInfo;
}
