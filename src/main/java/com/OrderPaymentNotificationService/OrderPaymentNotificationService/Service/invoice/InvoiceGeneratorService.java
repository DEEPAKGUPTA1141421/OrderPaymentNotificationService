package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Invoice;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceCustomer;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders a seller-authored {@link Invoice} to PDF. Sibling to
 * ReceiptGeneratorService (which renders auto-generated buyer-order
 * receipts) — deliberately not shared, since the two have unrelated data
 * models (Invoice/InvoiceItem vs Booking/BookingItem) and lifecycles.
 */
@Service
@Slf4j
public class InvoiceGeneratorService {

    private static final String COMPANY_NAME    = "Dashly Technologies Pvt. Ltd.";
    private static final String COMPANY_ADDRESS = "123, Commerce Park, Andheri East, Mumbai - 400069";
    private static final String COMPANY_EMAIL   = "support@dashly.in";

    private static final Color BRAND_BLUE   = new Color(26, 86, 219);
    private static final Color LIGHT_HEADER = new Color(235, 241, 255);
    private static final Color DIVIDER_GRAY = new Color(200, 200, 200);
    private static final Color TEXT_DARK    = new Color(30, 30, 30);
    private static final Color TEXT_MUTED   = new Color(100, 100, 100);
    private static final Color WHITE        = Color.WHITE;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generatePdf(Invoice invoice) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, invoice);
            addDivider(doc);
            addBillTo(doc, invoice.getCustomer());
            addDivider(doc);
            addItemsTable(doc, invoice.getItems());
            addTotalsTable(doc, invoice);
            addFooter(doc);

            doc.close();
            log.debug("Invoice PDF generated | invoiceNumber={} bytes={}", invoice.getInvoiceNumber(), baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed for invoice " + invoice.getInvoiceNumber(), e);
        }
    }

    private void addHeader(Document doc, Invoice invoice) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{55, 45});
        header.setSpacingAfter(6f);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(8);
        Paragraph companyName = new Paragraph(COMPANY_NAME, font(14, Font.BOLD, BRAND_BLUE));
        companyName.setSpacingAfter(3);
        left.addElement(companyName);
        left.addElement(new Paragraph(COMPANY_ADDRESS, font(9, Font.NORMAL, TEXT_MUTED)));
        left.addElement(new Paragraph("Email: " + COMPANY_EMAIL, font(9, Font.NORMAL, TEXT_MUTED)));
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPaddingBottom(8);
        Paragraph title = new Paragraph("INVOICE", font(18, Font.BOLD, BRAND_BLUE));
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        ZonedDateTime issuedAt = (invoice.getIssuedAt() != null ? invoice.getIssuedAt() : invoice.getCreatedAt())
                .atZone(ZoneId.of("Asia/Kolkata"));
        addLabelValue(right, "Invoice No : ", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "DRAFT");
        addLabelValue(right, "Date       : ", issuedAt.format(DATE_FMT));
        header.addCell(right);

        doc.add(header);
    }

    private void addBillTo(Document doc, InvoiceCustomer customer) throws DocumentException {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(DIVIDER_GRAY);
        cell.setPadding(8);
        cell.addElement(new Paragraph("BILL TO", font(9, Font.BOLD, TEXT_MUTED)));
        if (customer != null) {
            cell.addElement(new Paragraph(customer.getName(), font(10, Font.BOLD, TEXT_DARK)));
            if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
                cell.addElement(new Paragraph(customer.getPhone(), font(9, Font.NORMAL, TEXT_DARK)));
            }
            if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
                cell.addElement(new Paragraph(customer.getEmail(), font(9, Font.NORMAL, TEXT_DARK)));
            }
            if (customer.getGstin() != null && !customer.getGstin().isBlank()) {
                cell.addElement(new Paragraph("GSTIN: " + customer.getGstin(), font(9, Font.NORMAL, TEXT_MUTED)));
            }
        } else {
            cell.addElement(new Paragraph("Cash sale — no customer on record", font(9, Font.NORMAL, TEXT_MUTED)));
        }

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(60);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);
        table.addCell(cell);
        doc.add(table);
    }

    private void addItemsTable(Document doc, java.util.List<InvoiceItem> items) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4f, 1.5f, 2f, 2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(4);
        table.setHeaderRows(1);

        for (String h : new String[]{"Item", "Qty", "Unit Price", "Tax", "Line Total"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font(9, Font.BOLD, WHITE)));
            cell.setBackgroundColor(BRAND_BLUE);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        boolean alternate = false;
        for (InvoiceItem item : items) {
            Color bg = alternate ? LIGHT_HEADER : WHITE;
            addItemRow(table, bg,
                    item.getNameSnapshot() + (item.getSkuSnapshot() != null ? "\n" + item.getSkuSnapshot() : ""),
                    String.valueOf(item.getQuantity()),
                    "Rs. " + paiseToRupees(item.getUnitPricePaise()),
                    item.getTaxRate() > 0 ? item.getTaxRate() + "%" : "-",
                    "Rs. " + paiseToRupees(item.getTotalPaise()));
            alternate = !alternate;
        }

        doc.add(table);
    }

    private void addTotalsTable(Document doc, Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{6f, 2.5f});
        table.setWidthPercentage(55);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(2);
        table.setSpacingAfter(10);

        addTotalRow(table, "Subtotal", "Rs. " + paiseToRupees(invoice.getSubtotalPaise()));
        if (invoice.getDiscountPaise() > 0) {
            addTotalRow(table, "Discount", "- Rs. " + paiseToRupees(invoice.getDiscountPaise()));
        }
        if (invoice.getTaxPaise() > 0) {
            addTotalRow(table, "Tax", "Rs. " + paiseToRupees(invoice.getTaxPaise()));
        }

        PdfPCell labelCell = new PdfPCell(new Phrase("Total", font(10, Font.BOLD, WHITE)));
        labelCell.setBackgroundColor(BRAND_BLUE);
        labelCell.setPadding(7);
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase("Rs. " + paiseToRupees(invoice.getTotalPaise()), font(10, Font.BOLD, WHITE)));
        valueCell.setBackgroundColor(BRAND_BLUE);
        valueCell.setPadding(7);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);

        doc.add(table);
    }

    private void addFooter(Document doc) throws DocumentException {
        doc.add(spacer(16));
        Paragraph disclaimer = new Paragraph(
                "This is a computer-generated invoice and does not require a physical signature.",
                font(8, Font.ITALIC, TEXT_MUTED));
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        doc.add(disclaimer);
    }

    private void addDivider(Document doc) throws DocumentException {
        Paragraph line = new Paragraph(" ");
        line.setSpacingBefore(4);
        line.setSpacingAfter(4);
        doc.add(line);
    }

    private void addItemRow(PdfPTable table, Color bg, String... values) {
        boolean right = false;
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, font(9, Font.NORMAL, TEXT_DARK)));
            cell.setBackgroundColor(bg);
            cell.setPadding(5);
            cell.setBorderColor(DIVIDER_GRAY);
            cell.setBorderWidth(0.5f);
            if (right) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            right = true;
            table.addCell(cell);
        }
    }

    private void addTotalRow(PdfPTable table, String label, String value) {
        PdfPCell lc = new PdfPCell(new Phrase(label, font(9, Font.NORMAL, TEXT_DARK)));
        lc.setPadding(5);
        lc.setBorderColor(DIVIDER_GRAY);
        lc.setBorderWidth(0.5f);
        table.addCell(lc);

        PdfPCell vc = new PdfPCell(new Phrase(value, font(9, Font.NORMAL, TEXT_DARK)));
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

    private Paragraph spacer(float points) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(points);
        return p;
    }

    private Font font(int size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private String paiseToRupees(long paise) {
        return BigDecimal.valueOf(paise)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
