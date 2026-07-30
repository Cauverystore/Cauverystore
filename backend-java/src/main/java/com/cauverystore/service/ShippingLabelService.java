package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.util.PdfBrandingUtil;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private final OrderRepository orderRepo;
    private final SellerRegistrationRepository sellerRegRepo;

    public byte[] generateLabelPdf(Long orderId) throws DocumentException {
        return generateLabelPdf(orderId, null);
    }

    /** When requestingSellerId is non-null, only that seller's own orders can be labeled. */
    public byte[] generateLabelPdf(Long orderId, Long requestingSellerId) throws DocumentException {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (requestingSellerId != null && !requestingSellerId.equals(order.getSellerId())) {
            throw new RuntimeException("Access denied: this order does not belong to you");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A6.rotate(), 18, 18, 18, 18);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font bold14 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font bold11 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font bold9 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font normal9 = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small8 = FontFactory.getFont(FontFactory.HELVETICA, 8);

        doc.add(PdfBrandingUtil.buildBrandHeader());
        doc.add(PdfBrandingUtil.buildDivider());
        doc.add(new Paragraph(" "));

        // COD badge, in brand red, only when relevant
        if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
            PdfPTable codBadge = new PdfPTable(1);
            codBadge.setWidthPercentage(100);
            PdfPCell codCell = new PdfPCell(new Phrase("CASH ON DELIVERY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
            codCell.setBackgroundColor(PdfBrandingUtil.RED);
            codCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            codCell.setPadding(5);
            codBadge.addCell(codCell);
            doc.add(codBadge);
            doc.add(new Paragraph(" "));
        }

        // Ship From / Ship To
        PdfPTable addresses = new PdfPTable(2);
        addresses.setWidthPercentage(100);
        addresses.setWidths(new float[]{50, 50});

        SellerRegistration seller = order.getSellerId() != null
                ? sellerRegRepo.findByUserId(order.getSellerId()).orElse(null)
                : null;

        PdfPCell fromCell = new PdfPCell();
        fromCell.setPadding(6);
        fromCell.setBackgroundColor(PdfBrandingUtil.BEIGE);
        fromCell.addElement(new Paragraph("SHIP FROM", bold9));
        if (seller != null) {
            fromCell.addElement(new Paragraph(safe(seller.getBusinessName()), normal9));
            fromCell.addElement(new Paragraph(safe(seller.getBusinessAddress()), small8));
            fromCell.addElement(new Paragraph("Ph: " + safe(seller.getBusinessPhone()), small8));
        } else {
            fromCell.addElement(new Paragraph("Cauvery Store Fulfillment Center", normal9));
        }
        addresses.addCell(fromCell);

        PdfPCell toCell = new PdfPCell();
        toCell.setPadding(6);
        Address addr = order.getAddress();
        toCell.addElement(new Paragraph("SHIP TO", bold9));
        if (addr != null) {
            toCell.addElement(new Paragraph(safe(addr.getFullName()), bold11));
            toCell.addElement(new Paragraph(safe(addr.getStreet()), normal9));
            toCell.addElement(new Paragraph(safe(addr.getCity()) + ", " + safe(addr.getState()) + " - " + safe(addr.getPincode()), normal9));
            toCell.addElement(new Paragraph("Ph: " + safe(addr.getPhone()), small8));
        } else {
            toCell.addElement(new Paragraph("Address not available", normal9));
        }
        addresses.addCell(toCell);
        doc.add(addresses);

        doc.add(new Paragraph(" "));

        // Order / courier identifiers
        PdfPTable idTable = new PdfPTable(2);
        idTable.setWidthPercentage(100);
        idTable.setWidths(new float[]{50, 50});
        addIdCell(idTable, "Order ID", "#" + order.getId(), bold9, bold14);
        addIdCell(idTable, "Items", String.valueOf(order.getItems() != null ? order.getItems().stream().mapToInt(OrderItem::getQuantity).sum() : 0), bold9, bold14);
        doc.add(idTable);

        doc.add(new Paragraph(" "));

        // Tracking / AWB block - large, bold, boxed
        PdfPTable trackingBox = new PdfPTable(1);
        trackingBox.setWidthPercentage(100);
        PdfPCell trackingLabelCell = new PdfPCell(new Phrase("COURIER / AWB NO.", bold9));
        trackingLabelCell.setBorder(Rectangle.NO_BORDER);
        trackingLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        trackingBox.addCell(trackingLabelCell);

        String courier = order.getCourier() != null ? order.getCourier() : "Not assigned";
        String tracking = order.getTrackingNumber() != null ? order.getTrackingNumber() : "PENDING";
        PdfPCell trackingCell = new PdfPCell(new Phrase(courier + "  |  " + tracking, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PdfBrandingUtil.TEAL)));
        trackingCell.setPadding(8);
        trackingCell.setBorderColor(PdfBrandingUtil.TEAL);
        trackingCell.setBorderWidth(1.5f);
        trackingCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        trackingBox.addCell(trackingCell);
        doc.add(trackingBox);

        doc.close();
        return out.toByteArray();
    }

    private void addIdCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }
}
