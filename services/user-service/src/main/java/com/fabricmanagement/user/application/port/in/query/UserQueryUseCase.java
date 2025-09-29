package com.fabricmanagement.user.application.port.in.query;

import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;
import com.fabricmanagement.user.domain.valueobject.UserId;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Port interface for querying users.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface UserQueryUseCase {
    
    /**
     * Retrieves a user by their ID.
     *
     * @param userId the user ID
     * @return the user response
     */
    UserDetailResponse getUserById(UserId userId);
    
    /**
     * Retrieves all users with pagination.
     *
     * @param pageable pagination information
     * @return page of user responses
     */
    Page<UserResponse> getAllUsers(Pageable pageable);
    
    /**
     * Searches users by username or email.
     *
     * @param query the search query
     * @return list of matching users
     */
    List<UserResponse> searchUsers(String query);
    
    /**
     * Retrieves users by status.
     *
     * @param status the user status
     * @return list of users with the specified status
     */
    List<UserResponse> getUsersByStatus(UserStatus status);
    
    /**
     * Checks if a username is available.
     *
     * @param username the username to check
     * @return true if username is available, false otherwise
     */
    boolean isUsernameAvailable(String username);
    
    /**
     * Checks if an email is available.
     *
     * @param email the email to check
     * @return true if email is available, false otherwise
     */
    boolean isEmailAvailable(String email);
}
