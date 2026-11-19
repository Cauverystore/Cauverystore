package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductCompliance;
import com.cauverystore.entities.ProductWorkflowStatus;
import com.cauverystore.repository.ProductComplianceRepository;
import com.cauverystore.repository.ProductRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fill in the compliance details of many products from one spreadsheet, instead of opening the
 * Compliance form of each product in turn.
 *
 * The download lists the products (by default those not on sale) with whatever details they
 * already have; the upload saves each row exactly as the Compliance form would, and can then
 * take a row that now passes every check through submit, confirm, approve and publish.
 * An empty cell leaves that detail as it is, so a sheet can be filled a column at a time.
 */
@Service
public class ComplianceBulkService {

    // Headings, in sheet order. Upload finds columns by heading, so they may be moved.
    static final String ID = "Product ID (do not change)";
    static final String CODE = "Product Code";
    static final String NAME = "Product Name";
    static final String PRICE = "Selling Price (Rs)";
    static final String SKU = "SKU";
    static final String MRP = "MRP (Rs)";
    static final String NET = "Net Quantity";
    static final String UNIT = "Unit";
    static final String GROSS = "Gross Quantity";
    static final String MFR = "Manufacturer Name";
    static final String MFR_ADDR = "Manufacturer Address";
    static final String PACKER_SAME = "Packer same as Manufacturer (Yes/No)";
    static final String PACKER = "Packer Name";
    static final String PACKER_ADDR = "Packer Address";
    static final String IMPORTER = "Importer Name";
    static final String IMPORTER_ADDR = "Importer Address";
    static final String STATE = "State of Origin";
    static final String CARE_PHONE = "Customer Care Phone";
    static final String CARE_EMAIL = "Customer Care Email";
    static final String MIN_QTY = "Min Order Qty";
    static final String MAX_QTY = "Max Order Qty";
    static final String MISSING = "Still missing (when downloaded)";

    static final List<String> HEADERS = List.of(ID, CODE, NAME, PRICE, SKU, MRP, NET, UNIT, GROSS, MFR, MFR_ADDR,
            PACKER_SAME, PACKER, PACKER_ADDR, IMPORTER, IMPORTER_ADDR, STATE, CARE_PHONE, CARE_EMAIL,
            MIN_QTY, MAX_QTY, MISSING);

    /** The units the Compliance form offers, stored exactly as the form stores them. */
    static final List<String> UNITS = List.of("g", "kg", "ml", "l", "mm", "cm", "m", "sq m", "N (number)", "pair", "set");

    /** Reads a typed unit: any case, and the usual ways of writing "pieces". */
    static String unitOf(String typed) {
        String t = typed.trim().toLowerCase(Locale.ROOT);
        if (List.of("n", "n (number)", "no", "nos", "pc", "pcs", "piece", "pieces", "number").contains(t)) return "N (number)";
        if (t.equals("pairs")) return "pair";
        if (t.equals("sets")) return "set";
        if (t.equals("ltr") || t.equals("litre") || t.equals("liter")) return "l";
        if (t.equals("gm") || t.equals("gms") || t.equals("grams")) return "g";
        for (String u : UNITS) if (u.equals(t)) return u;
        throw new IllegalArgumentException("Unit '" + typed + "' is not one of " + String.join(", ", UNITS) + ".");
    }

    static final int MAX_ROWS = 1000;

    private final ProductRepository productRepo;
    private final ProductComplianceRepository complianceRepo;
    private final ProductComplianceService engine;
    private final ProductComplianceWorkflowService workflow;
    private final ProductService productService;
    private final Validator validator;
    private final TransactionTemplate tx;

