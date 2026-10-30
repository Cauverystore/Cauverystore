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
            assertEquals("https://picsum.photos/seed/s24ultra3/400/400",
                    sheet.getRow(1).getCell(27).getStringCellValue());
        }
    }
}
