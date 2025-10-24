package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.modulith.events.core.EventSerializer;

/**
 * Events Infrastructure Configuration
 * 
 * Configures Spring Modulith event publication and persistence.
 * Events are serialized to JSON and stored in event_publication table.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class EventsConfiguration {

    private final ObjectMapper objectMapper;

    @Bean
    public EventSerializer eventSerializer() {
        log.info("Configuring EventSerializer with Jackson ObjectMapper");
        return new JacksonEventSerializerImpl(objectMapper);
    }

    /**
     * Custom EventSerializer implementation using Jackson ObjectMapper
     */
    private static class JacksonEventSerializerImpl implements EventSerializer {
        
        private final ObjectMapper objectMapper;

        public JacksonEventSerializerImpl(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        @NonNull
        public Object serialize(@NonNull Object event) {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize event: " + event, e);
            }
        }

        @Override
        @NonNull
        public <T> T deserialize(@NonNull Object serialized, @NonNull Class<T> type) {
            try {
                return objectMapper.readValue(serialized.toString(), type);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize event: " + serialized, e);
            }
        }
    }
}

