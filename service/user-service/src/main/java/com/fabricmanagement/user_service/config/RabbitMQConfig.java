package com.fabricmanagement.user_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String USER_EXCHANGE = "user.exchange";

    // Queue names
    public static final String USER_CREATED_QUEUE = "user.created.queue";
    public static final String USER_UPDATED_QUEUE = "user.updated.queue";
    public static final String USER_DELETED_QUEUE = "user.deleted.queue";

    // Routing keys
    public static final String USER_CREATED_KEY = "user.created";
    public static final String USER_UPDATED_KEY = "user.updated";
    public static final String USER_DELETED_KEY = "user.deleted";

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE);
    }

    @Bean
    public Queue userCreatedQueue() {
        return QueueBuilder.durable(USER_CREATED_QUEUE)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Queue userUpdatedQueue() {
        return QueueBuilder.durable(USER_UPDATED_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .build();
    }

    @Bean
    public Queue userDeletedQueue() {
        return QueueBuilder.durable(USER_DELETED_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .build();
    }

    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue, TopicExchange userExchange) {
        return BindingBuilder
                .bind(userCreatedQueue)
                .to(userExchange)
                .with(USER_CREATED_KEY);
    }

    @Bean
    public Binding userUpdatedBinding(Queue userUpdatedQueue, TopicExchange userExchange) {
        return BindingBuilder
                .bind(userUpdatedQueue)
                .to(userExchange)
                .with(USER_UPDATED_KEY);
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange userExchange) {
        return BindingBuilder
                .bind(userDeletedQueue)
                .to(userExchange)
                .with(USER_DELETED_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}