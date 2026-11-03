package com.smartats.infrastructure.mq;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Send a resume parsing message
     */
    public void sendResumeParseMessage(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.RESUME_EXCHANGE,
                    RabbitMQConfig.RESUME_PARSE_ROUTING_KEY,
                    message
            );

            log.info("Resume parsing message sent successfully: {}", message);

        } catch (Exception e) {
            log.error("Failed to send the resume parsing message", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to send the message");
        }
    }
}