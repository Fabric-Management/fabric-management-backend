package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Request Password Reset Command
 * 
 * Command for initiating password reset process
 */
@Data
@Builder
public class RequestPasswordResetCommand implements Command {
    
    private String contactValue;         // email or phone
    private String contactType;         // EMAIL or PHONE
    private String resetMethod;         // LINK or CODE
}
