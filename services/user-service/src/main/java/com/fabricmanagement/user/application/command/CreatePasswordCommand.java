package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Create Password Command
 * 
 * Command for creating password with verification code
 */
@Data
@Builder
public class CreatePasswordCommand implements Command {
    
    private String contactValue;         // email or phone
    private String contactType;         // EMAIL or PHONE
    private String verificationCode;    // OTP code
    private String password;            // new password
    private String firstName;           // for new users
    private String lastName;            // for new users
    private String displayName;         // for new users
    private String companyName;         // optional company info
    private String userType;            // EMPLOYEE or EXTERNAL_PARTNER
}
