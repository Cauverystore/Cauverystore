package com.cauverystore.controller;

import com.cauverystore.entities.Category;
import com.cauverystore.repository.CategoryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ExcelTemplateController {

    private final CategoryRepository categoryRepo;

    private static final String[] CATEGORIES = {"Electronics","Fashion","Home & Kitchen","Grocery","Beauty","Appliances","Books","Sports & Fitness","Toys & Games"};
    private static final String[] COUNTRIES = {"India","USA","China","Japan","South Korea","Germany","Vietnam","Taiwan","Other"};
    private static final String[] WARRANTY = {"6 Months","1 Year","2 Years","3 Years","No Warranty"};
    private static final String[] RETURN_POLICY = {"7 Days","10 Days","15 Days","No Returns"};
    private static final String[] SHIPPING_CLASS = {"Standard","Express","Heavy","Fragile"};
    private static final String[] BADGES = {"None","New Arrival","Best Seller","Limited Offer","Discount"};
    private static final String[] PRE_PACKAGED = {"Yes","No"};
    private static final String[] HEADERS = {
        "Product Code (leave empty)", "Product Name", "Brand", "Category", "Price (₹)", "Stock Quantity",
        "Description", "Manufacturer", "Model Number", "Barcode/UPC", "HSN Code",
        "Country of Origin", "Short Description", "Technical Specifications",
        "Warranty", "Return Policy", "Meta Title", "Meta Description",
        "Keywords", "Slug", "Weight (kg)", "Dimensions (LxWxH cm)",
        "Shipping Class", "Delivery Days", "Badges",
        "Image 1 URL", "Image 2 URL", "Image 3 URL",
        "Pre-Packaged and Labelled", "GST Rate Line ID"
    };

    public ExcelTemplateController(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    @GetMapping({"/admin/template.xlsx", "/seller/template.xlsx"})
    public ResponseEntity<byte[]> downloadTemplate() throws Exception {
        List<Category> categories = categoryRepo.findAll();
        String[] catNames = categories.stream().map(Category::getName).toArray(String[]::new);
        if (catNames.length == 0) catNames = CATEGORIES;

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Product Upload Template");

        // Styles
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle sampleStyle = createSampleStyle(wb);
        CellStyle priceStyle = wb.createCellStyle();
        priceStyle.setDataFormat(wb.createDataFormat().getFormat("₹#,##0.00"));

        // Headers
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(headerStyle);
        }

        // The upload sheet holds only the headings: example rows here were uploaded as real
        // products by mistake. One worked example lives on its own sheet, which upload never reads.
        String[] example = {"","Stainless Steel Airtight Container, 1000 ml","Example Brand","Home & Kitchen","299","25",
             "A 1000 ml stainless steel container with a lid for storing dry kitchen items.\n- 1000 ml capacity\n- Stainless steel body\n- Airtight lid",
             "Example Manufacturer","","","7323","India",
             "1000 ml stainless steel container with lid for dry kitchen items.","Capacity: 1000 ml; Material: Stainless steel; Lid: Yes",
             "","7 Days","Stainless Steel Airtight Container 1000 ml","Buy a 1000 ml stainless steel airtight container with lid for snacks, spices and dry kitchen items.",
             "steel container,airtight container,kitchen storage","","0.42","12x12x15",
             "Standard","","",
             "","","",
             "Yes",""};
        Sheet exampleSheet = wb.createSheet(EXAMPLE_SHEET);
        Row exHeader = exampleSheet.createRow(0);
        Row exRow = exampleSheet.createRow(1);
        for (int c = 0; c < HEADERS.length; c++) {
            Cell h = exHeader.createCell(c);
            h.setCellValue(HEADERS[c]);
            h.setCellStyle(headerStyle);
            Cell cell = exRow.createCell(c);
            cell.setCellValue(c < example.length ? example[c] : "");
            cell.setCellStyle(sampleStyle);
        }
        exampleSheet.createRow(3).createCell(0).setCellValue(
            "This sheet is only an example and is never uploaded. Type your products on the first sheet, \"Product Upload Template\".");

        // Column widths
        int[] widths = {3200,5000,3000,4000,3000,2800,8000,4000,3500,3500,3000,3500,5000,6000,2800,2800,6000,7000,4000,4000,2500,3500,3000,2800,5000,7000,7000,7000,3200,3200};
        for (int i = 0; i < widths.length && i < HEADERS.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
            exampleSheet.setColumnWidth(i, widths[i]);
        }

        // Validations
        DataValidationHelper dv = sheet.getDataValidationHelper();

        addCategoryDropdown(wb, sheet, dv, catNames);
        addDropdown(sheet, dv, COUNTRIES, 1, 1000, 11, 11, "Select country");
        addDropdown(sheet, dv, WARRANTY, 1, 1000, 14, 14, "Select warranty");
        addDropdown(sheet, dv, RETURN_POLICY, 1, 1000, 15, 15, "Select return policy");
        addDropdown(sheet, dv, SHIPPING_CLASS, 1, 1000, 22, 22, "Select shipping class");
        addDropdown(sheet, dv, BADGES, 1, 1000, 24, 24, "Select badge");
        addDropdown(sheet, dv, PRE_PACKAGED, 1, 1000, 28, 28, "Select Yes/No");

        // Freeze top row
        sheet.createFreezePane(0, 1);

        // Instructions sheet
        Sheet instructions = wb.createSheet("Instructions");
        Row iRow1 = instructions.createRow(0);
        iRow1.createCell(0).setCellValue("BULK PRODUCT UPLOAD INSTRUCTIONS");
        iRow1.getCell(0).setCellStyle(headerStyle);

        String[] instructionsText = {
            "",
            "1. Do NOT modify or remove the header row (Row 1). Upload will fail if headers are changed. Type one product per row from Row 2. Only the first sheet is uploaded; the 'Example (not uploaded)' sheet shows a filled-in row for reference.",
            "2. Product Code must be left EMPTY - the store creates a unique code for every product automatically. If any row has a Product Code, the whole file is refused and nothing is uploaded.",
            "3. Category, Country of Origin, Warranty, Return Policy, Shipping Class, and Badges have dropdown validation. Pick the Category from the dropdown - it lists the store's categories. If the store has an 'Others' category, a row with an empty or unknown category is put there (change it later by editing the product); otherwise that row is refused.",
            "4. Price must be in Indian Rupees (₹). Do not include the ₹ symbol in the cell — it's formatted automatically.",
            "5. Stock Quantity must be a whole number (integer).",
            "6. Short Description is limited to 150 characters.",
            "7. Meta Title is limited to 60 characters. Meta Description is limited to 160 characters.",
            "8. Technical Specifications should be semi-colon separated (e.g., \"RAM: 8GB; Storage: 256GB\").",
            "9. Weight must be in kilograms (e.g., 0.250 for 250g).",
            "10. Dimensions format: Length x Width x Height in cm (e.g., 16.2x7.9x0.86).",
            "11. Slug should be lowercase with hyphens (auto-generated from product name).",
             "12. Image 1 URL, Image 2 URL and Image 3 URL (Columns Z, AA, AB) are optional - one photo link per column. Image 1 becomes the main photo. Use a direct link that opens just the picture (JPG/PNG/WebP); the photos are downloaded and stored automatically. You can also add photos later with the Images button on the Products page.",
             "13. Leave the photo columns empty if you have no links; a product can be saved without photos.",
             "14. Pre-Packaged and Labelled (Column AC) is required — answer Yes or No. It decides the GST rate for staples such as rice, flour and dairy, which are taxed at 5% when sold pre-packaged and labelled but nil (or at another rate) when loose. Answer Yes for anything shipped in a sealed, labelled pack.",
             "15. GST Rate Line ID (Column AD) is optional — leave blank unless the GST screen told you a specific published rate line for an HSN taxed at more than one rate, and gives you its id.",
             "16. Save the file as .xlsx format before uploading.",
             "17. Maximum 1000 products per upload.",
             ""
        };
        for (int r = 0; r < instructionsText.length; r++) {
            Row row = instructions.createRow(r + 2);
            row.createCell(0).setCellValue(instructionsText[r]);
        }
        instructions.setColumnWidth(0, 22000);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos); wb.close();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product-upload-template.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bos.toByteArray());
    }

    /**
     * Category names live on a hidden sheet and the dropdown points at that range. A list typed
     * straight into the dropdown is limited by Excel to 255 characters, which a growing store
     * passes at around 20 categories - after that Excel reports the file as damaged.
     */
    private void addCategoryDropdown(Workbook wb, Sheet sheet, DataValidationHelper dv, String[] catNames) {
        String[] names = java.util.Arrays.stream(catNames)
                .filter(n -> n != null && !n.isBlank()).sorted().toArray(String[]::new);
        if (names.length == 0) return;
        Sheet list = wb.createSheet(CATEGORY_LIST_SHEET);
        for (int i = 0; i < names.length; i++) list.createRow(i).createCell(0).setCellValue(names[i]);
        wb.setSheetHidden(wb.getSheetIndex(list), true);
        DataValidationConstraint c = dv.createFormulaListConstraint(
                "'" + CATEGORY_LIST_SHEET + "'!$A$1:$A$" + names.length);
        DataValidation v = dv.createValidation(c, new CellRangeAddressList(1, 1000, 3, 3));
        v.setShowErrorBox(true);
        v.createErrorBox("Select a category", "Please select a category from the dropdown.");
        sheet.addValidationData(v);
    }

    static final String CATEGORY_LIST_SHEET = "Category List";
    static final String EXAMPLE_SHEET = "Example (not uploaded)";

    private void addDropdown(Sheet sheet, DataValidationHelper dv, String[] options, int firstRow, int lastRow, int firstCol, int lastCol, String title) {
        DataValidationConstraint c = dv.createExplicitListConstraint(options);
        CellRangeAddressList r = new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
        DataValidation v = dv.createValidation(c, r);
        v.setShowErrorBox(true);
        v.createErrorBox(title, "Please select a value from the dropdown.");
        sheet.addValidationData(v);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle hs = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(IndexedColors.WHITE.getIndex());
        hs.setFont(f);
        hs.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        hs.setAlignment(HorizontalAlignment.CENTER);
        hs.setBorderBottom(BorderStyle.THIN);
        hs.setBorderTop(BorderStyle.THIN);
        return hs;
    }

    private CellStyle createSampleStyle(Workbook wb) {
        CellStyle ss = wb.createCellStyle();
        ss.setWrapText(true);
        ss.setVerticalAlignment(VerticalAlignment.TOP);
        ss.setBorderBottom(BorderStyle.THIN);
        return ss;
    }
}
