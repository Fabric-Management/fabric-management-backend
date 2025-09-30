package com.fabricmanagement.user.application.command;

import com.fabricmanagement.shared.application.command.Command;
import lombok.Builder;
import lombok.Data;

/**
 * Check Contact Availability Command
 * 
 * Command for checking if contact info exists in system
 */
@Data
@Builder
public class CheckContactAvailabilityCommand implements Command {
    
    private String contactValue;         // email or phone
    private String contactType;         // EMAIL or PHONE
}
