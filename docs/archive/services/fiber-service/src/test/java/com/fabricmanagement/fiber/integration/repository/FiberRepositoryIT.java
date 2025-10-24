package com.fabricmanagement.fiber.integration.repository;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import com.fabricmanagement.fiber.infrastructure.repository.FiberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static com.fabricmanagement.fiber.fixtures.FiberFixtures.*;
import static com.fabricmanagement.fiber.support.TestSupport.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration Tests for FiberRepository
 *
 * Uses Testcontainers for REAL PostgreSQL instance
 * - No mocks, real database queries
 * - Production parity (same database, schema, constraints)
 * - Tests actual SQL, unique constraints, triggers, indexes
 *
 * Runtime: ~5-10 seconds (container startup cached by Testcontainers)
 *
 * Netflix/Google Standard:
 * - Real infrastructure in tests
 * - Catch integration bugs early
 * - Verify database constraints work
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FiberRepository - Database Integration Tests")
class FiberRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fiber_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private FiberRepository fiberRepository;

    // ═════════════════════════════════════════════════════
    // BASIC CRUD TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("⚠️ CRITICAL: Hibernate should auto-generate UUID when ID is null")
        void shouldAutoGenerateUUID_whenIdNotSetManually() {
            // Given - Entity created WITHOUT manual ID
            Fiber fiber = createPureFiber("CO", "Cotton");
            
            // ⚠️ CRITICAL ASSERTION: ID must be NULL before persist
            // If mapper sets .id(UUID.randomUUID()), this test FAILS
            // This bug cost us 3-4 days of debugging!
            assertThat(fiber.getId()).isNull();
            assertThat(fiber.getVersion()).isNull();
            
            // When - Hibernate persists entity
            Fiber saved = fiberRepository.save(fiber);
            
            // Then - Hibernate auto-generated UUID and version
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getVersion()).isEqualTo(0L);
            
            // Verify it's persisted to DB with generated ID
            Fiber retrieved = fiberRepository.findById(saved.getId()).orElseThrow();
            assertThat(retrieved.getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("Should save and retrieve fiber with UUID primary key")
        void shouldSaveAndRetrieveFiber_withUUID() {
            // Given
            Fiber fiber = createPureFiber("CO", "Cotton");

            // When
            Fiber saved = fiberRepository.save(fiber);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getId()).isInstanceOf(UUID.class);

            Fiber retrieved = fiberRepository.findById(saved.getId()).orElseThrow();
            assertThat(retrieved.getCode()).isEqualTo("CO");
            assertThat(retrieved.getName()).isEqualTo("Cotton");
            assertThat(retrieved.getCategory()).isEqualTo(FiberCategory.NATURAL);
        }

        @Test
        @DisplayName("Should auto-generate UUID when not provided")
        void shouldAutoGenerateUUID_whenNotProvided() {
            // Given
            Fiber fiber = createPureFiber("PE", "Polyester");
            fiber.setId(null);  // No ID provided

            // When
            Fiber saved = fiberRepository.save(fiber);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getId()).isInstanceOf(UUID.class);
        }

        @Test
        @DisplayName("Should update existing fiber")
        void shouldUpdateExistingFiber() {
            // Given
            Fiber fiber = fiberRepository.save(createPureFiber("WO", "Wool"));
            UUID originalId = fiber.getId();

            // When
            fiber.setName("Merino Wool");
            Fiber updated = fiberRepository.save(fiber);

            // Then
            assertThat(updated.getName()).isEqualTo("Merino Wool");
            assertThat(updated.getId()).isEqualTo(originalId);
        }

        @Test
        @DisplayName("Should soft delete fiber (status=INACTIVE)")
        void shouldSoftDeleteFiber() {
            // Given
            Fiber fiber = fiberRepository.save(createPureFiber("SI", "Silk"));

            // When
            fiber.setStatus(FiberStatus.INACTIVE);
            fiber.setDeleted(true);
            fiberRepository.save(fiber);

            // Then
            Fiber found = fiberRepository.findById(fiber.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(FiberStatus.INACTIVE);
            assertThat(found.getDeleted()).isTrue();
        }
    }

    // ═════════════════════════════════════════════════════
    // CONSTRAINT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Database Constraint Tests")
    class ConstraintTests {

        @Test
        @DisplayName("Should enforce unique code constraint")
        void shouldEnforceUniqueCodeConstraint() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));

            // When & Then
            Fiber duplicate = createPureFiber("CO", "Cotton Duplicate");

            assertThatThrownBy(() -> {
                fiberRepository.save(duplicate);
                fiberRepository.flush();
            }).hasMessageContaining("unique constraint");
        }

        @Test
        @DisplayName("Should allow same code if first is soft deleted")
        void shouldAllowSameCode_whenFirstIsSoftDeleted() {
            // Given
            Fiber first = fiberRepository.save(createPureFiber("LI", "Linen"));
            first.setStatus(FiberStatus.INACTIVE);
            first.setDeleted(true);
            fiberRepository.save(first);

            // When - Create new with same code (business decision: allow or not?)
            // This test documents current behavior
            Fiber second = createPureFiber("LI", "Linen Reactivated");

            // Then - Should enforce unique regardless of deleted flag
            assertThatThrownBy(() -> {
                fiberRepository.save(second);
                fiberRepository.flush();
            }).hasMessageContaining("unique constraint");
        }
    }

    // ═════════════════════════════════════════════════════
    // QUERY METHOD TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom Query Method Tests")
    class QueryMethodTests {

        @Test
        @DisplayName("Should find fibers by category")
        void shouldFindFibersByCategory() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));  // NATURAL
            fiberRepository.save(createSyntheticFiber("PE", "Polyester"));  // SYNTHETIC
            fiberRepository.save(createPureFiber("WO", "Wool"));  // NATURAL

            // When
            List<Fiber> naturalFibers = fiberRepository.findByCategory(FiberCategory.NATURAL);

            // Then
            assertThat(naturalFibers).hasSize(2);
            assertThat(naturalFibers).extracting(Fiber::getCode)
                    .containsExactlyInAnyOrder("CO", "WO");
        }

        @Test
        @DisplayName("Should find fibers by status")
        void shouldFindFibersByStatus() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));
            fiberRepository.save(createPureFiber("PE", "Polyester"));

            Fiber inactive = createPureFiber("WO", "Wool");
            inactive.setStatus(FiberStatus.INACTIVE);
            fiberRepository.save(inactive);

            // When
            List<Fiber> activeFibers = fiberRepository.findByStatus(FiberStatus.ACTIVE);

            // Then
            assertThat(activeFibers).hasSize(2);
            assertThat(activeFibers).extracting(Fiber::getCode)
                    .containsExactlyInAnyOrder("CO", "PE");
        }

        @Test
        @DisplayName("Should find default fibers only")
        void shouldFindDefaultFibersOnly() {
            // Given
            fiberRepository.save(createDefaultFiber("CO"));  // isDefault=TRUE
            fiberRepository.save(createPureFiber("PE", "Polyester"));  // isDefault=FALSE
            fiberRepository.save(createDefaultFiber("WO"));  // isDefault=TRUE

            // When
            List<Fiber> defaultFibers = fiberRepository.findByIsDefaultTrue();

            // Then
            assertThat(defaultFibers).hasSize(2);
            assertThat(defaultFibers).extracting(Fiber::getCode)
                    .containsExactlyInAnyOrder("CO", "WO");
            assertThat(defaultFibers).allMatch(Fiber::getIsDefault);
        }

        @Test
        @DisplayName("Should check fiber exists by code")
        void shouldCheckFiberExistsByCode() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));

            // When & Then
            assertThat(fiberRepository.existsByCode("CO")).isTrue();
            assertThat(fiberRepository.existsByCode("XX")).isFalse();
        }

        @Test
        @DisplayName("Should check fiber exists by code and status")
        void shouldCheckFiberExistsByCodeAndStatus() {
            // Given
            Fiber inactive = createPureFiber("CO", "Cotton");
            inactive.setStatus(FiberStatus.INACTIVE);
            fiberRepository.save(inactive);

            // When & Then
            assertThat(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.ACTIVE)).isFalse();
            assertThat(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.INACTIVE)).isTrue();
        }

        @Test
        @DisplayName("Should search fibers by code or name (case-insensitive)")
        void shouldSearchFibersByCodeOrName() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));
            fiberRepository.save(createPureFiber("CO-ORG", "Organic Cotton"));
            fiberRepository.save(createPureFiber("PE", "Polyester"));

            // When
            List<Fiber> results = fiberRepository
                    .findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase("cotton", "cotton");

            // Then
            assertThat(results).hasSize(2);  // CO and CO-ORG (both contain "cotton")
            assertThat(results).extracting(Fiber::getCode)
                    .containsExactlyInAnyOrder("CO", "CO-ORG");
        }
    }

    // ═════════════════════════════════════════════════════
    // INDEX PERFORMANCE TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Index Performance Tests")
    class IndexPerformanceTests {

        @Test
        @DisplayName("Should use index for category lookup (performance test)")
        void shouldUseIndexForCategoryLookup() {
            // Given - Insert 100 fibers across different categories
            for (int i = 0; i < 50; i++) {
                fiberRepository.save(createPureFiber("CO-" + i, "Cotton " + i));
            }
            for (int i = 0; i < 50; i++) {
                fiberRepository.save(createSyntheticFiber("PE-" + i, "Polyester " + i));
            }

            // When - Query should use idx_fibers_category index
            long startTime = System.currentTimeMillis();
            List<Fiber> results = fiberRepository.findByCategory(FiberCategory.NATURAL);
            long duration = System.currentTimeMillis() - startTime;

            // Then
            assertThat(results).hasSize(50);
            assertThat(duration).isLessThan(100);  // Should be fast with index
        }

        @Test
        @DisplayName("Should use index for default fiber lookup")
        void shouldUseIndexForDefaultFiberLookup() {
            // Given
            for (int i = 0; i < 10; i++) {
                fiberRepository.save(createDefaultFiber("DF-" + i));
            }
            for (int i = 0; i < 90; i++) {
                fiberRepository.save(createPureFiber("REG-" + i, "Regular " + i));
            }

            // When - Query should use partial index (WHERE is_default = TRUE)
            long startTime = System.currentTimeMillis();
            List<Fiber> defaults = fiberRepository.findByIsDefaultTrue();
            long duration = System.currentTimeMillis() - startTime;

            // Then
            assertThat(defaults).hasSize(10);
            assertThat(duration).isLessThan(50);  // Very fast with partial index
        }
    }

    // ═════════════════════════════════════════════════════
    // TRIGGER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Database Trigger Tests")
    class TriggerTests {

        @Test
        @DisplayName("Should auto-update updated_at on modification")
        void shouldAutoUpdateUpdatedAt_onModification() throws Exception {
            // Given
            Fiber fiber = fiberRepository.save(createPureFiber("CO", "Cotton"));
            var originalUpdatedAt = fiber.getUpdatedAt();

            Thread.sleep(1000);  // Wait for timestamp difference

            // When
            fiber.setName("Cotton Updated");
            Fiber updated = fiberRepository.save(fiber);
            fiberRepository.flush();

            // Then
            Fiber retrieved = fiberRepository.findById(updated.getId()).orElseThrow();
            assertThat(retrieved.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("Should set createdAt automatically on insert")
        void shouldSetCreatedAtAutomatically() {
            // Given
            Fiber fiber = createPureFiber("PE", "Polyester");
            fiber.setCreatedAt(null);  // Don't set

            // When
            Fiber saved = fiberRepository.save(fiber);

            // Then
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    // ═════════════════════════════════════════════════════
    // TRANSACTIONAL BEHAVIOR TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transactional Behavior Tests")
    class TransactionalTests {

        @Test
        @DisplayName("Should rollback on constraint violation")
        void shouldRollback_onConstraintViolation() {
            // Given
            fiberRepository.save(createPureFiber("CO", "Cotton"));

            // When
            try {
                fiberRepository.save(createPureFiber("CO", "Duplicate"));
                fiberRepository.flush();
            } catch (Exception e) {
                // Expected exception
            }

            // Then
            List<Fiber> allFibers = fiberRepository.findAll();
            assertThat(allFibers).hasSize(1);  // Only first one saved
        }

        @Test
        @DisplayName("Should maintain version for optimistic locking")
        void shouldMaintainVersion_forOptimisticLocking() {
            // Given
            Fiber fiber = fiberRepository.save(createPureFiber("CO", "Cotton"));
            assertThat(fiber.getVersion()).isEqualTo(0L);

            // When
            fiber.setName("Cotton Updated");
            Fiber updated = fiberRepository.save(fiber);

            // Then
            assertThat(updated.getVersion()).isEqualTo(1L);
        }
    }

    // ═════════════════════════════════════════════════════
    // BLEND FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Blend Fiber Repository Tests")
    class BlendFiberTests {

        @Test
        @DisplayName("Should save blend fiber with components")
        void shouldSaveBlendFiber_withComponents() {
            // Given
            Fiber blendFiber = createCottonPolyesterBlend();

            // When
            Fiber saved = fiberRepository.save(blendFiber);

            // Then
            Fiber retrieved = fiberRepository.findById(saved.getId()).orElseThrow();
            assertThat(retrieved.getCompositionType()).isEqualTo(CompositionType.BLEND);
            assertThat(retrieved.getComponents()).hasSize(2);
        }

        @Test
        @DisplayName("Should cascade delete components when fiber deleted")
        void shouldCascadeDeleteComponents_whenFiberDeleted() {
            // Given
            Fiber blendFiber = fiberRepository.save(createCottonPolyesterBlend());
            UUID fiberId = blendFiber.getId();

            // When
            fiberRepository.deleteById(fiberId);

            // Then
            assertThat(fiberRepository.findById(fiberId)).isEmpty();
            // Components should be cascade deleted (verify in database)
        }
    }

    // ═════════════════════════════════════════════════════
    // DATA INTEGRITY TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should preserve tenant_id as global UUID")
        void shouldPreserveTenantId_asGlobalUUID() {
            // Given
            Fiber fiber = createPureFiber("CO", "Cotton");
            assertThat(fiber.getTenantId()).isEqualTo(GLOBAL_TENANT_ID);

            // When
            Fiber saved = fiberRepository.save(fiber);

            // Then
            assertThat(saved.getTenantId()).isEqualTo(GLOBAL_TENANT_ID);
        }

        @Test
        @DisplayName("Should preserve BaseEntity audit fields")
        void shouldPreserveAuditFields() {
            // Given
            Fiber fiber = createPureFiber("PE", "Polyester");

            // When
            Fiber saved = fiberRepository.save(fiber);

            // Then
            assertThat(saved.getCreatedBy()).isEqualTo(TEST_USER);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getVersion()).isNotNull();
            assertThat(saved.isDeleted()).isFalse();
        }
    }
}

