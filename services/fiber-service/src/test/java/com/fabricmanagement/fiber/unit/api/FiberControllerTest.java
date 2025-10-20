package com.fabricmanagement.fiber.unit.api;

import com.fabricmanagement.fiber.api.FiberController;
import com.fabricmanagement.fiber.api.dto.request.CreateBlendFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.CreateFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.FiberComponentDto;
import com.fabricmanagement.fiber.api.dto.request.UpdateFiberPropertyRequest;
import com.fabricmanagement.fiber.api.dto.response.FiberResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberSummaryResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberValidationResponse;
import com.fabricmanagement.fiber.application.service.FiberService;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiberController - Unit Tests")
class FiberControllerTest {

    @Mock
    private FiberService fiberService;

    @InjectMocks
    private FiberController fiberController;

    @Nested
    @DisplayName("Create Fiber Tests")
    class CreateFiberTests {

        @Test
        @DisplayName("Should create fiber and return 201")
        @SuppressWarnings("null")
        void shouldCreateFiber() {
            CreateFiberRequest request = new CreateFiberRequest();
            request.setCode("CO");
            request.setName("Cotton");
            request.setCategory("NATURAL");
            request.setOriginType("PLANT");
            request.setSustainabilityType("ORGANIC");
            request.setReusable(true);

            UUID fiberId = UUID.randomUUID();
            when(fiberService.createFiber(request)).thenReturn(fiberId);

            ResponseEntity<ApiResponse<UUID>> response = fiberController.createFiber(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(fiberId);
            verify(fiberService).createFiber(request);
        }
    }

    @Nested
    @DisplayName("Create Blend Fiber Tests")
    class CreateBlendFiberTests {

        @Test
        @DisplayName("Should create blend fiber and return 201")
        @SuppressWarnings("null")
        void shouldCreateBlendFiber() {
            CreateBlendFiberRequest request = new CreateBlendFiberRequest();
            request.setCode("BLD-001");
            request.setName("Cotton/Polyester");
            request.setOriginType("UNKNOWN");
            request.setReusable(true);
            
            FiberComponentDto comp1 = new FiberComponentDto();
            comp1.setFiberCode("CO");
            comp1.setPercentage(BigDecimal.valueOf(60));
            
            FiberComponentDto comp2 = new FiberComponentDto();
            comp2.setFiberCode("PES");
            comp2.setPercentage(BigDecimal.valueOf(40));
            
            request.setComponents(Arrays.asList(comp1, comp2));

            UUID fiberId = UUID.randomUUID();
            when(fiberService.createBlendFiber(request)).thenReturn(fiberId);

            ResponseEntity<ApiResponse<UUID>> response = fiberController.createBlendFiber(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(fiberService).createBlendFiber(request);
        }
    }

    @Nested
    @DisplayName("Get Fiber Tests")
    class GetFiberTests {

        @Test
        @DisplayName("Should get fiber by ID")
        @SuppressWarnings("null")
        void shouldGetFiber() {
            UUID fiberId = UUID.randomUUID();
            FiberResponse fiberResponse = FiberResponse.builder()
                    .id(fiberId.toString())
                    .code("CO")
                    .name("Cotton")
                    .build();

            when(fiberService.getFiber(fiberId)).thenReturn(fiberResponse);

            ResponseEntity<ApiResponse<FiberResponse>> response = fiberController.getFiber(fiberId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData().getCode()).isEqualTo("CO");
            verify(fiberService).getFiber(fiberId);
        }
    }

    @Nested
    @DisplayName("List Fibers Tests")
    class ListFibersTests {

        @Test
        @DisplayName("Should list fibers with pagination")
        @SuppressWarnings("null")
        void shouldListFibers() {
            Pageable pageable = PageRequest.of(0, 20);
            List<FiberSummaryResponse> fibers = Arrays.asList(
                    FiberSummaryResponse.builder().code("CO").build(),
                    FiberSummaryResponse.builder().code("PES").build()
            );
            Page<FiberSummaryResponse> page = new PageImpl<>(fibers, pageable, 2);

            when(fiberService.listFibers(pageable)).thenReturn(page);

            ResponseEntity<PagedResponse<FiberSummaryResponse>> response = fiberController.listFibers(pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(2);
            verify(fiberService).listFibers(pageable);
        }
    }

    @Nested
    @DisplayName("Update Fiber Tests")
    class UpdateFiberTests {

        @Test
        @DisplayName("Should update fiber property")
        @SuppressWarnings("null")
        void shouldUpdateFiberProperty() {
            UUID fiberId = UUID.randomUUID();
            UpdateFiberPropertyRequest request = new UpdateFiberPropertyRequest();
            request.setStapleLength(BigDecimal.valueOf(30));

            doNothing().when(fiberService).updateFiberProperty(fiberId, request);

            ResponseEntity<ApiResponse<Void>> response = fiberController.updateFiberProperty(fiberId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(fiberService).updateFiberProperty(fiberId, request);
        }
    }

