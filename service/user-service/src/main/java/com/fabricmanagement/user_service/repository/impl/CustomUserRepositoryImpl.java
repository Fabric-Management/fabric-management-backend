package com.fabricmanagement.user_service.repository.impl;

import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import com.fabricmanagement.user_service.repository.CustomUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class CustomUserRepositoryImpl implements CustomUserRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<User> findUsersWithFilters(
            UUID companyId,
            List<Role> roles,
            List<UserStatus> statuses,
            String search,
            Boolean emailVerified,
            Boolean hasPassword,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> user = query.from(User.class);

        List<Predicate> predicates = new ArrayList<>();

        // Always exclude deleted users
        predicates.add(cb.equal(user.get("deleted"), false));

        // Company filter
        if (companyId != null) {
            predicates.add(cb.equal(user.get("companyId"), companyId));
        }

        // Role filter
        if (roles != null && !roles.isEmpty()) {
            Join<User, Role> roleJoin = user.join("roles");
            predicates.add(roleJoin.in(roles));
        }

        // Status filter
        if (statuses != null && !statuses.isEmpty()) {
            predicates.add(user.get("status").in(statuses));
        }

        // Search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(user.get("username")), searchPattern),
                    cb.like(cb.lower(user.get("email")), searchPattern),
                    cb.like(cb.lower(user.get("firstName")), searchPattern),
                    cb.like(cb.lower(user.get("lastName")), searchPattern)
            );
            predicates.add(searchPredicate);
        }

        // Email verified filter
        if (emailVerified != null) {
            predicates.add(cb.equal(user.get("emailVerified"), emailVerified));
        }

        // Has password filter
        if (hasPassword != null) {
            predicates.add(cb.equal(user.get("hasPassword"), hasPassword));
        }

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                if (order.isAscending()) {
                    orders.add(cb.asc(user.get(order.getProperty())));
                } else {
                    orders.add(cb.desc(user.get(order.getProperty())));
                }
            });
            query.orderBy(orders);
        }

        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<User> results = typedQuery.getResultList();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        countQuery.select(cb.countDistinct(countRoot));
        countQuery.where(cb.and(predicates.toArray(new Predicate[0])));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public UserStatistics getUserStatistics(UUID companyId) {
        String baseQuery = "SELECT " +
                "COUNT(u), " +
                "SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN u.emailVerified = true THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN u.hasPassword = true THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN u.status = 'LOCKED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN u.createdAt >= :oneMonthAgo THEN 1 ELSE 0 END) " +
                "FROM User u WHERE u.deleted = false";

        if (companyId != null) {
            baseQuery += " AND u.companyId = :companyId";
        }

        TypedQuery<Object[]> query = entityManager.createQuery(baseQuery, Object[].class);
        query.setParameter("oneMonthAgo", LocalDateTime.now().minusMonths(1));

        if (companyId != null) {
            query.setParameter("companyId", companyId);
        }

        Object[] result = query.getSingleResult();

        return new UserStatistics(
                ((Number) result[0]).longValue(), // totalUsers
                result[1] != null ? ((Number) result[1]).longValue() : 0L, // activeUsers
                result[2] != null ? ((Number) result[2]).longValue() : 0L, // verifiedUsers
                result[3] != null ? ((Number) result[3]).longValue() : 0L, // usersWithPassword
                result[4] != null ? ((Number) result[4]).longValue() : 0L, // lockedUsers
                result[5] != null ? ((Number) result[5]).longValue() : 0L  // newUsersThisMonth
        );
    }

    @Override
    public List<RoleCount> countUsersByRole(UUID companyId) {
        String jpql = "SELECT r, COUNT(DISTINCT u) FROM User u JOIN u.roles r " +
                "WHERE u.deleted = false ";

        if (companyId != null) {
            jpql += "AND u.companyId = :companyId ";
        }

        jpql += "GROUP BY r";

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);

        if (companyId != null) {
            query.setParameter("companyId", companyId);
        }

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(result -> new RoleCount(
                        (Role) result[0],
                        ((Number) result[1]).longValue()
                ))
                .toList();
    }
}