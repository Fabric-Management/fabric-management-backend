package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Verify Reset Code Command
 * 
 * Command for verifying reset code/link
 */
@Data
@Builder
public class VerifyResetCodeCommand implements Command {
    
    private String contactValue;         // email or phone
    private String resetToken;          // code or link token
    private String resetMethod;         // LINK or CODE
}
