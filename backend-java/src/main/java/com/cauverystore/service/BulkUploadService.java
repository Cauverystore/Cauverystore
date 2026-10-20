package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class BulkUploadService {

    private final ProductRepository productRepo;
    private final CategoryRepository catRepo;
    private final ProductService productService;
    private final ListingEligibilityService listingEligibility;

    public BulkUploadService(ProductRepository productRepo, CategoryRepository catRepo, ProductService productService,
                             ListingEligibilityService listingEligibility) {
        this.listingEligibility = listingEligibility;
        this.productRepo = productRepo;
        this.catRepo = catRepo;
        this.productService = productService;
    }

    public Map<String, Object> uploadProducts(MultipartFile file) {
        return uploadProducts(file, null);
    }

    public Map<String, Object> uploadProducts(MultipartFile file, Long sellerId) {
        Map<String, Object> result = new HashMap<>();
        int success = 0;
        List<String> failures = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            if (rows.hasNext()) {
                rows.next(); // skip header
            }

            while (rows.hasNext()) {
                Row row = rows.next();
                try {
                    processRow(row, sellerId);
                    success++;
                } catch (Exception e) {
                    failures.add("Row " + (row.getRowNum() + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.put("error", "Failed to process file: " + e.getMessage());
            return result;
        }

        if (success > 0) {
            productService.evictProductCache();
        }

        result.put("success", success);
        result.put("failureCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    private void processRow(Row row, Long sellerId) {
        String name = getCellString(row, 0);
        String categoryName = getCellString(row, 1);
        String description = getCellString(row, 2);
        double price = getCellDouble(row, 3);
        int stock = getCellInt(row, 4);
        String brand = getCellString(row, 5);
        String manufacturer = getCellString(row, 6);
        String modelNumber = getCellString(row, 7);
        String barcode = getCellString(row, 8);
        String hsnCode = getCellString(row, 9);
        String countryOfOrigin = getCellString(row, 10);
        String warranty = getCellString(row, 11);

        if (name == null || name.isBlank()) {
            throw new RuntimeException("Product name is required");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new RuntimeException("Category is required");
        }

        // Staff uploads may still add a category on the fly. A seller's may not: a made-up name
        // would be a way round a category's licence rule, and the store owns its categories.
        Category category = catRepo.findByName(categoryName)
                .orElseGet(() -> {
                    if (sellerId != null) {
                        throw new RuntimeException("Category \"" + categoryName + "\" does not exist. Use a category name from the list on the site.");
                    }
                    Category cat = new Category();
                    cat.setName(categoryName);
                    return catRepo.save(cat);
                });
        if (sellerId != null && listingEligibility != null) {
            listingEligibility.check(sellerId, category, brand);
        }

        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setBrand(brand);
        product.setManufacturer(manufacturer);
        product.setModelNumber(modelNumber);
        product.setBarcode(barcode);
        product.setHsnCode(hsnCode);
        product.setCountryOfOrigin(countryOfOrigin);
        product.setWarranty(warranty);
        product.setActive(true);
        if (sellerId != null) {
            product.setSellerId(sellerId);
        }

        productRepo.save(product);
    }

    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private double getCellDouble(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        try {
            return Double.parseDouble(cell.getStringCellValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int getCellInt(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        try {
            return Integer.parseInt(cell.getStringCellValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
