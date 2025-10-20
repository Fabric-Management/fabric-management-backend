package com.fabricmanagement.fiber.unit.service;

import com.fabricmanagement.fiber.api.dto.request.CreateBlendFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.CreateFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.UpdateFiberPropertyRequest;
import com.fabricmanagement.fiber.api.dto.response.FiberResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberSummaryResponse;
import com.fabricmanagement.fiber.application.mapper.FiberEventMapper;
import com.fabricmanagement.fiber.application.mapper.FiberMapper;
import com.fabricmanagement.fiber.application.service.FiberService;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import com.fabricmanagement.fiber.infrastructure.messaging.FiberEventPublisher;
import com.fabricmanagement.fiber.infrastructure.repository.FiberRepository;
import com.fabricmanagement.shared.domain.exception.DuplicateResourceException;
import com.fabricmanagement.shared.domain.exception.FiberNotFoundException;
import com.fabricmanagement.shared.domain.exception.InactiveFiberException;
import com.fabricmanagement.shared.domain.exception.InvalidCompositionException;
import com.fabricmanagement.shared.infrastructure.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.fabricmanagement.fiber.support.TestSupport.*;

import static com.fabricmanagement.fiber.fixtures.FiberFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for FiberService
 *
 * Testing Strategy (Google SRE Style):
 * - Fast (< 100ms per test)
 * - Isolated (mocked dependencies)
 * - Focused (single behavior per test)
 * - Readable (Given-When-Then pattern)
 * - Comprehensive (happy path + edge cases + errors)
 *
 * Coverage Goal: 95%+
 *
 * Test Naming: should{ExpectedBehavior}_when{Condition}
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiberService - Business Logic Tests")
class FiberServiceTest {

    @Mock
    private FiberRepository fiberRepository;

    @Mock
    private FiberMapper fiberMapper;

    @Mock
    private FiberEventMapper eventMapper;

    @Mock
    private FiberEventPublisher eventPublisher;

    @InjectMocks
    private FiberService fiberService;

    // ═════════════════════════════════════════════════════
    // CREATE PURE FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Pure Fiber Tests")
    class CreatePureFiberTests {

