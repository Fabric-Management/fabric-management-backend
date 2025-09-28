package com.fabricmanagement.identity.domain.repository;

import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.valueobject.ContactId;
import com.fabricmanagement.identity.domain.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserContact value object.
 */
public interface UserContactRepository {

    Optional<UserContact> findById(ContactId id);

    List<UserContact> findByUserId(UserId userId);

    Optional<UserContact> findByUserIdAndValue(UserId userId, String value);

    Optional<UserContact> findByUserIdAndPrimary(UserId userId, boolean primary);

    UserContact save(UserContact contact);

    void deleteById(ContactId id);

    boolean existsByValue(String value);

    boolean existsByUserIdAndValue(UserId userId, String value);
}