package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.CreateInvoiceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.PriceValidationResult;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.SendInvoiceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice.InvoiceService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice.PriceOverrideRequiredException;

import lombok.RequiredArgsConstructor;

/**
 * Seller-authored (POS-style) invoices — distinct from the auto-generated
 * buyer-order receipts under {@link ReceiptController}. All endpoints are
 * scoped to the authenticated seller (shopId from the JWT); ownership is
 * enforced in InvoiceService, never trusted from the request body.
 */
@RestController
@RequestMapping("/api/v1/seller/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<?> createDraft(@RequestBody @Valid CreateInvoiceRequest req) {
        return respond(() -> invoiceService.createDraft(req));
    }

    @PutMapping("/{invoiceId}")
    public ResponseEntity<?> updateDraft(@PathVariable UUID invoiceId, @RequestBody @Valid CreateInvoiceRequest req) {
        return respond(() -> invoiceService.updateDraft(invoiceId, req));
    }

    @PostMapping("/{invoiceId}/finalize")
    public ResponseEntity<?> finalizeInvoice(@PathVariable UUID invoiceId) {
        return respond(() -> invoiceService.finalizeInvoice(invoiceId));
    }

    @PostMapping("/{invoiceId}/cancel")
    public ResponseEntity<?> cancelInvoice(@PathVariable UUID invoiceId) {
        return respond(() -> invoiceService.cancelInvoice(invoiceId));
    }

    @PutMapping("/{invoiceId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable UUID invoiceId, @RequestBody java.util.Map<String, String> body) {
        return respond(() -> invoiceService.updateStatus(invoiceId, body.get("status")));
    }

    @PostMapping("/{invoiceId}/send")
    public ResponseEntity<?> sendInvoice(@PathVariable UUID invoiceId, @RequestBody SendInvoiceRequest req) {
        return respond(() -> invoiceService.sendInvoice(invoiceId, req.channel(), req.destination()));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return respond(() -> invoiceService.list(status, query, page, size));
    }

    @GetMapping("/customers")
    public ResponseEntity<?> customerBook() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customers fetched", invoiceService.getCustomerBook(), 200));
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<?> getDetail(@PathVariable UUID invoiceId) {
        return respond(() -> invoiceService.getDetail(invoiceId));
    }

    @GetMapping("/{invoiceId}/download")
    public ResponseEntity<?> downloadPdf(@PathVariable UUID invoiceId) {
        try {
            InvoiceService.InvoicePdfDownload pdf = invoiceService.downloadPdf(invoiceId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
                    .body(pdf.bytes());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(new ApiResponse<>(false, e.getMessage(), null, 404));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(new ApiResponse<>(false, "Access denied", null, 403));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(new ApiResponse<>(false,
                    "Couldn't fetch the invoice PDF right now. Please try again.", null, 502));
        }
    }

    // ── Shared error mapping ──────────────────────────────────────────────

    private ResponseEntity<?> respond(java.util.function.Supplier<ApiResponse<Object>> action) {
        try {
            ApiResponse<Object> res = action.get();
            return ResponseEntity.status(res.statusCode()).body(res);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(new ApiResponse<>(false, e.getMessage(), null, 404));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(new ApiResponse<>(false, "Access denied", null, 403));
        } catch (PriceOverrideRequiredException e) {
            PriceValidationResult r = e.getResult();
            return ResponseEntity.status(422).body(new ApiResponse<>(false, e.getMessage(), java.util.Map.of(
                    "itemIndex", e.getItemIndex(),
                    "reason", r.reason(),
                    "catalogPrice", r.catalogPrice(),
                    "enteredPrice", r.enteredPrice(),
                    "percentageDifference", r.percentageDifference()
            ), 422));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ApiResponse<>(false, e.getMessage(), null, 400));
        }
    }
}
