package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.receipt.ReceiptEvent;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;
import java.util.List;

/**
 * Generates a GST-compliant Tax Invoice PDF for a confirmed Dashly order.
 *
 * Invoice structure (per Indian GST rules):
 * 1. Header — company name + "TAX INVOICE" title
 * 2. Meta — invoice number, date | company address + GSTIN
 * 3. Bill To — customer ID, shop ID
 * 4. Items — line-item table with qty, unit price, line total
 * 5. Totals — subtotal (excl. GST), CGST 9%, SGST 9%, grand total
 * 6. Payment — mode + status
 * 7. Footer — "computer-generated" note
 *
 * GST assumption: all prices are 18% GST-inclusive (CGST 9% + SGST 9%).
 */
@Service
@Slf4j
public class ReceiptGeneratorService {

    // ── Company constants ─────────────────────────────────────────────────────
    private static final String COMPANY_NAME = "Dashly Technologies Pvt. Ltd.";
    private static final String COMPANY_GSTIN = "27AAAD0000A1ZY"; // placeholder — replace before production
    private static final String COMPANY_ADDRESS = "123, Commerce Park, Andheri East, Mumbai - 400069";
    private static final String COMPANY_EMAIL = "support@dashly.in";
    private static final String COMPANY_PHONE = "+91-22-12345678";

    // ── GST rate ──────────────────────────────────────────────────────────────
    private static final double GST_RATE = 0.18; // 18% inclusive
    private static final double CGST_RATE = 0.09;
    private static final double SGST_RATE = 0.09;

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color BRAND_BLUE = new Color(26, 86, 219);
    private static final Color LIGHT_HEADER = new Color(235, 241, 255);
    private static final Color DIVIDER_GRAY = new Color(200, 200, 200);
    private static final Color TEXT_DARK = new Color(30, 30, 30);
    private static final Color TEXT_MUTED = new Color(100, 100, 100);
    private static final Color WHITE = Color.WHITE;

    // ── Date formatter ────────────────────────────────────────────────────────

    // ══════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ══════════════════════════════════════════════════════════════════════════

