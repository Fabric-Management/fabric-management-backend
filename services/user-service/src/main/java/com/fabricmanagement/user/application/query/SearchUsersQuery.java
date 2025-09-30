package com.fabricmanagement.user.application.query;

import com.fabricmanagement.shared.application.query.Query;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Search Users Query
 * 
 * Query for searching users with pagination and filtering
 */
@Data
@Builder
public class SearchUsersQuery implements Query {
    
    private String tenantId;
    private String searchTerm;
    private int page;
    private int size;
    private String sortBy;
    private String sortDirection;
}
