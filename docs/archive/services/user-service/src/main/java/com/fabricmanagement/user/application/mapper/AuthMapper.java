package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.shared.domain.message.AuthMessageKeys;
import com.fabricmanagement.shared.infrastructure.service.MessageResolver;
import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthMapper {
    
    private final MessageResolver messageResolver;
    
    public CheckContactResponse toNotFoundResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .verified(false)
                .hasPassword(false)
                .userId(null)
                .maskedContact(null)
                .nextStep("REGISTER")
                .message(messageResolver.getMessage(AuthMessageKeys.EMAIL_NOT_REGISTERED))
                .build();
    }
    
    public CheckContactResponse toUserNotFoundResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .verified(false)
                .hasPassword(false)
                .userId(null)
                .maskedContact(null)
                .nextStep("CONTACT_ADMIN")
                .message(messageResolver.getMessage(AuthMessageKeys.USER_NOT_FOUND))
                .build();
    }
    
    public CheckContactResponse toCheckResponse(User user) {
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        
        String messageKey = hasPassword 
            ? AuthMessageKeys.EMAIL_FOUND_LOGIN 
            : AuthMessageKeys.EMAIL_FOUND_SET_PASSWORD;
        
        return CheckContactResponse.builder()
                .exists(true)
                .hasPassword(hasPassword)
                .userId(user.getId().toString())
                .message(messageResolver.getMessage(messageKey))
                .build();
    }
    
    public CheckContactResponse toErrorResponse() {
        return CheckContactResponse.builder()
                .exists(false)
                .hasPassword(false)
                .message(messageResolver.getMessage(AuthMessageKeys.EMAIL_NOT_REGISTERED))
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

