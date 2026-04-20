package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatMessageEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.KafkaProducerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageProducer {

    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Value("${chat.message.received.topic:chat.message.received}")
    private String topic;

    public void publishMessageReceived(ChatMessageEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaProducerService.sendMessage(topic, json);
            log.info("[ChatMessageProducer] Published: channelUrl={}, messageId={}",
                event.getChannelUrl(), event.getMessageId());
        } catch (JsonProcessingException e) {
            log.error("[ChatMessageProducer] Failed to serialize ChatMessageEvent: {}", e.getMessage());
        }
    }
}