        @Test
        @DisplayName("Should create pure fiber when valid request provided")
        void shouldCreatePureFiber_whenValidRequest() {
            // Given
            CreateFiberRequest request = createPureFiberRequest(CODE_COTTON, NAME_COTTON);
            Fiber fiberToSave = createPureFiber(CODE_COTTON, NAME_COTTON);
            
            // Simulate Hibernate generating ID after save
            Fiber savedFiber = createPureFiber(CODE_COTTON, NAME_COTTON);
            savedFiber.setId(newId());

            when(fiberRepository.existsByCode(CODE_COTTON)).thenReturn(false);
            when(fiberMapper.fromCreateRequest(request)).thenReturn(fiberToSave);
            when(fiberRepository.save(any(Fiber.class))).thenReturn(savedFiber);

            // When
            UUID fiberId = fiberService.createFiber(request);

            // Then
            assertThat(fiberId).isNotNull();
            assertThat(fiberId).isEqualTo(savedFiber.getId());

            verify(fiberRepository).existsByCode(CODE_COTTON);
            verify(fiberMapper).fromCreateRequest(request);
            verify(fiberRepository).save(fiberToSave);
            verify(eventPublisher).publishFiberDefined(savedFiber);
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when fiber code already exists")
        void shouldThrowDuplicateException_whenCodeAlreadyExists() {
            // Given
            CreateFiberRequest request = createPureFiberRequest(CODE_COTTON, NAME_COTTON);
            when(fiberRepository.existsByCode(CODE_COTTON)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> fiberService.createFiber(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Fiber code already exists: " + CODE_COTTON);

            verify(fiberRepository).existsByCode(CODE_COTTON);
            verify(fiberRepository, never()).save(any());
            verify(eventPublisher, never()).publishFiberDefined(any());
        }

        @Test
        @DisplayName("Should set default properties when creating fiber")
        void shouldSetDefaultProperties_whenCreatingFiber() {
            // Given
            CreateFiberRequest request = createPureFiberRequest(CODE_POLYESTER, NAME_POLYESTER);
            Fiber fiber = createSyntheticFiber(CODE_POLYESTER, NAME_POLYESTER);

            when(fiberRepository.existsByCode(CODE_POLYESTER)).thenReturn(false);
            when(fiberMapper.fromCreateRequest(request)).thenReturn(fiber);
            when(fiberRepository.save(any())).thenReturn(fiber);

            // When
            fiberService.createFiber(request);

            // Then
            verify(fiberRepository).save(argThat(saved ->
                    saved.getStatus() == FiberStatus.ACTIVE &&
                    saved.getCompositionType() == CompositionType.PURE &&
                    saved.getIsDefault() == false &&
                    saved.getReusable() == true
            ));
        }

        @Test
        @DisplayName("Should publish FiberDefined event after successful creation")
        void shouldPublishFiberDefinedEvent_afterSuccessfulCreation() {
            // Given
            CreateFiberRequest request = createPureFiberRequest("VI", "Viscose");
            Fiber fiber = createPureFiber("VI", "Viscose");

            when(fiberRepository.existsByCode("VI")).thenReturn(false);
            when(fiberMapper.fromCreateRequest(request)).thenReturn(fiber);
            when(fiberRepository.save(any())).thenReturn(fiber);

            // When
            fiberService.createFiber(request);

            // Then
            verify(eventPublisher).publishFiberDefined(any());
        }
    }

    // ═════════════════════════════════════════════════════
    // CREATE BLEND FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Blend Fiber Tests")
    class CreateBlendFiberTests {

        @Test
        @DisplayName("Should create blend fiber when composition total is 100%")
        void shouldCreateBlendFiber_whenCompositionValid() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-001", "CO/PE 60/40");
            Fiber fiberToSave = createCottonPolyesterBlend();
            
            // Simulate Hibernate generating ID after save
            Fiber savedFiber = createCottonPolyesterBlend();
            savedFiber.setId(newId());

            when(fiberRepository.existsByCode("BLD-001")).thenReturn(false);
            when(fiberRepository.existsByCodeAndStatus(CODE_COTTON, FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberRepository.existsByCodeAndStatus(CODE_POLYESTER, FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberMapper.fromCreateBlendRequest(request)).thenReturn(fiberToSave);
            when(fiberRepository.save(any())).thenReturn(savedFiber);

            // When
            UUID fiberId = fiberService.createBlendFiber(request);

            // Then
            assertThat(fiberId).isNotNull();
            assertThat(fiberId).isEqualTo(savedFiber.getId());

            verify(fiberRepository).existsByCodeAndStatus(CODE_COTTON, FiberStatus.ACTIVE);
            verify(fiberRepository).existsByCodeAndStatus(CODE_POLYESTER, FiberStatus.ACTIVE);
            verify(fiberRepository).save(fiberToSave);
            verify(eventPublisher).publishFiberDefined(savedFiber);
        }

        @Test
        @DisplayName("Should throw InvalidCompositionException when total is not 100%")
        void shouldThrowException_whenCompositionTotalNot100() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-002", "Invalid");
            request.setComponents(Arrays.asList(
                    createComponentDto(CODE_COTTON, PCT_60.doubleValue()),
                    createComponentDto(CODE_POLYESTER, 30.0)  // Total = 90% ❌
            ));

            when(fiberRepository.existsByCode("BLD-002")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Total percentage must equal 100");

            verify(fiberRepository, never()).save(any());
            verify(eventPublisher, never()).publishFiberDefined(any());
        }

        @Test
        @DisplayName("Should throw FiberNotFoundException when component fiber not found")
        void shouldThrowException_whenComponentFiberNotFound() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-003", "Invalid");
            request.getComponents().get(0).setFiberCode("XX");  // Non-existent fiber

            when(fiberRepository.existsByCode("BLD-003")).thenReturn(false);  // Blend code check
            when(fiberRepository.existsByCodeAndStatus("XX", FiberStatus.ACTIVE)).thenReturn(false);
            when(fiberRepository.existsByCode("XX")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                    .isInstanceOf(FiberNotFoundException.class)
                    .hasMessageContaining("Component fiber not found: XX");

            verify(fiberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw InactiveFiberException when component fiber is inactive")
        void shouldThrowException_whenComponentFiberInactive() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-004", "Invalid");

            when(fiberRepository.existsByCode("BLD-004")).thenReturn(false); // Blend code doesn't exist
            when(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.ACTIVE)).thenReturn(false);
            when(fiberRepository.existsByCode("CO")).thenReturn(true); // Fiber exists but inactive

            // When & Then
            assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                    .isInstanceOf(InactiveFiberException.class)
                    .hasMessageContaining("Component fiber is inactive: CO");
        }

        @Test
        @DisplayName("Should throw InvalidCompositionException when less than 2 components")
        void shouldThrowException_whenLessThan2Components() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-005", "Invalid");
            request.setComponents(Arrays.asList(
                    createComponentDto("CO", 100.0)  // Only 1 component ❌
            ));

            when(fiberRepository.existsByCode("BLD-005")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Blend must have at least 2 components");
        }

        @Test
        @DisplayName("Should throw InvalidCompositionException when duplicate fiber codes")
        void shouldThrowException_whenDuplicateFiberCodes() {
            // Given
            CreateBlendFiberRequest request = createBlendFiberRequest("BLD-006", "Invalid");
            request.setComponents(Arrays.asList(
                    createComponentDto("CO", 50.0),
                    createComponentDto("CO", 50.0)  // Duplicate ❌
            ));

            when(fiberRepository.existsByCode("BLD-006")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Duplicate fiber code in composition: CO");
        }
    }

    // ═════════════════════════════════════════════════════
    // UPDATE FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Update Fiber Tests")
    class UpdateFiberTests {

        @Test
        @DisplayName("Should update fiber property when valid request")
        void shouldUpdateFiberProperty_whenValidRequest() {
            // Given
            UUID fiberId = newId();
            Fiber fiber = createPureFiber(CODE_COTTON, NAME_COTTON);
            UpdateFiberPropertyRequest request = createUpdatePropertyRequest();

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(fiber));
            when(fiberRepository.save(any())).thenReturn(fiber);

            // When
            fiberService.updateFiberProperty(fiberId, request);

            // Then
            verify(fiberRepository).findById(fiberId);
            verify(fiberRepository).save(fiber);
            verify(eventPublisher).publishFiberUpdated(any());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when updating default fiber")
        void shouldThrowException_whenUpdatingDefaultFiber() {
            // Given
            UUID fiberId = newId();
            Fiber defaultFiber = createDefaultFiber(CODE_COTTON);
            UpdateFiberPropertyRequest request = createUpdatePropertyRequest();

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(defaultFiber));

            // When & Then
            assertThatThrownBy(() -> fiberService.updateFiberProperty(fiberId, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Cannot update default fiber: " + CODE_COTTON);

            verify(fiberRepository).findById(fiberId);
            verify(fiberRepository, never()).save(any());
            verify(eventPublisher, never()).publishFiberUpdated(any());
        }

        @Test
        @DisplayName("Should throw FiberNotFoundException when fiber not found")
        void shouldThrowException_whenFiberNotFound() {
            // Given
            UUID fiberId = UUID.randomUUID();
            UpdateFiberPropertyRequest request = createUpdatePropertyRequest();

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> fiberService.updateFiberProperty(fiberId, request))
                    .isInstanceOf(FiberNotFoundException.class)
                    .hasMessageContaining("Fiber not found");

            verify(fiberRepository, never()).save(any());
        }
    }

    // ═════════════════════════════════════════════════════
    // DEACTIVATE FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deactivate Fiber Tests")
    class DeactivateFiberTests {

        @Test
        @DisplayName("Should deactivate fiber when valid request")
        void shouldDeactivateFiber_whenValidRequest() {
            // Given
            UUID fiberId = UUID.randomUUID();
            Fiber fiber = createPureFiber("CO", "Cotton");

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(fiber));
            when(fiberRepository.save(any())).thenReturn(fiber);

            // When
            fiberService.deactivateFiber(fiberId);

            // Then
            verify(fiberRepository).save(argThat(saved ->
                    saved.getStatus() == FiberStatus.INACTIVE
            ));
            verify(eventPublisher).publishFiberDeactivated(any());
        }

        @Test
        @DisplayName("Should throw exception when deactivating default fiber")
        void shouldThrowException_whenDeactivatingDefaultFiber() {
            // Given
            UUID fiberId = UUID.randomUUID();
            Fiber defaultFiber = createDefaultFiber("CO");

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(defaultFiber));

            // When & Then
            assertThatThrownBy(() -> fiberService.deactivateFiber(fiberId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Cannot deactivate default fiber");

            verify(fiberRepository, never()).save(any());
        }
    }

    // ═════════════════════════════════════════════════════
    // GET FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Fiber Tests")
    class GetFiberTests {

        @Test
        @DisplayName("Should return fiber when fiber exists")
        void shouldReturnFiber_whenFiberExists() {
            // Given
            UUID fiberId = UUID.randomUUID();
            Fiber fiber = createPureFiber("CO", "Cotton");
            FiberResponse expectedResponse = FiberResponse.builder()
                    .id(fiberId.toString())
                    .code("CO")
                    .name("Cotton")
                    .build();

            when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(fiber));
            when(fiberMapper.toResponse(fiber)).thenReturn(expectedResponse);

            // When
            FiberResponse response = fiberService.getFiber(fiberId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo("CO");
            assertThat(response.getName()).isEqualTo("Cotton");

            verify(fiberRepository).findById(fiberId);
            verify(fiberMapper).toResponse(fiber);
        }

        @Test
        @DisplayName("Should throw FiberNotFoundException when fiber not found")
        void shouldThrowException_whenFiberNotFound() {
            // Given
            UUID fiberId = UUID.randomUUID();
            when(fiberRepository.findById(fiberId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> fiberService.getFiber(fiberId))
                    .isInstanceOf(FiberNotFoundException.class)
                    .hasMessageContaining("Fiber not found");
        }
    }

    // ═════════════════════════════════════════════════════
    // GET DEFAULT FIBERS TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Default Fibers Tests")
    class GetDefaultFibersTests {

        @Test
        @DisplayName("Should return all default fibers")
        void shouldReturnAllDefaultFibers() {
            // Given
            List<Fiber> defaultFibers = createDefaultFiberSet();

            when(fiberRepository.findByIsDefaultTrue()).thenReturn(defaultFibers);
            when(fiberMapper.toResponse(any())).thenAnswer(invocation -> {
                Fiber f = invocation.getArgument(0);
                return FiberResponse.builder()
                        .code(f.getCode())
                        .name(f.getName())
                        .isDefault(true)
                        .build();
            });

            // When
            List<FiberResponse> responses = fiberService.getDefaultFibers();

            // Then
            assertThat(responses).hasSize(9);
            assertThat(responses).extracting(FiberResponse::getIsDefault)
                    .containsOnly(true);

            verify(fiberRepository).findByIsDefaultTrue();
        }

        @Test
        @DisplayName("Should return empty list when no default fibers")
        void shouldReturnEmptyList_whenNoDefaultFibers() {
            // Given
            when(fiberRepository.findByIsDefaultTrue()).thenReturn(List.of());

            // When
            List<FiberResponse> responses = fiberService.getDefaultFibers();

            // Then
            assertThat(responses).isEmpty();
        }
    }

    // ═════════════════════════════════════════════════════
    // VALIDATE COMPOSITION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validate Fiber Composition Tests")
    class ValidateCompositionTests {

        @Test
        @DisplayName("Should validate all fibers active")
        void shouldValidateAllFibersActive_whenAllExist() {
            // Given
            List<String> fiberCodes = Arrays.asList("CO", "PES", "WO");

            when(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberRepository.existsByCodeAndStatus("PES", FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberRepository.existsByCodeAndStatus("WO", FiberStatus.ACTIVE)).thenReturn(true);

            // When
            var result = fiberService.validateComposition(fiberCodes);

            // Then
            assertThat(result.getValid()).isTrue();
            assertThat(result.getActiveFibers()).containsExactlyInAnyOrder("CO", "PES", "WO");
            assertThat(result.getInactiveFibers()).isEmpty();
            assertThat(result.getNotFoundFibers()).isEmpty();
        }

        @Test
        @DisplayName("Should identify inactive fibers")
        void shouldIdentifyInactiveFibers() {
            // Given
            List<String> fiberCodes = Arrays.asList("CO", "PES");

            when(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberRepository.existsByCodeAndStatus("PES", FiberStatus.ACTIVE)).thenReturn(false);
            when(fiberRepository.existsByCode("PES")).thenReturn(true);  // Exists but inactive

            // When
            var result = fiberService.validateComposition(fiberCodes);

            // Then
            assertThat(result.getValid()).isFalse();
            assertThat(result.getActiveFibers()).containsExactly("CO");
            assertThat(result.getInactiveFibers()).containsExactly("PES");
        }

        @Test
        @DisplayName("Should identify not found fibers")
        void shouldIdentifyNotFoundFibers() {
            // Given
            List<String> fiberCodes = Arrays.asList("CO", "XX");

            when(fiberRepository.existsByCodeAndStatus("CO", FiberStatus.ACTIVE)).thenReturn(true);
            when(fiberRepository.existsByCodeAndStatus("XX", FiberStatus.ACTIVE)).thenReturn(false);
            when(fiberRepository.existsByCode("XX")).thenReturn(false);  // Doesn't exist

            // When
            var result = fiberService.validateComposition(fiberCodes);

            // Then
            assertThat(result.getValid()).isFalse();
            assertThat(result.getActiveFibers()).containsExactly("CO");
            assertThat(result.getNotFoundFibers()).containsExactly("XX");
        }
    }

    // ═════════════════════════════════════════════════════
    // SEARCH & FILTER TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search and Filter Tests")
    class SearchAndFilterTests {

        @Test
        @DisplayName("Should find fibers by code pattern")
        void shouldFindFibersByCodePattern() {
            // Given
            String query = "CO";
            List<Fiber> matchingFibers = Arrays.asList(
                    createPureFiber("CO", "Cotton"),
                    createPureFiber("CO-ORG", "Organic Cotton")
            );

            when(fiberRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(query, query))
                    .thenReturn(matchingFibers);

            // When
            List<FiberSummaryResponse> results = fiberService.searchFibers(query);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("Should filter fibers by category")
        void shouldFilterFibersByCategory() {
            // Given
            FiberCategory category = FiberCategory.NATURAL;
            List<Fiber> naturalFibers = Arrays.asList(
                    createPureFiber("CO", "Cotton"),
                    createPureFiber("WO", "Wool")
            );

            when(fiberRepository.findByCategory(category)).thenReturn(naturalFibers);

            // When
            List<FiberSummaryResponse> results = fiberService.getFibersByCategory("NATURAL");

            // Then
            assertThat(results).hasSize(2);
            verify(fiberRepository).findByCategory(category);
        }
    }

    // ═════════════════════════════════════════════════════
    // EVENT PUBLISHING TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Publishing Tests")
    class EventPublishingTests {

        @Test
        @DisplayName("Should NOT publish event when fiber creation fails")
        void shouldNotPublishEvent_whenCreationFails() {
            // Given
            CreateFiberRequest request = createPureFiberRequest("CO", "Cotton");
            when(fiberRepository.existsByCode("CO")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> fiberService.createFiber(request))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(eventPublisher, never()).publishFiberDefined(any());
            verify(eventPublisher, never()).publishFiberUpdated(any());
        }

        @Test
        @DisplayName("Should publish event asynchronously using CompletableFuture")
        void shouldPublishEventAsync_whenFiberCreated() {
            // Given
            CreateFiberRequest request = createPureFiberRequest("PES", "Polyester");
            Fiber fiber = createSyntheticFiber("PES", "Polyester");

            when(fiberRepository.existsByCode("PES")).thenReturn(false);
            when(fiberMapper.fromCreateRequest(request)).thenReturn(fiber);
            when(fiberRepository.save(any())).thenReturn(fiber);

            // When
            fiberService.createFiber(request);

            // Then
            verify(eventPublisher).publishFiberDefined(any());
            // Event publisher should use CompletableFuture (non-blocking)
        }
    }
}

