package com.fabricmanagement.user.application.port.in.query;

import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;

/**
 * Use case interface for searching user profiles.
 * Single responsibility: Handle user profile search.
 */
public interface SearchUsersUseCase {
    
    /**
     * Searches user profiles by query.
     * 
     * @param query the search query
     * @param page the page number
     * @param size the page size
     * @return paginated search results
     */
    PageResponse<UserResponse> searchUsers(String query, int page, int size);
}
