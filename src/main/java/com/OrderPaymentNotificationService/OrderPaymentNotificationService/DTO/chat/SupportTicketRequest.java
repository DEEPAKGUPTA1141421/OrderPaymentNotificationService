package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupportTicketRequest {

    @NotBlank(message = "ticketId is required")
    private String ticketId;

    @NotBlank(message = "issue description is required")
    private String issue;

    private String category;
}