    public byte[] generatePdf(ReceiptEvent event, String invoiceNumber) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, invoiceNumber, event);
            addDivider(doc);
            addBillTo(doc, event);
            addDivider(doc);
            addItemsTable(doc, event.getItems());
            addTotalsTable(doc, event.getTotalAmountPaise());
            addPaymentInfo(doc, event);
            addFooter(doc, invoiceNumber);

            doc.close();
            log.debug("PDF generated | invoiceNumber={} bytes={}", invoiceNumber, baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed for invoice " + invoiceNumber, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Section builders
    // ══════════════════════════════════════════════════════════════════════════

    /** Two-column header: company info (left) | invoice meta (right). */
    private void addHeader(Document doc, String invoiceNumber, ReceiptEvent event) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[] { 55, 45 });
        header.setSpacingAfter(6f);

        // Left: company branding
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(8);

        Paragraph companyName = new Paragraph(COMPANY_NAME, font(14, Font.BOLD, BRAND_BLUE));
        companyName.setSpacingAfter(3);
        left.addElement(companyName);
        left.addElement(new Paragraph("GSTIN: " + COMPANY_GSTIN, font(9, Font.NORMAL, TEXT_MUTED)));
        left.addElement(new Paragraph(COMPANY_ADDRESS, font(9, Font.NORMAL, TEXT_MUTED)));
        left.addElement(new Paragraph("Email: " + COMPANY_EMAIL, font(9, Font.NORMAL, TEXT_MUTED)));
        left.addElement(new Paragraph("Phone: " + COMPANY_PHONE, font(9, Font.NORMAL, TEXT_MUTED)));
        header.addCell(left);

        // Right: "TAX INVOICE" + meta
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPaddingBottom(8);

        Paragraph invoiceTitle = new Paragraph("TAX INVOICE", font(18, Font.BOLD, BRAND_BLUE));
        invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invoiceTitle);

        ZonedDateTime confirmedAt = DateTimeUtil.toIst(event.getOrderConfirmedAt());
        addLabelValue(right, "Invoice No : ", invoiceNumber);
        addLabelValue(right, "Date       : ", confirmedAt.format(DateTimeUtil.DISPLAY_DATE_FMT));
        addLabelValue(right, "Booking ID : ", shortId(event.getBookingId()));
        header.addCell(right);

        doc.add(header);
    }

    private void addBillTo(Document doc, ReceiptEvent event) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 50, 50 });
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);

        // Bill To
        PdfPCell billTo = new PdfPCell();
        billTo.setBorder(Rectangle.BOX);
        billTo.setBorderColor(DIVIDER_GRAY);
        billTo.setPadding(8);
        billTo.addElement(new Paragraph("BILL TO", font(9, Font.BOLD, TEXT_MUTED)));
        billTo.addElement(new Paragraph("Customer ID:", font(9, Font.BOLD, TEXT_DARK)));
        billTo.addElement(new Paragraph(event.getUserId().toString(), font(9, Font.NORMAL, TEXT_DARK)));
        table.addCell(billTo);

        // Ship From (seller)
        PdfPCell shipFrom = new PdfPCell();
        shipFrom.setBorder(Rectangle.BOX);
        shipFrom.setBorderColor(DIVIDER_GRAY);
        shipFrom.setPadding(8);
        shipFrom.addElement(new Paragraph("SELLER / SHOP", font(9, Font.BOLD, TEXT_MUTED)));
        shipFrom.addElement(new Paragraph("Shop ID:", font(9, Font.BOLD, TEXT_DARK)));
        shipFrom.addElement(new Paragraph(event.getShopId().toString(), font(9, Font.NORMAL, TEXT_DARK)));
        table.addCell(shipFrom);

        doc.add(table);
    }

    /** Line-items table. */
    private void addItemsTable(Document doc, List<ReceiptEvent.ReceiptItemEvent> items)
            throws DocumentException {

        PdfPTable table = new PdfPTable(new float[] { 4f, 3.5f, 1.5f, 2.5f, 2.5f });
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(4);
        table.setHeaderRows(1);

        // Column headers
        String[] headers = { "Product ID", "Variant ID", "Qty", "Unit Price", "Line Total" };
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font(9, Font.BOLD, WHITE)));
            cell.setBackgroundColor(BRAND_BLUE);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        // Data rows
        boolean alternate = false;
        for (ReceiptEvent.ReceiptItemEvent item : items) {
            Color bg = alternate ? LIGHT_HEADER : WHITE;
            addItemRow(table, bg,
                    shortId(item.getProductId()),
                    shortId(item.getVariantId()),
                    String.valueOf(item.getQuantity()),
                    "Rs. " + paiseToRupees(item.getUnitPricePaise()),
                    "Rs. " + paiseToRupees(item.getLineTotalPaise()));
            alternate = !alternate;
        }

        doc.add(table);
    }

    /** Totals block: subtotal, CGST, SGST, grand total. */
    private void addTotalsTable(Document doc, String totalAmountPaise) throws DocumentException {
        long totalPaise = Long.parseLong(totalAmountPaise);
        long basePaise = Math.round(totalPaise / (1 + GST_RATE));
        long gstPaise = totalPaise - basePaise;
        long cgstPaise = gstPaise / 2;
        long sgstPaise = gstPaise - cgstPaise; // absorbs rounding

        PdfPTable table = new PdfPTable(new float[] { 6f, 2.5f });
        table.setWidthPercentage(55);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(2);
        table.setSpacingAfter(10);

        addTotalRow(table, "Taxable Amount (excl. GST)", "Rs. " + paiseToRupees(String.valueOf(basePaise)), false);
        addTotalRow(table, "CGST @ " + (int) (CGST_RATE * 100) + "%",
                "Rs. " + paiseToRupees(String.valueOf(cgstPaise)), false);
        addTotalRow(table, "SGST @ " + (int) (SGST_RATE * 100) + "%",
                "Rs. " + paiseToRupees(String.valueOf(sgstPaise)), false);

        // Grand total — highlighted row
        PdfPCell labelCell = new PdfPCell(new Phrase("Grand Total (incl. GST)", font(10, Font.BOLD, WHITE)));
        labelCell.setBackgroundColor(BRAND_BLUE);
        labelCell.setPadding(7);
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(
                new Phrase("Rs. " + paiseToRupees(totalAmountPaise), font(10, Font.BOLD, WHITE)));
        valueCell.setBackgroundColor(BRAND_BLUE);
        valueCell.setPadding(7);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);

        doc.add(table);
    }

    private void addPaymentInfo(Document doc, ReceiptEvent event) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(DIVIDER_GRAY);
        cell.setPadding(10);
        cell.setBackgroundColor(LIGHT_HEADER);

        cell.addElement(new Paragraph("PAYMENT DETAILS", font(9, Font.BOLD, TEXT_MUTED)));

        String modeDisplay = switch (event.getPaymentMode()) {
            case "COD" -> "Cash on Delivery";
            case "ONLINE" -> "Online (PhonePe / UPI)";
            case "POINTS" -> "Loyalty Points";
            case "MIXED" -> "Online + Loyalty Points";
            default -> event.getPaymentMode();
        };

        cell.addElement(spacer(3));
        cell.addElement(inlineLabel("Payment Mode   : ", modeDisplay));
        cell.addElement(inlineLabel("Amount Charged : ", "Rs. " + paiseToRupees(event.getTotalAmountPaise())));
        cell.addElement(inlineLabel("Amount Paid    : ", "Rs. " + paiseToRupees(event.getPaidAmountPaise())));

        ZonedDateTime confirmedAt = DateTimeUtil.toIst(event.getOrderConfirmedAt());
        cell.addElement(inlineLabel("Confirmed At   : ", confirmedAt.format(DateTimeUtil.DISPLAY_DATETIME_FMT)));

        table.addCell(cell);
        doc.add(table);
    }

    private void addFooter(Document doc, String invoiceNumber) throws DocumentException {
        doc.add(spacer(16));

        Paragraph disclaimer = new Paragraph(
                "This is a computer-generated Tax Invoice and does not require a physical signature.",
                font(8, Font.ITALIC, TEXT_MUTED));
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        doc.add(disclaimer);

        Paragraph brand = new Paragraph(
                COMPANY_NAME + "  |  GSTIN: " + COMPANY_GSTIN + "  |  " + COMPANY_EMAIL,
                font(8, Font.NORMAL, TEXT_MUTED));
        brand.setAlignment(Element.ALIGN_CENTER);
        doc.add(brand);
    }

    private void addDivider(Document doc) throws DocumentException {
        Paragraph line = new Paragraph(" ");
        line.setSpacingBefore(4);
        line.setSpacingAfter(4);
        doc.add(line);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Table cell helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void addItemRow(PdfPTable table, Color bg, String... values) {
        boolean right = false;
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, font(9, Font.NORMAL, TEXT_DARK)));
            cell.setBackgroundColor(bg);
            cell.setPadding(5);
            cell.setBorderColor(DIVIDER_GRAY);
            cell.setBorderWidth(0.5f);
            if (right)
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            right = true; // first column left, rest right
            table.addCell(cell);
        }
    }

    private void addTotalRow(PdfPTable table, String label, String value, boolean bold) {
        int style = bold ? Font.BOLD : Font.NORMAL;

        PdfPCell lc = new PdfPCell(new Phrase(label, font(9, style, TEXT_DARK)));
        lc.setPadding(5);
        lc.setBorderColor(DIVIDER_GRAY);
        lc.setBorderWidth(0.5f);
        table.addCell(lc);

        PdfPCell vc = new PdfPCell(new Phrase(value, font(9, style, TEXT_DARK)));
        vc.setPadding(5);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vc.setBorderColor(DIVIDER_GRAY);
        vc.setBorderWidth(0.5f);
        table.addCell(vc);
    }

    private void addLabelValue(PdfPCell cell, String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label, font(9, Font.BOLD, TEXT_MUTED)));
        p.add(new Chunk(value, font(9, Font.NORMAL, TEXT_DARK)));
        p.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(p);
    }

    private Paragraph inlineLabel(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label, font(9, Font.BOLD, TEXT_DARK)));
        p.add(new Chunk(value, font(9, Font.NORMAL, TEXT_DARK)));
        return p;
    }

    private Paragraph spacer(float points) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(points);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utility helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Font font(int size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    /** Converts a paise string to a formatted rupee string, e.g. "1499.00". */
    private String paiseToRupees(String paise) {
        if (paise == null || paise.isBlank())
            return "0.00";
        try {
            return new BigDecimal(paise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    /** Returns the first 8 chars of a UUID in upper case for display brevity. */
    private String shortId(java.util.UUID id) {
        return id == null ? "—" : id.toString().substring(0, 8).toUpperCase();
    }
}
