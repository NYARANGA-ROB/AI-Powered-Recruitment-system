package com.smartats.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange
    public static final String RESUME_EXCHANGE = "smartats.exchange";

    // Queue
    public static final String RESUME_PARSE_QUEUE = "resume.parse.queue";

    // Dead letter queue
    public static final String RESUME_PARSE_DLQ = "resume.parse.dlq";

    // Dead letter exchange
    public static final String DEAD_LETTER_EXCHANGE = "smartats.dlx";

    // Delayed retry queue (once a message expires it is routed back to the main queue automatically)
    public static final String RESUME_PARSE_DELAY_QUEUE = "resume.parse.delay";
    public static final String RESUME_PARSE_DELAY_ROUTING_KEY = "resume.parse.delay";

    // Routing keys
    public static final String RESUME_PARSE_ROUTING_KEY = "resume.parse";
    public static final String DEAD_LETTER_ROUTING_KEY = "resume.parse.dlq";

    /**
     * JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure the RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    /**
     * Main exchange
     */
    @Bean
    public DirectExchange resumeExchange() {
        return new DirectExchange(RESUME_EXCHANGE, true, false);
    }

    /**
     * Dead letter exchange
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * Main queue (with dead letter configuration)
     */
    @Bean
    public Queue resumeParseQueue() {
        return QueueBuilder.durable(RESUME_PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-max-retries", 3)  // Maximum number of retries
                .build();
    }

    /**
     * Delayed retry queue (when the message TTL expires it is routed back to the main queue via the DLX, giving exponential backoff)
     */
    @Bean
    public Queue resumeParseDelayQueue() {
        return QueueBuilder.durable(RESUME_PARSE_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", RESUME_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RESUME_PARSE_ROUTING_KEY)
                .build();
    }

    /**
     * Dead letter queue
     */
    @Bean
    public Queue resumeParseDLQ() {
        return QueueBuilder.durable(RESUME_PARSE_DLQ).build();
    }

    /**
     * Bind the main queue to the main exchange
     */
    @Bean
    public Binding resumeParseBinding() {
        return BindingBuilder
                .bind(resumeParseQueue())
                .to(resumeExchange())
                .with(RESUME_PARSE_ROUTING_KEY);
    }

    /**
     * Bind the delayed retry queue to the main exchange
     */
    @Bean
    public Binding delayQueueBinding() {
        return BindingBuilder
                .bind(resumeParseDelayQueue())
                .to(resumeExchange())
                .with(RESUME_PARSE_DELAY_ROUTING_KEY);
    }

    /**
     * Bind the dead letter queue to the dead letter exchange
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(resumeParseDLQ())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}