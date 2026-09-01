package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.CreateInvoiceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.CustomerRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceItemRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceItemResponseDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceResponseDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceSummaryDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.PriceValidationResult;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.ProductDetailDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Invoice;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceCustomer;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceDelivery;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoicePdf;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceCustomerRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceDeliveryRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoicePdfRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BaseService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network.ProductClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final InvoiceRepository         invoiceRepo;
    private final InvoiceCustomerRepository customerRepo;
    private final InvoicePdfRepository      pdfRepo;
    private final InvoiceDeliveryRepository deliveryRepo;
    private final ProductClient             productClient;
    private final PriceValidationService    priceValidationService;
    private final InvoiceNumberService      invoiceNumberService;
    private final InvoiceGeneratorService   pdfGenerator;
    private final InvoicePdfUploadService   pdfUploadService;
    private final InvoiceDeliveryProducerService deliveryProducer;

    @org.springframework.beans.factory.annotation.Value("${internal.api.key:internal-api}")
    private String internalApiKey;

    // ── Create / update (DRAFT only) ──────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> createDraft(CreateInvoiceRequest req) {
        UUID sellerId = getUserId();

        Invoice invoice = new Invoice();
        invoice.setSellerId(sellerId);
        invoice.setStatus(Invoice.Status.DRAFT);
        applyCustomerAndItems(invoice, sellerId, req);

        invoiceRepo.save(invoice);
        log.info("Invoice draft created | sellerId={} invoiceId={}", sellerId, invoice.getId());
        return new ApiResponse<>(true, "Draft created", toResponseDto(invoice), 201);
    }

    @Transactional
    public ApiResponse<Object> updateDraft(UUID invoiceId, CreateInvoiceRequest req) {
        UUID sellerId = getUserId();
        Invoice invoice = requireOwned(invoiceId, sellerId);

        if (invoice.getStatus() != Invoice.Status.DRAFT) {
            return new ApiResponse<>(false, "Only draft invoices can be edited", null, 422);
        }

        invoice.getItems().clear(); // orphanRemoval deletes the old rows on flush
        applyCustomerAndItems(invoice, sellerId, req);

        invoiceRepo.save(invoice);
        return new ApiResponse<>(true, "Draft updated", toResponseDto(invoice), 200);
    }

    private void applyCustomerAndItems(Invoice invoice, UUID sellerId, CreateInvoiceRequest req) {
        invoice.setCustomer(resolveCustomer(sellerId, req.customer()));

        List<InvoiceItemRequest> itemReqs = req.items() != null ? req.items() : List.of();
        long subtotal = 0, discount = 0, tax = 0;

        for (int i = 0; i < itemReqs.size(); i++) {
            InvoiceItem item = buildItem(sellerId, i, itemReqs.get(i));
            invoice.getItems().add(item);
            item.setInvoice(invoice);

            subtotal += Math.round(item.getUnitPricePaise() * item.getQuantity());
            discount += item.getDiscountPaise();
            tax      += item.getTaxPaise();
        }

        long invoiceLevelDiscountPaise = req.invoiceDiscount() != null
                ? Math.round(req.invoiceDiscount() * 100) : 0;
        discount += invoiceLevelDiscountPaise;

        invoice.setSubtotalPaise(subtotal);
        invoice.setDiscountPaise(discount);
        invoice.setTaxPaise(tax);
        invoice.setTotalPaise(Math.max(0, subtotal - discount + tax));
    }

    /// Reuses an existing customer-book entry (matched by phone, then email,
    /// then name) instead of always inserting a new row, so the same
    /// walk-in customer invoiced repeatedly doesn't show up as duplicates
    /// in the customer picker.
    private InvoiceCustomer resolveCustomer(UUID sellerId, CustomerRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) return null;
        InvoiceCustomer customer = findExistingCustomer(sellerId, req).orElseGet(InvoiceCustomer::new);
        customer.setSellerId(sellerId);
        customer.setName(req.name());
        customer.setPhone(req.phone());
        customer.setEmail(req.email());
        customer.setGstin(req.gstin());
        return customerRepo.save(customer);
    }

    private java.util.Optional<InvoiceCustomer> findExistingCustomer(UUID sellerId, CustomerRequest req) {
        if (req.phone() != null && !req.phone().isBlank()) {
            java.util.Optional<InvoiceCustomer> byPhone = customerRepo.findFirstBySellerIdAndPhone(sellerId, req.phone());
            if (byPhone.isPresent()) return byPhone;
        }
        if (req.email() != null && !req.email().isBlank()) {
            java.util.Optional<InvoiceCustomer> byEmail = customerRepo.findFirstBySellerIdAndEmailIgnoreCase(sellerId, req.email());
            if (byEmail.isPresent()) return byEmail;
        }
        if ((req.phone() == null || req.phone().isBlank()) && (req.email() == null || req.email().isBlank())) {
            return customerRepo.findFirstBySellerIdAndNameIgnoreCaseAndPhoneIsNullAndEmailIsNull(sellerId, req.name());
        }
        return java.util.Optional.empty();
    }

    private InvoiceItem buildItem(UUID sellerId, int index, InvoiceItemRequest req) {
        InvoiceItem.ItemType type;
        try {
            type = InvoiceItem.ItemType.valueOf(req.type());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid item type at index " + index + ": " + req.type());
        }

        InvoiceItem item = new InvoiceItem();
        item.setItemType(type);
        item.setQuantity(Math.max(1, req.quantity()));
        item.setDiscountPaise(Math.round(req.discount() * 100));

        double catalogPrice = 0;
        if (type == InvoiceItem.ItemType.CATALOG) {
            if (req.productId() == null) {
                throw new IllegalArgumentException("productId is required for a CATALOG item at index " + index);
            }
            item.setProductId(req.productId());
            item.setVariantId(req.variantId());

            if (req.variantId() != null) {
                // Authoritative lookup — never trust a client-supplied catalog price when we can verify it.
                ApiResponse<ProductDetailDto> detailRes = productClient.getProductDetailInternal(
                        req.productId(), req.variantId(), internalApiKey);
                ProductDetailDto detail = detailRes != null ? detailRes.data() : null;
                if (detail == null) {
                    throw new NoSuchElementException("Product not found: " + req.productId());
                }
                if (!detail.getShopId().equals(sellerId)) {
                    throw new SecurityException("Product does not belong to this seller: " + req.productId());
                }
                catalogPrice = detail.getPrice();
                item.setNameSnapshot(detail.getName());
                item.setTaxRate(req.taxRate() != null ? req.taxRate() : detail.getGstRate());
            } else {
                // No variant to look up server-side — fall back to the client's search-result hint.
                catalogPrice = req.catalogPriceHint() != null ? req.catalogPriceHint() : req.unitPrice();
                item.setNameSnapshot(req.name() != null ? req.name() : "Product");
                item.setTaxRate(req.taxRate() != null ? req.taxRate() : 0);
            }
            item.setSkuSnapshot(req.sku());
            item.setCatalogPricePaise(Math.round(catalogPrice * 100));
        } else {
            if (req.name() == null || req.name().isBlank()) {
                throw new IllegalArgumentException("name is required for a CUSTOM item at index " + index);
            }
            item.setNameSnapshot(req.name());
            item.setSkuSnapshot(req.sku());
            item.setBarcodeSnapshot(req.barcode());
            item.setTaxRate(req.taxRate() != null ? req.taxRate() : 0);
        }

        if (catalogPrice > 0) {
            PriceValidationResult validation = priceValidationService.validate(catalogPrice, req.unitPrice());
            if (validation.warning() && !req.priceOverrideConfirmed()) {
                throw new PriceOverrideRequiredException(index, validation);
            }
            item.setPriceOverride(validation.warning());
            item.setOverrideReason(req.overrideReason());
        }

        item.setUnitPricePaise(Math.round(req.unitPrice() * 100));

        long afterDiscount = Math.max(0, item.getUnitPricePaise() * item.getQuantity() - item.getDiscountPaise());
        long taxPaise = Math.round(afterDiscount * (item.getTaxRate() / 100.0));
        item.setTaxPaise(taxPaise);
        item.setTotalPaise(afterDiscount + taxPaise);

        return item;
    }

    // ── Finalize / cancel / send ──────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> finalizeInvoice(UUID invoiceId) {
        UUID sellerId = getUserId();
        Invoice invoice = requireOwned(invoiceId, sellerId);

        try {
            invoice.getStatus().assertCanTransitionTo(Invoice.Status.FINALIZED);
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 422);
        }
        if (invoice.getItems().isEmpty()) {
            return new ApiResponse<>(false, "Cannot finalize an invoice with no items", null, 422);
        }
        if (invoice.getCustomer() == null) {
            return new ApiResponse<>(false, "Customer details are required to finalize an invoice", null, 422);
        }

        invoice.setInvoiceNumber(invoiceNumberService.next(sellerId));
        invoice.setIssuedAt(Instant.now());
        invoice.setStatus(Invoice.Status.FINALIZED);
        invoiceRepo.save(invoice);

        byte[] pdfBytes = pdfGenerator.generatePdf(invoice);
        String pdfUrl;
        try {
            pdfUrl = pdfUploadService.upload(invoiceId, pdfBytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload invoice PDF for " + invoiceId, e);
        }
        InvoicePdf pdf = pdfRepo.findByInvoiceId(invoiceId).orElseGet(InvoicePdf::new);
        pdf.setInvoiceId(invoiceId);
        pdf.setPdfUrl(pdfUrl);
        pdfRepo.save(pdf);

        log.info("Invoice finalized | sellerId={} invoiceId={} number={}",
                sellerId, invoiceId, invoice.getInvoiceNumber());
        return new ApiResponse<>(true, "Invoice finalized", toResponseDto(invoice), 200);
    }

    @Transactional
    public ApiResponse<Object> cancelInvoice(UUID invoiceId) {
        UUID sellerId = getUserId();
        Invoice invoice = requireOwned(invoiceId, sellerId);
        try {
            invoice.getStatus().assertCanTransitionTo(Invoice.Status.CANCELLED);
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 422);
        }
        invoice.setStatus(Invoice.Status.CANCELLED);
        invoiceRepo.save(invoice);
        return new ApiResponse<>(true, "Invoice cancelled", null, 200);
    }

    @Transactional
    public ApiResponse<Object> updateStatus(UUID invoiceId, String newStatusStr) {
        UUID sellerId = getUserId();
        Invoice invoice = requireOwned(invoiceId, sellerId);
        Invoice.Status newStatus;
        try {
            newStatus = Invoice.Status.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "Invalid status: " + newStatusStr, null, 400);
        }
        try {
            invoice.getStatus().assertCanTransitionTo(newStatus);
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 422);
        }
        invoice.setStatus(newStatus);
        invoiceRepo.save(invoice);
        return new ApiResponse<>(true, "Status updated to " + newStatus.name(), toResponseDto(invoice), 200);
    }

    @Transactional
    public ApiResponse<Object> sendInvoice(UUID invoiceId, String channelStr, String destinationOverride) {
        UUID sellerId = getUserId();
        Invoice invoice = requireOwned(invoiceId, sellerId);

        if (invoice.getStatus() == Invoice.Status.DRAFT || invoice.getStatus() == Invoice.Status.CANCELLED) {
            return new ApiResponse<>(false, "Finalize the invoice before sending it", null, 422);
        }

        InvoiceDelivery.Channel channel;
        try {
            channel = InvoiceDelivery.Channel.valueOf(channelStr.toUpperCase());
        } catch (Exception e) {
            return new ApiResponse<>(false, "Invalid channel: " + channelStr, null, 400);
        }

        String destination = destinationOverride;
        if ((destination == null || destination.isBlank()) && invoice.getCustomer() != null) {
            destination = channel == InvoiceDelivery.Channel.WHATSAPP
                    ? invoice.getCustomer().getPhone() : invoice.getCustomer().getEmail();
        }
        if (destination == null || destination.isBlank()) {
            return new ApiResponse<>(false, "No " + channel.name() + " destination available for this invoice", null, 422);
        }

        InvoiceDelivery delivery = new InvoiceDelivery();
        delivery.setInvoiceId(invoiceId);
        delivery.setChannel(channel);
        delivery.setDestination(destination);
        delivery.setStatus(InvoiceDelivery.Status.PENDING);
        deliveryRepo.save(delivery);

        if (invoice.getStatus() == Invoice.Status.FINALIZED) {
            invoice.setStatus(Invoice.Status.SENT);
            invoiceRepo.save(invoice);
        }

        deliveryProducer.publish(invoiceId, delivery.getId(), channel.name(), destination);

        log.info("Invoice send requested | sellerId={} invoiceId={} channel={} destination={}",
                sellerId, invoiceId, channel, mask(destination));
        return new ApiResponse<>(true, "Invoice queued for delivery", toResponseDto(invoice), 202);
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> list(String status, String query, int page, int size) {
        UUID sellerId = getUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Invoice> result;
        if (query != null && !query.isBlank()) {
            result = invoiceRepo.search(sellerId, query.trim(), pageable);
        } else if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                result = invoiceRepo.findBySellerIdAndStatus(sellerId, Invoice.Status.valueOf(status.toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid status: " + status, null, 400);
            }
        } else {
            result = invoiceRepo.findBySellerId(sellerId, pageable);
        }

        List<InvoiceSummaryDto> summaries = result.getContent().stream().map(this::toSummaryDto).toList();
        return new ApiResponse<>(true, "Invoices fetched", java.util.Map.of(
                "invoices", summaries,
                "currentPage", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalInvoices", result.getTotalElements(),
                "hasNext", result.hasNext()
        ), 200);
    }

    @Transactional
    public ApiResponse<Object> getDetail(UUID invoiceId) {
        Invoice invoice = requireOwned(invoiceId, getUserId());
        return new ApiResponse<>(true, "Invoice fetched", toResponseDto(invoice), 200);
    }

    public record InvoicePdfDownload(byte[] bytes, String filename) {}

    /// Fetches the PDF bytes server-side (via a freshly signed Cloudinary URL)
    /// instead of redirecting the caller to Cloudinary directly — a raw PDF's
    /// public delivery URL 401s under Cloudinary's default security settings,
    /// and letting that 401 reach the seller app's own HTTP client made its
    /// auth interceptor mistake it for an expired session and force a logout.
    public InvoicePdfDownload downloadPdf(UUID invoiceId) {
        Invoice invoice = requireOwned(invoiceId, getUserId());
        pdfRepo.findByInvoiceId(invoice.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "This invoice hasn't been finalized yet — no PDF has been generated."));
        try {
            byte[] bytes = pdfUploadService.downloadBytes(invoiceId);
            String filename = (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : invoiceId.toString()) + ".pdf";
            return new InvoicePdfDownload(bytes, filename);
        } catch (Exception e) {
            log.error("Failed to fetch invoice PDF from Cloudinary | invoiceId={} error={}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch invoice PDF for " + invoiceId, e);
        }
    }

    public List<java.util.Map<String, Object>> getCustomerBook() {
        java.util.LinkedHashMap<String, InvoiceCustomer> deduped = new java.util.LinkedHashMap<>();
        for (InvoiceCustomer c : customerRepo.findBySellerIdOrderByNameAsc(getUserId())) {
            String key = c.getPhone() != null && !c.getPhone().isBlank()
                    ? "p:" + c.getPhone()
                    : c.getEmail() != null && !c.getEmail().isBlank()
                        ? "e:" + c.getEmail().toLowerCase()
                        : "n:" + c.getName().toLowerCase();
            // Keep the most recently created duplicate — it's the most likely
            // to carry the customer's up-to-date contact details.
            InvoiceCustomer existing = deduped.get(key);
            if (existing == null || c.getCreatedAt().isAfter(existing.getCreatedAt())) {
                deduped.put(key, c);
            }
        }
        return deduped.values().stream()
                .sorted(java.util.Comparator.comparing(InvoiceCustomer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> java.util.Map.<String, Object>of(
                        "id", c.getId().toString(),
                        "name", c.getName(),
                        "phone", c.getPhone() != null ? c.getPhone() : "",
                        "email", c.getEmail() != null ? c.getEmail() : ""))
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Invoice requireOwned(UUID invoiceId, UUID sellerId) {
        return invoiceRepo.findByIdAndSellerId(invoiceId, sellerId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + invoiceId));
    }

    private String mask(String destination) {
        if (destination.length() <= 4) return "***";
        return destination.substring(0, 2) + "***" + destination.substring(destination.length() - 2);
    }

    private InvoiceSummaryDto toSummaryDto(Invoice invoice) {
        String lastChannel = deliveryRepo.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
                .findFirst().map(d -> d.getChannel().name()).orElse(null);
        return new InvoiceSummaryDto(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                invoice.getCustomer() != null ? invoice.getCustomer().getName() : null,
                toRupees(invoice.getTotalPaise()),
                lastChannel,
                invoice.getCreatedAt());
    }

    private InvoiceResponseDto toResponseDto(Invoice invoice) {
        CustomerRequest customerDto = invoice.getCustomer() != null
                ? new CustomerRequest(invoice.getCustomer().getName(), invoice.getCustomer().getPhone(),
                        invoice.getCustomer().getEmail(), invoice.getCustomer().getGstin())
                : null;

        List<InvoiceItemResponseDto> items = invoice.getItems().stream().map(item -> new InvoiceItemResponseDto(
                item.getId(),
                item.getItemType().name(),
                item.getProductId(),
                item.getVariantId(),
                item.getNameSnapshot(),
                item.getSkuSnapshot(),
                item.getBarcodeSnapshot(),
                item.getCatalogPricePaise() != null ? toRupees(item.getCatalogPricePaise()) : null,
                toRupees(item.getUnitPricePaise()),
                item.getQuantity(),
                toRupees(item.getDiscountPaise()),
                item.getTaxRate(),
                toRupees(item.getTaxPaise()),
                toRupees(item.getTotalPaise()),
                item.isPriceOverride(),
                item.getOverrideReason()
        )).toList();

        return new InvoiceResponseDto(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                customerDto,
                items,
                toRupees(invoice.getSubtotalPaise()),
                toRupees(invoice.getDiscountPaise()),
                toRupees(invoice.getTaxPaise()),
                toRupees(invoice.getTotalPaise()),
                invoice.getCurrency(),
                invoice.getIssuedAt(),
                invoice.getDueAt(),
                invoice.getCreatedAt());
    }

    private double toRupees(long paise) {
        return BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
