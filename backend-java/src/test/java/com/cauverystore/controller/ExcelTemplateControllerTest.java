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
            // Every sample row must line up with the headings, or "Yes" lands in a photo column.
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                assertEquals("Yes", sheet.getRow(r).getCell(28).getStringCellValue(), "row " + (r + 1));
            }
            assertEquals("Product Code (leave empty)", header.getCell(0).getStringCellValue());
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                assertEquals("", sheet.getRow(r).getCell(0).getStringCellValue(), "sample row " + (r + 1) + " must not carry a code");
            }
            assertEquals("https://picsum.photos/seed/s24ultra3/400/400",
                    sheet.getRow(1).getCell(27).getStringCellValue());
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
