package com.fabricmanagement.fiber.application.service;

import com.fabricmanagement.fiber.api.dto.request.CreateBlendFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.CreateFiberRequest;
import com.fabricmanagement.fiber.api.dto.request.UpdateFiberPropertyRequest;
import com.fabricmanagement.fiber.api.dto.response.FiberResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberSummaryResponse;
import com.fabricmanagement.fiber.api.dto.response.FiberValidationResponse;
import com.fabricmanagement.fiber.application.mapper.FiberMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;
import com.fabricmanagement.fiber.infrastructure.messaging.FiberEventPublisher;
import com.fabricmanagement.fiber.infrastructure.repository.FiberRepository;
import com.fabricmanagement.shared.domain.exception.*;
import com.fabricmanagement.shared.infrastructure.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService {
    
    private final FiberRepository fiberRepository;
    private final FiberMapper fiberMapper;
    private final FiberEventPublisher eventPublisher;
    
    @Transactional
    public UUID createFiber(CreateFiberRequest request) {
        log.info("Creating fiber: code={}", request.getCode());
        
        if (fiberRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Fiber code already exists: " + request.getCode());
        }
        
        Fiber fiber = fiberMapper.fromCreateRequest(request);
        fiber = fiberRepository.save(fiber);
        
        eventPublisher.publishFiberDefined(fiber);
        
        log.info("Fiber created: id={}, code={}", fiber.getId(), fiber.getCode());
        return fiber.getId();
    }
    
    @Transactional
    public UUID createBlendFiber(CreateBlendFiberRequest request) {
        log.info("Creating blend fiber: code={}", request.getCode());
        
        if (fiberRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Fiber code already exists: " + request.getCode());
        }
        
        validateBlendComposition(request);
        
        Fiber fiber = fiberMapper.fromCreateBlendRequest(request);
        fiber = fiberRepository.save(fiber);
        
        eventPublisher.publishFiberDefined(fiber);
        
        log.info("Blend fiber created: id={}, code={}", fiber.getId(), fiber.getCode());
        return fiber.getId();
    }
    
    @Transactional
    public void updateFiberProperty(UUID fiberId, UpdateFiberPropertyRequest request) {
        log.info("Updating fiber property: fiberId={}", fiberId);
        
        Fiber fiber = fiberRepository.findById(fiberId)
                .orElseThrow(() -> new FiberNotFoundException("Fiber not found: " + fiberId));
        
        if (fiber.getIsDefault()) {
            throw new ForbiddenException("Cannot update default fiber: " + fiber.getCode());
        }
        
        if (request.getSustainabilityType() != null) {
            fiber.setSustainabilityType(SustainabilityType.valueOf(request.getSustainabilityType()));
        }
        
        if (fiber.getProperty() == null) {
            fiber.setProperty(new FiberProperty());
        }
        
        if (request.getStapleLength() != null) {
            fiber.getProperty().setStapleLength(request.getStapleLength());
        }
        if (request.getFineness() != null) {
            fiber.getProperty().setFineness(request.getFineness());
        }
        if (request.getTenacity() != null) {
            fiber.getProperty().setTenacity(request.getTenacity());
        }
        if (request.getMoistureRegain() != null) {
            fiber.getProperty().setMoistureRegain(request.getMoistureRegain());
        }
        if (request.getColor() != null) {
            fiber.getProperty().setColor(request.getColor());
        }
        
        fiberRepository.save(fiber);
        eventPublisher.publishFiberUpdated(fiber);
        
        log.info("Fiber property updated: fiberId={}", fiberId);
    }
    
    @Transactional
    public void deactivateFiber(UUID fiberId) {
        log.info("Deactivating fiber: fiberId={}", fiberId);
        
        Fiber fiber = fiberRepository.findById(fiberId)
                .orElseThrow(() -> new FiberNotFoundException("Fiber not found: " + fiberId));
        
        if (fiber.getIsDefault()) {
            throw new ForbiddenException("Cannot deactivate default fiber: " + fiber.getCode());
        }
        
        fiber.setStatus(FiberStatus.INACTIVE);
        fiberRepository.save(fiber);
        
        eventPublisher.publishFiberDeactivated(fiber);
        
        log.info("Fiber deactivated: fiberId={}", fiberId);
    }
    
    @Transactional(readOnly = true)
    public FiberResponse getFiber(UUID fiberId) {
        Fiber fiber = fiberRepository.findById(fiberId)
                .orElseThrow(() -> new FiberNotFoundException("Fiber not found: " + fiberId));
        
        return fiberMapper.toResponse(fiber);
    }
    
    @Transactional(readOnly = true)
    public Page<FiberSummaryResponse> listFibers(Pageable pageable) {
        // Note: Using findAll without JOIN FETCH for pagination (components not needed for summary)
        // If components are needed, use manual pagination with findAllWithComponents()
        Page<Fiber> fibers = fiberRepository.findAll(pageable);
        return fibers.map(fiberMapper::toSummaryResponse);
    }
    
    @Transactional(readOnly = true)
    public List<FiberResponse> getDefaultFibers() {
        List<Fiber> defaultFibers = fiberRepository.findByIsDefaultTrue();
        return defaultFibers.stream()
                .map(fiberMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<FiberSummaryResponse> searchFibers(String query) {
        List<Fiber> fibers = fiberRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(query, query);
        return fibers.stream()
                .map(fiberMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<FiberSummaryResponse> getFibersByCategory(String category) {
        FiberCategory fiberCategory = FiberCategory.valueOf(category);
        List<Fiber> fibers = fiberRepository.findByCategory(fiberCategory);
        return fibers.stream()
                .map(fiberMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public FiberValidationResponse validateComposition(List<String> fiberCodes) {
        List<String> activeFibers = new ArrayList<>();
        List<String> inactiveFibers = new ArrayList<>();
        List<String> notFoundFibers = new ArrayList<>();
        
        for (String code : fiberCodes) {
            if (fiberRepository.existsByCodeAndStatus(code, FiberStatus.ACTIVE)) {
                activeFibers.add(code);
            } else if (fiberRepository.existsByCode(code)) {
                inactiveFibers.add(code);
            } else {
                notFoundFibers.add(code);
            }
        }
        
        boolean valid = inactiveFibers.isEmpty() && notFoundFibers.isEmpty();
        
        return FiberValidationResponse.builder()
                .valid(valid)
                .activeFibers(activeFibers)
                .inactiveFibers(inactiveFibers)
                .notFoundFibers(notFoundFibers)
                .message(valid ? "All fibers are valid" : "Some fibers are invalid")
                .build();
    }
    
    @Transactional(readOnly = true)
    public Map<String, FiberResponse> getFibersBatch(List<String> fiberCodes) {
        List<Fiber> fibers = fiberRepository.findByCodeIn(fiberCodes);
        
        Map<String, FiberResponse> result = new HashMap<>();
        for (Fiber fiber : fibers) {
            result.put(fiber.getCode(), fiberMapper.toResponse(fiber));
        }
        
        return result;
    }
    
    private void validateBlendComposition(CreateBlendFiberRequest request) {
        if (request.getComponents().size() < 2) {
            throw new InvalidCompositionException("Blend must have at least 2 components");
        }
        
        BigDecimal total = request.getComponents().stream()
                .map(c -> c.getPercentage())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (total.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new InvalidCompositionException(
                    "Total percentage must equal 100, but was: " + total);
        }
        
        long uniqueCount = request.getComponents().stream()
                .map(c -> c.getFiberCode())
                .distinct()
                .count();
        
        if (uniqueCount != request.getComponents().size()) {
            String duplicate = request.getComponents().stream()
                    .map(c -> c.getFiberCode())
                    .filter(code -> request.getComponents().stream()
                            .filter(c -> c.getFiberCode().equals(code))
                            .count() > 1)
                    .findFirst()
                    .orElse("UNKNOWN");
            throw new InvalidCompositionException("Duplicate fiber code in composition: " + duplicate);
        }
        
        for (var component : request.getComponents()) {
            if (!fiberRepository.existsByCodeAndStatus(component.getFiberCode(), FiberStatus.ACTIVE)) {
                if (fiberRepository.existsByCode(component.getFiberCode())) {
                    throw new InactiveFiberException("Component fiber is inactive: " + component.getFiberCode());
                } else {
                    throw new FiberNotFoundException("Component fiber not found: " + component.getFiberCode());
                }
            }
        }
    }
}