    public ComplianceBulkService(ProductRepository productRepo, ProductComplianceRepository complianceRepo,
                                 ProductComplianceService engine, ProductComplianceWorkflowService workflow,
                                 ProductService productService, Validator validator,
                                 PlatformTransactionManager txManager) {
        this.productRepo = productRepo;
        this.complianceRepo = complianceRepo;
        this.engine = engine;
        this.workflow = workflow;
        this.productService = productService;
        this.validator = validator;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ download

    /** @param onlyNotLive true lists only products shoppers cannot see yet */
    public byte[] template(boolean onlyNotLive) throws Exception {
        List<Object[]> rows = tx.execute(status -> {
            List<Object[]> out = new ArrayList<>();
            productRepo.findAll().stream()
                    .filter(p -> !onlyNotLive || !ProductComplianceService.visibleToShoppers(p))
                    .sorted(Comparator.comparing(Product::getId))
                    .forEach(p -> {
                        ProductCompliance c = engine.findRecord(p.getId()).orElse(null);
                        String missing = engine.evaluate(p, c).issues().stream()
                                .map(ProductComplianceService.Issue::label).distinct().collect(Collectors.joining(", "));
                        out.add(rowFor(p, c, missing));
                    });
            return out;
        });
        return write(rows);
    }

    static Object[] rowFor(Product p, ProductCompliance c, String missing) {
        boolean has = c != null;
        return new Object[]{
                p.getId(), p.getProductCode(), p.getName(), p.getPrice(), p.getSku(), p.getMrp(),
                has ? c.getNetQuantity() : null, has ? c.getQuantityUnit() : null, has ? c.getGrossQuantity() : null,
                has && c.getManufacturerName() != null ? c.getManufacturerName() : p.getManufacturer(),
                has ? c.getManufacturerAddress() : null,
                has && c.getPackerSameAsManufacturer() != null ? (c.getPackerSameAsManufacturer() ? "Yes" : "No") : null,
                has ? c.getPackerName() : null, has ? c.getPackerAddress() : null,
                has ? c.getImporterName() : null, has ? c.getImporterAddress() : null,
                has ? c.getStateOfOrigin() : null, has ? c.getCustomerCarePhone() : null, has ? c.getCustomerCareEmail() : null,
                has ? c.getMinOrderQty() : null, has ? c.getMaxOrderQty() : null, missing};
    }

    static byte[] write(List<Object[]> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Compliance details");
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setWrapText(true);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle locked = wb.createCellStyle();
            locked.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            locked.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row h = sheet.createRow(0);
            h.setHeightInPoints(32);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = h.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(head);
            }
            Set<Integer> infoCols = Set.of(0, 1, 2, 3, HEADERS.size() - 1);
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object[] values = rows.get(r);
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    Object v = values[i];
                    if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (v != null) cell.setCellValue(String.valueOf(v));
                    if (infoCols.contains(i)) cell.setCellStyle(locked);
                }
            }
            int[] widths = {14, 14, 40, 12, 22, 10, 10, 8, 10, 28, 44, 16, 24, 40, 24, 40, 16, 18, 26, 10, 10, 60};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(3, 1);

            DataValidationHelper dv = sheet.getDataValidationHelper();
            int last = Math.max(rows.size(), 1) + 200;
            addList(sheet, dv, UNITS.toArray(String[]::new), HEADERS.indexOf(UNIT), last);
            addList(sheet, dv, new String[]{"Yes", "No"}, HEADERS.indexOf(PACKER_SAME), last);

            Sheet help = wb.createSheet("How to fill");
            String[] lines = {
                    "Fill in one row per product on the first sheet. Grey columns are for reference and are not read.",
                    "Leave a cell empty to keep what the product already has. Nothing is ever cleared by an empty cell.",
                    "Do not change Product ID: it decides which product a row updates.",
                    "SKU: your own stock code for the product. Each product needs a different one.",
                    "MRP: the maximum retail price printed on the pack, in rupees. It must be at least the selling price.",
                    "Net Quantity + Unit: what is in the pack, e.g. 2 and N (number) for two pieces, or 500 and ml.",
                    "Units: N (number) = pieces, pair, set, g, kg, ml, l, mm, cm, m, sq m.",
                    "Gross Quantity: the packed weight in grams, including the packaging.",
                    "Manufacturer Name and Address: as printed on the pack (required by law for goods sold online).",
                    "Packer: answer Yes if the manufacturer also packed it; otherwise fill in Packer Name and Address.",
                    "Importer Name and Address: only for imported goods.",
                    "On upload, tick 'Publish products that pass' to put every product that now passes all checks on sale.",
            };
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 120 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void addList(Sheet sheet, DataValidationHelper dv, String[] values, int col, int lastRow) {
        DataValidation v = dv.createValidation(dv.createExplicitListConstraint(values),
                new org.apache.poi.ss.util.CellRangeAddressList(1, lastRow, col, col));
        v.setShowErrorBox(true);
        sheet.addValidationData(v);
    }

    // ------------------------------------------------------------------ upload

    /** What one row asks to change. Null means "leave as it is". */
    record Changes(Long productId, Double mrp, BigDecimal net, String unit, BigDecimal gross,
                   String manufacturer, String manufacturerAddress, Boolean packerSame, String packer,
                   String packerAddress, String importer, String importerAddress, String state,
                   String carePhone, String careEmail, Integer minQty, Integer maxQty, String sku) {
        boolean empty() {
            return mrp == null && net == null && unit == null && gross == null && manufacturer == null
                    && manufacturerAddress == null && packerSame == null && packer == null && packerAddress == null
                    && importer == null && importerAddress == null && state == null && carePhone == null
                    && careEmail == null && minQty == null && maxQty == null;
        }
    }

    /** Reads every filled row. A row that cannot be read is returned as an error, not thrown. */
    static List<Object> parse(InputStream in) throws Exception {
        Workbook book;
        try {
            book = new XSSFWorkbook(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the file. Save it as an Excel .xlsx file and upload again.");
        }
        try (Workbook wb = book) {
            Sheet sheet = wb.getSheetAt(0);
            Row head = sheet.getRow(0);
            if (head == null) throw new IllegalArgumentException("The first sheet has no heading row.");
            Map<String, Integer> cols = new HashMap<>();
            for (Cell c : head) {
                String t = text(c);
                if (t != null) cols.put(t.toLowerCase(Locale.ROOT), c.getColumnIndex());
            }
            if (!cols.containsKey(ID.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("This is not the compliance sheet: the '" + ID
                        + "' column is missing. Download the compliance Excel and fill that in.");
            }
            List<Object> out = new ArrayList<>();
            int filled = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String idText = text(cell(row, cols, ID));
                if (idText == null) continue;
                if (++filled > MAX_ROWS) throw new IllegalArgumentException("Up to " + MAX_ROWS + " products per file.");
                try {
                    out.add(new RowOf(r + 1, readRow(row, cols, idText)));
                } catch (IllegalArgumentException e) {
                    out.add(new RowError(r + 1, e.getMessage()));
                }
            }
            return out;
        }
    }

    record RowOf(int row, Changes changes) {}
    record RowError(int row, String message) {}

    static Changes readRow(Row row, Map<String, Integer> cols, String idText) {
        Long id;
        try {
            id = (long) Double.parseDouble(idText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Product ID '" + idText + "' is not a number.");
        }
        Double mrp = number(row, cols, MRP);
        if (mrp != null && mrp <= 0) throw new IllegalArgumentException("MRP must be more than zero.");
        Double net = number(row, cols, NET);
        if (net != null && net <= 0) throw new IllegalArgumentException("Net Quantity must be more than zero.");
        Double gross = number(row, cols, GROSS);
        if (gross != null && gross <= 0) throw new IllegalArgumentException("Gross Quantity must be more than zero.");
        String unit = text(cell(row, cols, UNIT));
        if (unit != null) unit = unitOf(unit);
        String same = text(cell(row, cols, PACKER_SAME));
        Boolean packerSame = null;
        if (same != null) {
            if (same.equalsIgnoreCase("yes")) packerSame = true;
            else if (same.equalsIgnoreCase("no")) packerSame = false;
            else throw new IllegalArgumentException("Packer same as Manufacturer must be Yes or No, not '" + same + "'.");
        }
        return new Changes(id, mrp, net == null ? null : BigDecimal.valueOf(net), unit,
                gross == null ? null : BigDecimal.valueOf(gross),
                text(cell(row, cols, MFR)), text(cell(row, cols, MFR_ADDR)), packerSame,
                text(cell(row, cols, PACKER)), text(cell(row, cols, PACKER_ADDR)),
                text(cell(row, cols, IMPORTER)), text(cell(row, cols, IMPORTER_ADDR)),
                text(cell(row, cols, STATE)), phone(row, cols), text(cell(row, cols, CARE_EMAIL)),
                whole(row, cols, MIN_QTY), whole(row, cols, MAX_QTY), text(cell(row, cols, SKU)));
    }

    /** Copies the filled-in details onto the record; empty cells leave the record alone. */
    static void apply(Changes ch, ProductCompliance c) {
        if (ch.net() != null) c.setNetQuantity(ch.net());
        if (ch.unit() != null) c.setQuantityUnit(ch.unit());
        if (ch.gross() != null) c.setGrossQuantity(ch.gross());
        if (ch.manufacturer() != null) c.setManufacturerName(ch.manufacturer());
        if (ch.manufacturerAddress() != null) c.setManufacturerAddress(ch.manufacturerAddress());
        if (ch.packerSame() != null) c.setPackerSameAsManufacturer(ch.packerSame());
        if (ch.packer() != null) c.setPackerName(ch.packer());
        if (ch.packerAddress() != null) c.setPackerAddress(ch.packerAddress());
        if (ch.importer() != null) c.setImporterName(ch.importer());
        if (ch.importerAddress() != null) c.setImporterAddress(ch.importerAddress());
        if (ch.state() != null) c.setStateOfOrigin(ch.state());
        if (ch.carePhone() != null) c.setCustomerCarePhone(ch.carePhone());
        if (ch.careEmail() != null) c.setCustomerCareEmail(ch.careEmail());
        if (ch.minQty() != null) c.setMinOrderQty(ch.minQty());
        if (ch.maxQty() != null) c.setMaxOrderQty(ch.maxQty());
    }

    /**
     * Saves every row and reports what happened to each. A row that fails never stops the others.
     *
     * @param publish take each product that now passes every check through to on-sale
     */
    public Map<String, Object> importFile(MultipartFile file, boolean publish) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("The file is empty.");
        List<Map<String, Object>> results = new ArrayList<>();
        int saved = 0, published = 0, failed = 0;
        for (Object o : parse(file.getInputStream())) {
            Map<String, Object> r = new LinkedHashMap<>();
            if (o instanceof RowError e) {
                r.put("row", e.row());
                r.put("error", e.message());
                failed++;
                results.add(r);
                continue;
            }
            RowOf row = (RowOf) o;
            r.put("row", row.row());
            r.put("productId", row.changes().productId());
            try {
                Outcome out = importRow(row.changes(), publish);
                r.put("name", out.name());
                r.put("score", out.result().score());
                r.put("publishable", out.result().publishable());
                r.put("published", out.published());
                r.put("live", out.live());
                r.put("missing", out.result().issues().stream().filter(ProductComplianceService.Issue::critical)
                        .map(ProductComplianceService.Issue::label).distinct().toList());
                if (out.note() != null) r.put("note", out.note());
                saved++;
                if (out.published()) published++;
            } catch (RuntimeException e) {
                r.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                failed++;
            }
            results.add(r);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saved", saved);
        m.put("published", published);
        m.put("failed", failed);
        m.put("rows", results);
        return m;
    }

    record Outcome(String name, ProductComplianceService.Result result, boolean published, boolean live, String note) {}

    /**
     * Saving a row and publishing it run in separate transactions: a row that fails to save
     * changes nothing, and a product that saves but cannot publish keeps its new details.
     */
    Outcome importRow(Changes ch, boolean publish) {
        Outcome saved = tx.execute(status -> saveRow(ch));
        if (!publish || saved.live() || !saved.result().publishable()) return saved;
        String note = null;
        try {
            tx.executeWithoutResult(status -> publishThrough(ch.productId()));
        } catch (RuntimeException e) {
            note = "Details saved, but not published: " + e.getMessage();
        }
        String finalNote = note;
        return tx.execute(status -> {
            Product after = productRepo.findById(ch.productId()).orElseThrow();
            boolean nowLive = ProductComplianceService.visibleToShoppers(after);
            return new Outcome(after.getName(), engine.check(after.getId()), nowLive, nowLive, finalNote);
        });
    }

    Outcome saveRow(Changes ch) {
        Product p = productRepo.findById(ch.productId())
                .orElseThrow(() -> new IllegalArgumentException("No product with ID " + ch.productId() + "."));
        boolean newSku = ch.sku() != null && !ch.sku().equals(p.getSku());
        if (newSku && productRepo.existsBySkuIgnoreCaseAndIdNot(ch.sku(), p.getId())) {
            throw new IllegalArgumentException("SKU '" + ch.sku() + "' is already used by another product. "
                    + "Each product needs its own SKU.");
        }
        if (ch.mrp() != null && p.getPrice() != null && ch.mrp() < p.getPrice()) {
            throw new IllegalArgumentException("MRP " + ch.mrp() + " is below the selling price " + p.getPrice()
                    + ". The MRP must be at least the price the customer pays.");
        }
        if (ch.mrp() != null || newSku) {
            Product change = new Product();
            change.setActive(p.isActive());   // a bare Product is active; never flip it from here
            if (ch.mrp() != null) change.setMrp(ch.mrp());
            if (newSku) change.setSku(ch.sku());
            p = productService.updateProduct(p.getId(), change);
        }
        if (!ch.empty()) {
            ProductCompliance record = engine.recordFor(p);
            apply(ch, record);
            Set<ConstraintViolation<ProductCompliance>> bad = validator.validate(record);
            if (!bad.isEmpty()) {
                throw new IllegalArgumentException(bad.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
            }
            complianceRepo.save(record);
            workflow.onEdited(p, record);
        }
        ProductComplianceService.Result result = engine.check(p.getId());
        return new Outcome(p.getName(), result, false, ProductComplianceService.visibleToShoppers(p), null);
    }

    /** Runs the remaining workflow steps, from wherever the listing is, through to published. */
    void publishThrough(Long productId) {
        for (int guard = 0; guard < 5; guard++) {
            ProductCompliance record = engine.findRecord(productId).orElse(null);
            ProductWorkflowStatus s = ProductWorkflowStatus.from(record == null ? null : record.getStatus());
            String next = switch (s) {
                case DRAFT -> "submit";
                case SELLER_REVIEW -> "confirm";
                case ADMIN_REVIEW -> "approve";
                case APPROVED -> "publish";
                default -> null;
            };
            if (next == null) return;
            workflow.transition(productId, next, "Published from the compliance spreadsheet.");
        }
    }

    // ------------------------------------------------------------------ cell helpers

    private static Cell cell(Row row, Map<String, Integer> cols, String header) {
        Integer i = cols.get(header.toLowerCase(Locale.ROOT));
        return i == null ? null : row.getCell(i);
    }

    static String text(Cell c) {
        if (c == null) return null;
        String s = switch (c.getCellType()) {
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                yield d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> {
                try { yield c.getStringCellValue(); } catch (Exception e) { yield String.valueOf(c.getNumericCellValue()); }
            }
            default -> c.getStringCellValue();
        };
        s = s == null ? null : s.trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static Double number(Row row, Map<String, Integer> cols, String header) {
        String t = text(cell(row, cols, header));
        if (t == null) return null;
        try {
            return Double.parseDouble(t.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(header + " must be a number, not '" + t + "'.");
        }
    }

    private static Integer whole(Row row, Map<String, Integer> cols, String header) {
        Double d = number(row, cols, header);
        if (d == null) return null;
        if (d != Math.rint(d) || d < 1) throw new IllegalArgumentException(header + " must be a whole number of 1 or more.");
        return d.intValue();
    }

    /** Excel turns a typed phone number into a number; read it back without ".0" or exponent. */
    private static String phone(Row row, Map<String, Integer> cols) {
        Cell c = cell(row, cols, CARE_PHONE);
        if (c != null && c.getCellType() == CellType.NUMERIC) {
            return new BigDecimal(c.getNumericCellValue()).toPlainString();
        }
        return text(c);
    }
}
