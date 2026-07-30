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
            document.add(new Paragraph("Date: " + invoice.getOrderDate(), normalFont));
            document.add(new Paragraph("Status: " + invoice.getStatus(), statusFont));
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
                    table.addCell(new Phrase("\u20b9 " + item.get("price"), normalFont));
                    table.addCell(new Phrase("\u20b9 " + item.get("subtotal"), normalFont));
                }
            }

            document.add(table);
            document.add(new Paragraph(" "));

            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(40);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.addCell(new Phrase("Subtotal:", normalFont));
            totalsTable.addCell(new Phrase("\u20b9 " + String.format("%.2f", invoice.getSubtotal()), normalFont));
            totalsTable.addCell(new Phrase("Tax (12%):", normalFont));
            totalsTable.addCell(new Phrase("\u20b9 " + String.format("%.2f", invoice.getTax()), normalFont));
            totalsTable.addCell(new Phrase("Delivery:", normalFont));
            totalsTable.addCell(new Phrase("\u20b9 " + String.format("%.2f", invoice.getDeliveryCharge()), normalFont));

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PdfBrandingUtil.TEAL);
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total:", totalFont));
            totalLabel.setBackgroundColor(PdfBrandingUtil.BEIGE);
            totalsTable.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase("\u20b9 " + String.format("%.2f", invoice.getTotalAmount()), totalFont));
            totalValue.setBackgroundColor(PdfBrandingUtil.BEIGE);
            totalsTable.addCell(totalValue);

            document.add(totalsTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF invoice", e);
        }
    }
}
