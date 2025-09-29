package com.fabricmanagement.user.application.port.in.query;

import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;

/**
 * Use case interface for getting all user profiles.
 * Single responsibility: Handle user profile listing.
 */
public interface GetAllUsersUseCase {
    
    /**
     * Gets all user profiles with pagination.
     * 
     * @param page the page number
     * @param size the page size
     * @param sortBy the sort field
     * @param sortDirection the sort direction
     * @return paginated user list
     */
    PageResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDirection);
}