    @Nested
    @DisplayName("Deactivate Fiber Tests")
    class DeactivateFiberTests {

        @Test
        @DisplayName("Should deactivate fiber")
        @SuppressWarnings("null")
        void shouldDeactivateFiber() {
            UUID fiberId = UUID.randomUUID();
            doNothing().when(fiberService).deactivateFiber(fiberId);

            ResponseEntity<ApiResponse<Void>> response = fiberController.deactivateFiber(fiberId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(fiberService).deactivateFiber(fiberId);
        }
    }

    @Nested
    @DisplayName("Get Default Fibers Tests")
    class GetDefaultFibersTests {

        @Test
        @DisplayName("Should get default fibers")
        @SuppressWarnings("null")
        void shouldGetDefaultFibers() {
            List<FiberResponse> defaultFibers = Arrays.asList(
                    FiberResponse.builder().code("CO").isDefault(true).build(),
                    FiberResponse.builder().code("PES").isDefault(true).build()
            );

            when(fiberService.getDefaultFibers()).thenReturn(defaultFibers);

            ResponseEntity<ApiResponse<List<FiberResponse>>> response = fiberController.getDefaultFibers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(2);
            verify(fiberService).getDefaultFibers();
        }
    }

    @Nested
    @DisplayName("Search Fibers Tests")
    class SearchFibersTests {

        @Test
        @DisplayName("Should search fibers by query")
        @SuppressWarnings("null")
        void shouldSearchFibers() {
            List<FiberSummaryResponse> results = Arrays.asList(
                    FiberSummaryResponse.builder().code("CO").build()
            );

            when(fiberService.searchFibers("cotton")).thenReturn(results);

            ResponseEntity<ApiResponse<List<FiberSummaryResponse>>> response = 
                    fiberController.searchFibers("cotton");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(1);
            verify(fiberService).searchFibers("cotton");
        }
    }

    @Nested
    @DisplayName("Get Fibers by Category Tests")
    class GetFibersByCategoryTests {

        @Test
        @DisplayName("Should get fibers by category")
        @SuppressWarnings("null")
        void shouldGetFibersByCategory() {
            List<FiberSummaryResponse> naturalFibers = Arrays.asList(
                    FiberSummaryResponse.builder().code("CO").category("NATURAL").build(),
                    FiberSummaryResponse.builder().code("WO").category("NATURAL").build()
            );

            when(fiberService.getFibersByCategory("NATURAL")).thenReturn(naturalFibers);

            ResponseEntity<ApiResponse<List<FiberSummaryResponse>>> response = 
                    fiberController.getFibersByCategory("NATURAL");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(2);
            verify(fiberService).getFibersByCategory("NATURAL");
        }
    }

    @Nested
    @DisplayName("Validate Composition Tests")
    class ValidateCompositionTests {

        @Test
        @DisplayName("Should validate composition")
        @SuppressWarnings("null")
        void shouldValidateComposition() {
            List<String> fiberCodes = Arrays.asList("CO", "PES");
            FiberValidationResponse validationResponse = FiberValidationResponse.builder()
                    .valid(true)
                    .activeFibers(fiberCodes)
                    .inactiveFibers(Collections.emptyList())
                    .notFoundFibers(Collections.emptyList())
                    .message("All fibers are valid")
                    .build();

            when(fiberService.validateComposition(fiberCodes)).thenReturn(validationResponse);

            ResponseEntity<ApiResponse<FiberValidationResponse>> response = 
                    fiberController.validateFiberComposition(fiberCodes);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData().getValid()).isTrue();
            verify(fiberService).validateComposition(fiberCodes);
        }
    }

    @Nested
    @DisplayName("Get Fibers Batch Tests")
    class GetFibersBatchTests {

        @Test
        @DisplayName("Should get multiple fibers by codes")
        @SuppressWarnings("null")
        void shouldGetFibersBatch() {
            List<String> codes = Arrays.asList("CO", "PES");
            Map<String, FiberResponse> batchResult = new HashMap<>();
            batchResult.put("CO", FiberResponse.builder().code("CO").build());
            batchResult.put("PES", FiberResponse.builder().code("PES").build());

            when(fiberService.getFibersBatch(codes)).thenReturn(batchResult);

            ResponseEntity<ApiResponse<Map<String, FiberResponse>>> response = 
                    fiberController.getFibersBatch(codes);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(2);
            verify(fiberService).getFibersBatch(codes);
        }
    }
}

