package com.fabricmanagement.user.application.query;

import com.fabricmanagement.shared.application.query.Query;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Get User Query
 * 
 * Query for retrieving a user by ID
 */
@Data
@Builder
public class GetUserQuery implements Query {
    
    private UUID userId;
}
