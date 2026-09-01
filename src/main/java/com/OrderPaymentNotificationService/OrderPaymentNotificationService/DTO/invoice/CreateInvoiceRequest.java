package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateInvoiceRequest(
        @Valid @NotNull(message = "Customer details are required to generate an invoice")
        CustomerRequest customer,
        List<InvoiceItemRequest> items,
        Double invoiceDiscount          // rupees, invoice-level discount on top of line discounts (optional)
) {
}
