package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Complete Registration Command
 * 
 * Command for completing user registration after invitation
 */
@Data
@Builder
public class CompleteRegistrationCommand implements Command {
    
    private String tenantId;
    private String invitationToken;     // Token from invitation email/SMS
    private String password;
    private String contactValue;         // email or phone used for verification
    private String verificationCode;    // OTP or verification code
}
