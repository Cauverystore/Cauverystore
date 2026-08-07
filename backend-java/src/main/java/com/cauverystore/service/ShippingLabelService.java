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
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private static final Logger log = LoggerFactory.getLogger(ShippingLabelService.class);

    // Statuses a courier label makes no sense for - nothing to ship, or nothing to ship yet.
    private static final List<String> UNLABELABLE_STATUSES = List.of("CANCELLED", "REFUNDED", "RETURNED");

    private final OrderRepository orderRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

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
        if (UNLABELABLE_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException("Cannot generate a shipping label for an order that is " + order.getStatus());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Single-page standard: a 4x6 inch courier label (72pt/inch), portrait, 12pt margins.
        //
        // Every section is bounded so the label fits one page whatever the order contains - a
        // courier label that runs to two pages is a label that gets separated from its parcel.
        //
        // That bound does cost detail, deliberately: past the third item the rest collapse into
        // a "+N more" line, and long product names are truncated. A label exists to get the
        // parcel to the right door, and the packing list it points at is the invoice. Anything
        // the recipient needs to check contents against belongs there, not here.
        Document doc = new Document(new Rectangle(4 * 72f, 6 * 72f), 12, 12, 12, 12);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        Font bold13 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font bold11 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font bold9 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font normal9 = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small8 = FontFactory.getFont(FontFactory.HELVETICA, 8);

        doc.add(PdfBrandingUtil.buildBrandHeader(34f, 12f, 7.5f));
        doc.add(PdfBrandingUtil.buildDivider());

        // COD banner, slim so it never costs a page on its own.
        if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
            PdfPTable codBadge = new PdfPTable(1);
            codBadge.setWidthPercentage(100);
            codBadge.setSpacingBefore(4);
            PdfPCell codCell = new PdfPCell(new Phrase("CASH ON DELIVERY",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE)));
            codCell.setBackgroundColor(PdfBrandingUtil.RED);
            codCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            codCell.setPadding(3);
            codBadge.addCell(codCell);
            doc.add(codBadge);
        }

        // Ship From (Return Address) / Ship To
        SellerRegistration seller = order.getSellerId() != null
                ? sellerRegRepo.findByUserId(order.getSellerId()).orElse(null)
                : null;
        Address addr = order.getAddress();

        PdfPTable addresses = new PdfPTable(2);
        addresses.setWidthPercentage(100);
        addresses.setWidths(new float[]{50, 50});
        addresses.setSpacingBefore(4);

        PdfPCell fromCell = new PdfPCell();
        fromCell.setPadding(3);
        fromCell.setBackgroundColor(PdfBrandingUtil.BEIGE);
        fromCell.addElement(p("RETURN ADDRESS", bold9));
        if (seller != null) {
            fromCell.addElement(p(safe(seller.getBusinessName()), normal9));
            fromCell.addElement(p(safe(seller.getBusinessAddress()), small8));
            fromCell.addElement(p("Ph: " + safe(seller.getBusinessPhone()), small8));
        } else {
            fromCell.addElement(p("Cauvery Store Fulfillment Center", normal9));
        }
        addresses.addCell(fromCell);

        PdfPCell toCell = new PdfPCell();
        toCell.setPadding(3);
        toCell.addElement(p("SHIP TO", bold9));
        if (addr != null) {
            toCell.addElement(p(safe(addr.getFullName()), bold11));
            toCell.addElement(p(safe(addr.getStreet()), normal9));
            toCell.addElement(p(safe(addr.getCity()) + ", " + safe(addr.getState()) + " - " + safe(addr.getPincode()), normal9));
            toCell.addElement(p("Ph: " + safe(addr.getPhone()), small8));
        } else {
            toCell.addElement(p("Address not available", normal9));
        }
        addresses.addCell(toCell);
        doc.add(addresses);

        // Order ID / Service type
        PdfPTable idTable = new PdfPTable(2);
        idTable.setWidthPercentage(100);
        idTable.setWidths(new float[]{50, 50});
        idTable.setSpacingBefore(4);
        addIdCell(idTable, "Order ID", "#" + order.getId(), bold9, bold13);
        String serviceType = "COD".equalsIgnoreCase(order.getPaymentMethod()) ? "COD" : "Prepaid - Standard";
        addIdCell(idTable, "Service Type", serviceType, bold9, bold11);
        doc.add(idTable);

        // Itemized product list - capped at three rows so a large order cannot push the label
        // onto a second page; the rest folds into a "+N more" line.
        List<OrderItem> items = order.getItems();
        int itemCount = items == null ? 0 : items.size();
        double totalWeight = 0;
        boolean anyWeight = false;
        boolean anyFragile = false;
        if (items != null) {
            for (OrderItem item : items) {
                if (item.getProduct() != null) {
                    Double w = item.getProduct().getPackageWeight() != null ? item.getProduct().getPackageWeight() : item.getProduct().getWeight();
                    if (w != null) {
                        totalWeight += w * item.getQuantity();
                        anyWeight = true;
                    }
                    String shippingClass = item.getProduct().getShippingClass();
                    if (shippingClass != null && shippingClass.toLowerCase().contains("fragile")) {
                        anyFragile = true;
                    }
                }
            }
        }

        PdfPTable itemsTable = new PdfPTable(2);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{75, 25});
        itemsTable.setSpacingBefore(4);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        PdfPCell itemHeader1 = new PdfPCell(new Phrase("Product", tableHeaderFont));
        itemHeader1.setBackgroundColor(PdfBrandingUtil.TEAL);
        itemHeader1.setPadding(2);
        PdfPCell itemHeader2 = new PdfPCell(new Phrase("Qty", tableHeaderFont));
        itemHeader2.setBackgroundColor(PdfBrandingUtil.TEAL);
        itemHeader2.setPadding(2);
        itemHeader2.setHorizontalAlignment(Element.ALIGN_CENTER);
        itemsTable.addCell(itemHeader1);
        itemsTable.addCell(itemHeader2);

        for (int i = 0; i < Math.min(itemCount, 3); i++) {
            OrderItem item = items.get(i);
            String name = truncate(item.getProduct() != null ? item.getProduct().getName() : "Item", 42);
            PdfPCell nameCell = new PdfPCell(new Phrase(safe(name), small8));
            nameCell.setPadding(2);
            itemsTable.addCell(nameCell);
            PdfPCell qtyCell = new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), small8));
            qtyCell.setPadding(2);
            qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            itemsTable.addCell(qtyCell);
        }
        if (itemCount > 3) {
            PdfPCell moreCell = new PdfPCell(new Phrase(
                    "+ " + (itemCount - 3) + " more item" + (itemCount - 3 > 1 ? "s" : "") + " - see invoice", small8));
            moreCell.setColspan(2);
            moreCell.setPadding(2);
            itemsTable.addCell(moreCell);
        }
        doc.add(itemsTable);

        // Weight / fragile / delivery instructions share one banner line, keeping them off page two.
        String deliveryInstructions = addr != null ? addr.getDeliveryInstructions() : null;
        if (anyWeight || anyFragile || (deliveryInstructions != null && !deliveryInstructions.isBlank())) {
            StringBuilder parts = new StringBuilder();
            if (anyWeight) parts.append("Weight: ").append(String.format("%.2f kg", totalWeight));
            if (anyFragile) {
                if (parts.length() > 0) parts.append("   ");
                parts.append("FRAGILE - HANDLE WITH CARE");
            }
            if (deliveryInstructions != null && !deliveryInstructions.isBlank()) {
                if (parts.length() > 0) parts.append("   ");
                parts.append(truncate(deliveryInstructions, 60));
            }
            PdfPTable noteTable = new PdfPTable(1);
            noteTable.setWidthPercentage(100);
            noteTable.setSpacingBefore(4);
            PdfPCell noteCell = new PdfPCell(p(parts.toString(),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
            noteCell.setPadding(2);
            noteCell.setBorderColor(PdfBrandingUtil.RED);
            noteCell.setBorderWidth(0.6f);
            noteTable.addCell(noteCell);
            doc.add(noteTable);
        }

        // Barcodes - order barcode for internal tracking, tracking barcode for courier scanning
        PdfContentByte cb = writer.getDirectContent();
        PdfPTable barcodeTable = new PdfPTable(2);
        barcodeTable.setWidthPercentage(100);
        barcodeTable.setWidths(new float[]{50, 50});
        barcodeTable.setSpacingBefore(4);

        barcodeTable.addCell(buildBarcodeCell(cb, "ORDER BARCODE", "ORD" + order.getId(), bold9, small8));

        String tracking = order.getTrackingNumber();
        String courier = order.getCourier() != null ? order.getCourier() : "Not assigned";
        if (tracking != null && !tracking.isBlank()) {
            barcodeTable.addCell(buildBarcodeCell(cb, "COURIER: " + courier, tracking, bold9, small8));
        } else {
            PdfPCell pendingCell = new PdfPCell();
            pendingCell.setPadding(4);
            pendingCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pendingCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            pendingCell.addElement(new Paragraph("TRACKING", bold9));
            pendingCell.addElement(new Paragraph("Awaiting courier assignment", small8));
            barcodeTable.addCell(pendingCell);
        }
        doc.add(barcodeTable);

        doc.close();
        byte[] pdfBytes = out.toByteArray();

        recordLabelGenerated(order);

        return pdfBytes;
    }

    /** Persists that this label was generated and logs it, without letting a failure here block the PDF that was already built. */
    private void recordLabelGenerated(Order order) {
        try {
            order.setLabelGeneratedAt(LocalDateTime.now());
            orderRepo.save(order);
            Long actorId = authorizationService.getCurrentUserId();
            String actorEmail = authorizationService.getCurrentUserEmail();
            auditService.log(actorId, actorEmail, "SHIPPING_LABEL_GENERATED", "Order", order.getId(),
                    "Shipping label generated for order #" + order.getId(), null);
        } catch (Exception e) {
            log.warn("Failed to record shipping label generation for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private PdfPCell buildBarcodeCell(PdfContentByte cb, String label, String code, Font labelFont, Font smallFont) {
        Barcode128 barcode = new Barcode128();
        barcode.setCode(code);
        barcode.setBarHeight(16f);
        barcode.setSize(4.5f);
        barcode.setBaseline(4f);
        barcode.setTextAlignment(Element.ALIGN_CENTER);
        Image barcodeImage = barcode.createImageWithBarcode(cb, null, null);

        PdfPCell cell = new PdfPCell();
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.addElement(p(label, labelFont));
        com.lowagie.text.Chunk imageChunk = new com.lowagie.text.Chunk(barcodeImage, 0, 0);
        Paragraph imgParagraph = new Paragraph();
        imgParagraph.add(imageChunk);
        imgParagraph.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(imgParagraph);
        return cell;
    }

    private void addIdCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(3);
        cell.addElement(p(label, labelFont));
        cell.addElement(p(value, valueFont));
        table.addCell(cell);
    }

    /** Compact paragraph - leading set to 1.15x the font size so the label stays dense. */
    private Paragraph p(String text, Font font) {
        return new Paragraph(font.getCalculatedLeading(1.15f), text, font);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return safe(s);
        }
        return s.substring(0, max - 3) + "...";
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }
}
