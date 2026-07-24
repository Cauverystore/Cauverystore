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
    private static final String[] HEADERS = {
        "Product Code", "Product Name", "Brand", "Category", "Price (₹)", "Stock Quantity",
        "Description", "Manufacturer", "Model Number", "Barcode/UPC", "HSN Code",
        "Country of Origin", "Short Description", "Technical Specifications",
        "Warranty", "Return Policy", "Meta Title", "Meta Description",
        "Keywords", "Slug", "Weight (kg)", "Dimensions (LxWxH cm)",
        "Shipping Class", "Delivery Days", "Badges",
        "Primary Image URL", "Additional Images"
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

        // Sample data rows
        String[][] samples = {
            {"SNG-S24-001","Samsung Galaxy S24 Ultra","Samsung","Electronics","129999","50",
             "Samsung Galaxy S24 Ultra 5G smartphone with 200MP camera, S Pen, Titanium frame",
             "Samsung Electronics","SM-S928B","8806095432100","85171200","South Korea",
             "Flagship smartphone with AI features","Display: 6.8\" QHD+; Processor: Snapdragon 8 Gen 3; RAM: 12GB; Storage: 512GB; Camera: 200MP; Battery: 5000mAh",
             "1 Year","7 Days","Samsung Galaxy S24 Ultra - Best Price Online","Shop Samsung Galaxy S24 Ultra at the best price with free delivery. 200MP camera, S Pen, AI features included.",
             "samsung,s24,ultra,smartphone,5g,ai","samsung-galaxy-s24-ultra","0.233","16.2x7.9x0.86",
             "Standard","3","Best Seller",
             "https://picsum.photos/seed/s24ultra/400/400","https://picsum.photos/seed/s24ultra2/400/400,https://picsum.photos/seed/s24ultra3/400/400"},

            {"XIA-14-002","Xiaomi 14 Pro","Xiaomi","Electronics","69999","30",
             "Xiaomi 14 Pro with Leica optics, Snapdragon 8 Gen 3, 120W charging",
             "Xiaomi Inc.","2311DRK48G","6934177756230","85171200","China",
             "Leica-powered flagship with hypercharge","Display: 6.73\" LTPO AMOLED; Processor: Snapdragon 8 Gen 3; RAM: 12GB; Camera: 50MP Leica; Battery: 4880mAh; Charging: 120W",
             "1 Year","7 Days","Xiaomi 14 Pro - Leica Camera Phone at Best Price","Buy Xiaomi 14 Pro with Leica optics, Snapdragon 8 Gen 3 processor, and 120W hypercharge online.",
             "xiaomi,14pro,leica,smartphone,5g","xiaomi-14-pro","0.223","16.1x7.5x0.85",
             "Standard","2","New Arrival",
             "https://picsum.photos/seed/xiaomi14/400/400","https://picsum.photos/seed/xiaomi14a/400/400"},

            {"SNY-WH5-003","Sony WH-1000XM5 Headphones","Sony","Electronics","29990","60",
             "Industry-leading noise cancellation with Auto NC Optimizer, crystal clear hands-free calling",
             "Sony Corporation","WH1000XM5","0272429261751","85183000","Japan",
             "Premium wireless noise-cancelling headphones","Driver: 30mm; Frequency: 4Hz-40kHz; ANC: Auto NC Optimizer; Battery: 30hrs; Charging: USB-C; Codec: LDAC, AAC",
             "1 Year","10 Days","Sony WH-1000XM5 Wireless Headphones - Best ANC","Shop Sony WH-1000XM5 industry-leading noise cancellation headphones. 30hr battery, crystal clear calls.",
             "sony,headphones,noise-cancelling,wireless,anc","sony-wh1000xm5","0.250","18x15x8",
             "Standard","4","Best Seller",
             "https://picsum.photos/seed/sonywh5/400/400", ""},

            {"LG-OLED-004","LG C4 65-inch OLED TV","LG","Electronics","164990","12",
             "LG C4 OLED evo 4K Smart TV with α9 AI Processor, Dolby Vision, Dolby Atmos",
             "LG Electronics","OLED65C4PUA","8806091234567","85287200","South Korea",
             "65-inch OLED 4K Smart TV with AI processor","Display: 65\" OLED evo; Resolution: 4K; Processor: α9 Gen6 AI; HDMI: 4x; OS: webOS; Audio: 40W Dolby Atmos",
             "1 Year","7 Days","LG C4 65 inch OLED TV - Lowest Price Guaranteed","Buy LG C4 65-inch OLED evo 4K Smart TV. α9 AI Processor, Dolby Vision, Free Delivery.",
             "lg,oled,tv,4k,smart,65inch","lg-c4-65-oled-tv","24.5","144.1x82.6x4.6",
             "Heavy","7","Limited Offer",
             "https://picsum.photos/seed/lgc4/400/400", ""},

            {"PHL-TRIM-005","Philips OneBlade Trimmer","Philips","Electronics","5499","100",
             "Philips OneBlade Hybrid trimmer and shaver, 45 min battery, washable",
             "Philips India","QP6530/15","8710103123456","85101000","India",
             "Hybrid electric trimmer and shaver","Blade: OneBlade dual-sided; Battery: 45min; Charging: 8hrs; Waterproof: Yes; Attachment: 3 combs",
             "2 Years","15 Days","Philips OneBlade Trimmer - Shave & Trim Easily","Philips OneBlade hybrid trimmer shaver. 45-min battery, washable, perfect for face and body grooming.",
             "philips,trimmer,shaver,grooming,oneblade","philips-oneblade-trimmer","0.160","12x5x4",
             "Standard","3","Discount",
             "https://picsum.photos/seed/philips1/400/400", "https://picsum.photos/seed/philips2/400/400"},
        };

        for (int r = 0; r < samples.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < samples[r].length && c < HEADERS.length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(samples[r][c]);
                cell.setCellStyle(sampleStyle);
            }
        }

        // Column widths
        int[] widths = {3200,5000,3000,4000,3000,2800,8000,4000,3500,3500,3000,3500,5000,6000,2800,2800,6000,7000,4000,4000,2500,3500,3000,2800,5000,7000,7000};
        for (int i = 0; i < widths.length && i < HEADERS.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }

        // Validations
        DataValidationHelper dv = sheet.getDataValidationHelper();

        addDropdown(sheet, dv, catNames, 1, 1000, 3, 3, "Select a category");
        addDropdown(sheet, dv, COUNTRIES, 1, 1000, 11, 11, "Select country");
        addDropdown(sheet, dv, WARRANTY, 1, 1000, 14, 14, "Select warranty");
        addDropdown(sheet, dv, RETURN_POLICY, 1, 1000, 15, 15, "Select return policy");
        addDropdown(sheet, dv, SHIPPING_CLASS, 1, 1000, 22, 22, "Select shipping class");
        addDropdown(sheet, dv, BADGES, 1, 1000, 24, 24, "Select badge");

        // Freeze top row
        sheet.createFreezePane(0, 1);

        // Instructions sheet
        Sheet instructions = wb.createSheet("Instructions");
        Row iRow1 = instructions.createRow(0);
        iRow1.createCell(0).setCellValue("BULK PRODUCT UPLOAD INSTRUCTIONS");
        iRow1.getCell(0).setCellStyle(headerStyle);

        String[] instructionsText = {
            "",
            "1. Do NOT modify or remove the header row (Row 1). Upload will fail if headers are changed.",
            "2. Product Code is required — use a unique code per product (e.g., BRAND-MODEL-001).",
            "3. Category, Country of Origin, Warranty, Return Policy, Shipping Class, and Badges have dropdown validation.",
            "4. Price must be in Indian Rupees (₹). Do not include the ₹ symbol in the cell — it's formatted automatically.",
            "5. Stock Quantity must be a whole number (integer).",
            "6. Short Description is limited to 150 characters.",
            "7. Meta Title is limited to 60 characters. Meta Description is limited to 160 characters.",
            "8. Technical Specifications should be semi-colon separated (e.g., \"RAM: 8GB; Storage: 256GB\").",
            "9. Weight must be in kilograms (e.g., 0.250 for 250g).",
            "10. Dimensions format: Length x Width x Height in cm (e.g., 16.2x7.9x0.86).",
            "11. Slug should be lowercase with hyphens (auto-generated from product name).",
             "12. Primary Image URL (Column Y) is optional — provide a direct URL to the product image (JPG/PNG/WebP). Images will be downloaded and stored automatically.",
             "13. Additional Images (Column Z) is optional — add comma-separated URLs for extra product images.",
             "14. Save the file as .xlsx format before uploading.",
             "15. Maximum 1000 products per upload.",
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
