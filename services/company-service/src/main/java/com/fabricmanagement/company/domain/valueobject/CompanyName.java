package com.fabricmanagement.company.domain.valueobject;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * Company Name Value Object
 * 
 * Encapsulates company name validation and business rules
 */
@Embeddable
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class CompanyName {
    
    private static final Pattern COMPANY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-&.,()]{2,100}$");
    private static final String ERROR_MESSAGE = "Company name must be 2-100 characters long and contain only letters, numbers, spaces, hyphens, ampersands, periods, commas, and parentheses";
    
    private String value;

    public CompanyName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be null or empty");
        }
        
        String trimmedValue = value.trim();
        
        if (!COMPANY_NAME_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
        
        this.value = trimmedValue;
    }

    public static CompanyName of(String value) {
        return new CompanyName(value);
    }
}
