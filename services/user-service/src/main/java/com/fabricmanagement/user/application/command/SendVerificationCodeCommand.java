package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Send Verification Code Command
 * 
 * Command for sending verification code to contact info
 */
@Data
@Builder
public class SendVerificationCodeCommand implements Command {
    
    private String contactValue;         // email or phone
    private String contactType;         // EMAIL or PHONE
    private String purpose;            // REGISTRATION, LOGIN, PASSWORD_RESET
}
