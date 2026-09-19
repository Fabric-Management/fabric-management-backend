package com.fabricmanagement.sales.salesorder.infra.repository;

import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Operator-command alias: inherited tests exercise migration, RLS and immutable history together.
 */
@Testcontainers
class OrderCoverRequirementMigrationIT extends RequirementProfileHistoryIT {}
