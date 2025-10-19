package com.fabricmanagement.fiber.infrastructure.repository;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.FiberCategory;
import com.fabricmanagement.fiber.domain.valueobject.FiberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fiber Repository
 * 
 * Note: All queries automatically exclude soft-deleted records via @SQLRestriction on Fiber entity.
 * No need for "AndDeletedFalse" suffixes - Hibernate handles it.
 */
@Repository
public interface FiberRepository extends JpaRepository<Fiber, UUID> {
    
    boolean existsByCode(String code);
    
    boolean existsByCodeAndStatus(String code, FiberStatus status);
    
    Optional<Fiber> findByCode(String code);
    
    List<Fiber> findByIsDefaultTrue();
    
    List<Fiber> findByCategory(FiberCategory category);
    
    List<Fiber> findByStatus(FiberStatus status);
    
    List<Fiber> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String code, String name);
}

