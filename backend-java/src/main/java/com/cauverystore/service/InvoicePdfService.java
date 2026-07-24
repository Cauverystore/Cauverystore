package com.cauverystore.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.cauverystore.dto.InvoiceResponse;
import org.springframework.stereotype.Service;

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

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Paragraph title = new Paragraph("INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("Order ID: " + invoice.getOrderId(), normalFont));
            document.add(new Paragraph("Date: " + invoice.getOrderDate(), normalFont));
            document.add(new Paragraph("Status: " + invoice.getStatus(), normalFont));
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

            PdfPCell cell = new PdfPCell(new Phrase("Item", headerFont));
            table.addCell(cell);
            cell = new PdfPCell(new Phrase("Qty", headerFont));
            table.addCell(cell);
            cell = new PdfPCell(new Phrase("Price", headerFont));
            table.addCell(cell);
            cell = new PdfPCell(new Phrase("Subtotal", headerFont));
            table.addCell(cell);

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

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total:", totalFont));
            totalsTable.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase("\u20b9 " + String.format("%.2f", invoice.getTotalAmount()), totalFont));
            totalsTable.addCell(totalValue);

            document.add(totalsTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF invoice", e);
        }
    }
}
