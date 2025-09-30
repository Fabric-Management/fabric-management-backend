package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Self Register Command
 * 
 * Command for self-registration by users
 */
@Data
@Builder
public class SelfRegisterCommand implements Command {
    
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String displayName;
    private String companyName;          // If registering as company employee
    private String companyType;         // EMPLOYEE or EXTERNAL_PARTNER
    private String password;
    private String verificationCode;    // OTP for contact verification
}
