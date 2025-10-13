package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthMapper {
    
    public CheckContactResponse toNotFoundResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .hasPassword(false)
                .message("This contact is not registered. Please contact your administrator.")
                .build();
    }
    
    public CheckContactResponse toUserNotFoundResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .hasPassword(false)
                .message("User not found. Please contact your administrator.")
                .build();
    }
    
    public CheckContactResponse toCheckResponse(User user) {
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        
        return CheckContactResponse.builder()
                .exists(true)
                .hasPassword(hasPassword)
                .userId(user.getId().toString())
                .message(hasPassword ? "Please enter your password" : "Please create your password")
                .build();
    }
    
    public CheckContactResponse toErrorResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .hasPassword(false)
                .message("An error occurred. Please try again.")
                .build();
    }
    
    public LoginResponse toLoginResponse(User user, ContactDto contact, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(contact.getContactValue())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .build();
    }
    
    public Map<String, Object> buildJwtClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole() != null ? user.getRole().name() : "USER");
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("companyId", user.getCompanyId() != null ? user.getCompanyId().toString() : null);
        return claims;
    }
}

