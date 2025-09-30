package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Reset Password Command
 * 
 * Command for resetting password with verified token
 */
@Data
@Builder
public class ResetPasswordCommand implements Command {
    
    private String contactValue;         // email or phone
    private String resetToken;          // verified token
    private String newPassword;         // new password
    private String resetMethod;         // LINK or CODE
}
