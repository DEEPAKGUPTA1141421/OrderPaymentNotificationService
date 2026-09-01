package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Inline customer payload on create/update — a new InvoiceCustomer row is upserted from this. */
public record CustomerRequest(
        @NotBlank(message = "Customer name is required")
        String name,

        @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
        String phone,

        @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Enter a valid email address")
        String email,

        @Pattern(regexp = "^$|^[0-9A-Z]{15}$", message = "Enter a valid 15-character GSTIN")
        String gstin
) {
}
