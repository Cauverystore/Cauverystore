package com.cauverystore.controller;

import com.cauverystore.repository.CategoryRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelTemplateControllerTest {

    @Test
    void template_shouldOfferThreePhotoColumnsBeforePackaging() throws Exception {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.findAll()).thenReturn(List.of());

        byte[] file = new ExcelTemplateController(repo).downloadTemplate().getBody();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("Image 1 URL", header.getCell(25).getStringCellValue());
            assertEquals("Image 2 URL", header.getCell(26).getStringCellValue());
            assertEquals("Image 3 URL", header.getCell(27).getStringCellValue());
            assertEquals("Pre-Packaged and Labelled", header.getCell(28).getStringCellValue());
            assertEquals("GST Rate Line ID", header.getCell(29).getStringCellValue());
            assertEquals("Product Code (leave empty)", header.getCell(0).getStringCellValue());
            // No example rows on the upload sheet: uploading the template as-is must add nothing.
            assertEquals(0, sheet.getLastRowNum(), "upload sheet must hold only the headings");

            // The example sits on its own sheet, lined up with the headings and with no code.
            Sheet example = wb.getSheet(ExcelTemplateController.EXAMPLE_SHEET);
            assertNotNull(example);
            assertNotEquals(0, wb.getSheetIndex(example));
            Row ex = example.getRow(1);
            assertEquals("", ex.getCell(0).getStringCellValue());
            assertEquals("Yes", ex.getCell(28).getStringCellValue());
            assertEquals("7323", ex.getCell(10).getStringCellValue());
        }
    }

    @Test
    void template_shouldOfferEveryCategoryHoweverManyThereAre() throws Exception {
        // 40 categories is far past the 255 characters Excel allows in a typed-in dropdown list.
        List<com.cauverystore.entities.Category> cats = new java.util.ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            com.cauverystore.entities.Category c = new com.cauverystore.entities.Category();
            c.setName("Category number " + i);
            cats.add(c);
        }
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.findAll()).thenReturn(cats);

        byte[] file = new ExcelTemplateController(repo).downloadTemplate().getBody();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet list = wb.getSheet(ExcelTemplateController.CATEGORY_LIST_SHEET);
            assertNotNull(list);
            assertTrue(wb.isSheetHidden(wb.getSheetIndex(list)));
            assertEquals(40, list.getLastRowNum() + 1);
            var validations = ((org.apache.poi.xssf.usermodel.XSSFSheet) wb.getSheetAt(0)).getDataValidations();
            assertTrue(validations.stream().anyMatch(v ->
                    v.getRegions().getCellRangeAddress(0).getFirstColumn() == 3
                            && v.getValidationConstraint().getFormula1().contains("$A$40")),
                    "the Category column should point at the full list");
        }
    }
}
