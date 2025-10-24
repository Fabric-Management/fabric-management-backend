package com.fabricmanagement.fiber.unit.messaging;

import com.fabricmanagement.fiber.application.mapper.FiberEventMapper;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.event.FiberDeactivatedEvent;
import com.fabricmanagement.fiber.domain.event.FiberDefinedEvent;
import com.fabricmanagement.fiber.domain.event.FiberUpdatedEvent;
import com.fabricmanagement.fiber.domain.valueobject.*;
import com.fabricmanagement.fiber.infrastructure.messaging.FiberEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiberEventPublisher - Unit Tests")
class FiberEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private FiberEventMapper eventMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FiberEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> valueCaptor;

    private Fiber testFiber;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventPublisher, "fiberEventsTopic", "fiber-events");
        
        testFiber = Fiber.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .code("CO")
                .name("Cotton")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.PLANT)
                .sustainabilityType(SustainabilityType.ORGANIC)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .build();
    }

    @Test
    @DisplayName("Should publish FIBER_DEFINED event successfully")
    void shouldPublishFiberDefinedEvent() throws Exception {
        FiberDefinedEvent event = new FiberDefinedEvent();
        event.setFiberId(testFiber.getId());
        event.setCode(testFiber.getCode());
        event.setName(testFiber.getName());
        event.setCategory(testFiber.getCategory().name());
        
        String eventJson = "{\"fiberId\":\"" + testFiber.getId() + "\",\"code\":\"CO\"}";
        
        when(eventMapper.toDefinedEvent(testFiber)).thenReturn(event);
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        
        eventPublisher.publishFiberDefined(testFiber);
        
        verify(kafkaTemplate).send(
                eq("fiber-events"),
                eq(testFiber.getId().toString()),
                eq(eventJson)
        );
        verify(eventMapper).toDefinedEvent(testFiber);
        verify(objectMapper).writeValueAsString(event);
    }

    @Test
    @DisplayName("Should publish FIBER_UPDATED event successfully")
    void shouldPublishFiberUpdatedEvent() throws Exception {
        FiberUpdatedEvent event = new FiberUpdatedEvent();
        event.setFiberId(testFiber.getId());
        event.setCode(testFiber.getCode());
        
        String eventJson = "{\"fiberId\":\"" + testFiber.getId() + "\"}";
        
        when(eventMapper.toUpdatedEvent(testFiber)).thenReturn(event);
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        
        eventPublisher.publishFiberUpdated(testFiber);
        
        verify(kafkaTemplate).send(
                eq("fiber-events"),
                eq(testFiber.getId().toString()),
                eq(eventJson)
        );
        verify(eventMapper).toUpdatedEvent(testFiber);
    }

    @Test
    @DisplayName("Should publish FIBER_DEACTIVATED event successfully")
    void shouldPublishFiberDeactivatedEvent() throws Exception {
        FiberDeactivatedEvent event = new FiberDeactivatedEvent();
        event.setFiberId(testFiber.getId());
        event.setCode(testFiber.getCode());
        
        String eventJson = "{\"fiberId\":\"" + testFiber.getId() + "\"}";
        
        when(eventMapper.toDeactivatedEvent(testFiber)).thenReturn(event);
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        
        eventPublisher.publishFiberDeactivated(testFiber);
        
        verify(kafkaTemplate).send(
                eq("fiber-events"),
                eq(testFiber.getId().toString()),
                eq(eventJson)
        );
        verify(eventMapper).toDeactivatedEvent(testFiber);
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void shouldHandleJsonSerializationError() throws Exception {
        when(eventMapper.toDefinedEvent(testFiber)).thenReturn(mock(FiberDefinedEvent.class));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));
        
        eventPublisher.publishFiberDefined(testFiber);
        
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle Kafka send error gracefully")
    void shouldHandleKafkaSendError() throws Exception {
        FiberDefinedEvent event = new FiberDefinedEvent();
        event.setFiberId(testFiber.getId());
        event.setCode(testFiber.getCode());
        
        when(eventMapper.toDefinedEvent(testFiber)).thenReturn(event);
        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        
        eventPublisher.publishFiberDefined(testFiber);
        
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
    }
}

