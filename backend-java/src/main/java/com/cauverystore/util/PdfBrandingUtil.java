package com.cauverystore.util;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.io.InputStream;

/**
 * Shared brand header/colors for every generated PDF document
 * (GST invoice, plain invoice, shipping label).
 */
public final class PdfBrandingUtil {

    public static final Color TEAL = new Color(14, 92, 92);    // #0E5C5C
    public static final Color GOLD = new Color(200, 162, 75);  // #C8A24B
    public static final Color BEIGE = new Color(245, 241, 234); // #F5F1EA
    public static final Color RED = new Color(217, 58, 42);    // #D93A2A

    private PdfBrandingUtil() {}

    /**
     * A two-column header row: logo on the left, "Cauvery Store" / tagline on the right.
     * Add this as the first element of the document.
     */
    public static PdfPTable buildBrandHeader() {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        try {
            header.setWidths(new float[]{25, 75});
        } catch (Exception ignored) { /* widths array size always matches columns here */ }

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        try (InputStream is = PdfBrandingUtil.class.getResourceAsStream("/branding/logo.jpg")) {
            if (is != null) {
                Image logo = Image.getInstance(is.readAllBytes());
                logo.scaleToFit(60, 60);
                logoCell.addElement(logo);
            }
        } catch (Exception ignored) {
            // If the logo can't be loaded, still render the document without it.
        }
        header.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEAL);
        Font taglineFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, GOLD);
        textCell.addElement(new Paragraph("Cauvery Store", brandFont));
        textCell.addElement(new Paragraph("Everyday Essentials, Delivered", taglineFont));
        header.addCell(textCell);

        return header;
    }

    /** A thin teal divider line, full width, for separating header from body. */
    public static PdfPTable buildDivider() {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(2f);
        cell.setBackgroundColor(TEAL);
        cell.setBorder(Rectangle.NO_BORDER);
        divider.addCell(cell);
        return divider;
    }
}
