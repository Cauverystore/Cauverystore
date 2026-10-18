package com.cauverystore.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.cauverystore.dto.InvoiceResponse;
import com.cauverystore.util.PdfBrandingUtil;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Service
public class InvoicePdfService {

    public byte[] generatePdf(InvoiceResponse invoice) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(PdfBrandingUtil.buildBrandHeader());
            document.add(PdfBrandingUtil.buildDivider());
            document.add(new Paragraph(" "));

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, PdfBrandingUtil.TEAL);
            Paragraph title = new Paragraph("INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font statusFont = "CANCELLED".equalsIgnoreCase(invoice.getStatus())
                    ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PdfBrandingUtil.RED)
                    : normalFont;

            document.add(new Paragraph("Order ID: " + invoice.getOrderId(), normalFont));
            document.add(new Paragraph("Date: " + readableDate(invoice.getOrderDate()), normalFont));
            document.add(new Paragraph("Status: " + readableStatus(invoice.getStatus()), statusFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Customer Details", headerFont));
            document.add(new Paragraph("Name: " + invoice.getCustomerName(), normalFont));
            document.add(new Paragraph("Email: " + invoice.getCustomerEmail(), normalFont));
            if (invoice.getPhone() != null) {
                document.add(new Paragraph("Phone: " + invoice.getPhone(), normalFont));
            }
            document.add(new Paragraph("Address: " + invoice.getAddress(), normalFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setWidths(new float[]{4, 1, 2, 2});

            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            for (String col : new String[]{"Item", "Qty", "Price", "Subtotal"}) {
                PdfPCell cell = new PdfPCell(new Phrase(col, tableHeaderFont));
                cell.setBackgroundColor(PdfBrandingUtil.TEAL);
                cell.setPadding(5);
                table.addCell(cell);
            }

            List<Map<String, Object>> items = invoice.getItems();
            if (items != null) {
                for (Map<String, Object> item : items) {
                    table.addCell(new Phrase(String.valueOf(item.get("name")), normalFont));
                    table.addCell(new Phrase(String.valueOf(item.get("quantity")), normalFont));
                    table.addCell(new Phrase(money(item.get("price")), normalFont));
                    table.addCell(new Phrase(money(item.get("subtotal")), normalFont));
                }
            }

            document.add(table);
            document.add(new Paragraph(" "));

            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(40);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.addCell(new Phrase("Subtotal:", normalFont));
            totalsTable.addCell(new Phrase(money(invoice.getSubtotal()), normalFont));
            // Not "Tax (12%)": an order's lines can carry different rates - 5% on staples, 18%
            // on electronics - so naming a single rate here stated something untrue on a tax
            // document. The per-rate breakdown belongs on the GST invoice, which has the HSN
            // and place of supply to justify it.
            // On orders with GST-inclusive prices the tax is already inside the subtotal and
            // delivery, so it is shown under the total as "included" rather than as an addition.
            if (!invoice.isTaxIncluded()) {
                totalsTable.addCell(new Phrase("GST:", normalFont));
                totalsTable.addCell(new Phrase(money(invoice.getTax()), normalFont));
            }
            totalsTable.addCell(new Phrase("Delivery:", normalFont));
            totalsTable.addCell(new Phrase(money(invoice.getDeliveryCharge()), normalFont));
            if (invoice.getGiftWrapCharge() > 0) {
                totalsTable.addCell(new Phrase("Gift wrap:", normalFont));
                totalsTable.addCell(new Phrase(money(invoice.getGiftWrapCharge()), normalFont));
            }

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PdfBrandingUtil.TEAL);
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total:", totalFont));
            totalLabel.setBackgroundColor(PdfBrandingUtil.BEIGE);
            totalsTable.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase(money(invoice.getTotalAmount()), totalFont));
            totalValue.setBackgroundColor(PdfBrandingUtil.BEIGE);
            totalsTable.addCell(totalValue);
            if (invoice.isTaxIncluded()) {
                totalsTable.addCell(new Phrase("Includes GST of:", normalFont));
                totalsTable.addCell(new Phrase(money(invoice.getTax()), normalFont));
            }

            document.add(totalsTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF invoice", e);
        }
    }

    // The standard PDF fonts have no rupee glyph, so the rupee sign printed as nothing and amounts
    // appeared bare. "Rs." always prints.
    private static String money(Object amount) {
        double v = amount instanceof Number n ? n.doubleValue()
                : amount != null ? Double.parseDouble(amount.toString()) : 0;
        return String.format("Rs. %.2f", v);
    }

    // Order dates arrive as ISO timestamps ("2026-10-18T15:15:46.377063"); a customer reads
    // "18 Oct 2026, 3:15 PM".
    private static String readableDate(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return java.time.LocalDateTime.parse(iso)
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH));
        } catch (java.time.format.DateTimeParseException e) {
            return iso;
        }
    }

    // PLACED / OUT_FOR_DELIVERY are internal codes; print them as words.
    private static String readableStatus(String status) {
        if (status == null || status.isBlank()) return "";
        String words = status.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
