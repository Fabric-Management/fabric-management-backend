package com.fabricmanagement.fiber.infrastructure.repository;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.FiberCategory;
import com.fabricmanagement.fiber.domain.valueobject.FiberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    
    @SuppressWarnings("null")
    @Override
    @Query("SELECT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.id = :id")
    Optional<Fiber> findById(@Param("id") UUID id);
    
    @Query("SELECT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.code = :code")
    Optional<Fiber> findByCode(@Param("code") String code);
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.isDefault = true")
    List<Fiber> findByIsDefaultTrue();
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.category = :category")
    List<Fiber> findByCategory(@Param("category") FiberCategory category);
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.status = :status")
    List<Fiber> findByStatus(@Param("status") FiberStatus status);
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components WHERE LOWER(f.code) LIKE LOWER(CONCAT('%', :code, '%')) OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Fiber> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(@Param("code") String code, @Param("name") String name);
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components WHERE f.code IN :codes")
    List<Fiber> findByCodeIn(@Param("codes") List<String> codes);
    
    @Query("SELECT DISTINCT f FROM Fiber f LEFT JOIN FETCH f.components")
    List<Fiber> findAllWithComponents();
}

