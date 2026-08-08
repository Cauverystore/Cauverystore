package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A spreadsheet is a transcription of a notification, and transcriptions carry typos. These
 * cover the ones that would cost money: a percentage-formatted cell read as a hundredth of the
 * rate, a heading that lost its leading zero, and the general rule that nothing uploaded is
 * trusted enough to charge anyone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstRateImportServiceTest {

    @Mock private GstRateMasterRepository rateRepo;
    @Mock private HsnMasterRepository hsnRepo;
    @Mock private MasterUpdateLogRepository logRepo;

    private GstRateImportService service;

    @BeforeEach
    void setUp() {
        service = new GstRateImportService(rateRepo, hsnRepo, logRepo);
        when(hsnRepo.existsById(any())).thenReturn(true);
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc(any())).thenReturn(List.of());
    }

    /** Builds a real .xlsx, because the parsing is most of what could go wrong. */
    private MultipartFile sheet(String[] headers, Object[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Rates");
            Row header = s.createRow(0);
            for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
                    else row.createCell(c).setCellValue(String.valueOf(v));
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "rates.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @SuppressWarnings("unchecked")
    private List<GstRateMaster> staged() {
        ArgumentCaptor<List<GstRateMaster>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateRepo).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rejected(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rejected");
    }

    @Test
    void shouldStageEveryImportedRateAsUnverified() throws Exception {
        // The whole safeguard. An uploaded rate that loaded VERIFIED would be charged to
        // customers on the strength of somebody's typing.
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}, {"6109", 18, "22-09-2025"}});

        service.importRates(file, "09/2025-Central Tax (Rate)", "priya@cauverystore.in");

        assertEquals(2, staged().size());
        assertTrue(staged().stream().noneMatch(GstRateMaster::isVerified));
        assertTrue(staged().stream().allMatch(r -> r.getSource().contains("09/2025")),
                "the notification has to be recorded on every row it produced");
    }

    @Test
    void shouldReadAPercentFormattedCellAsAWholeRate() throws Exception {
        // A cell formatted as a percentage arrives as 0.05. Taken literally that is a rate of
        // 0.05%, and every invoice under it would undercharge a hundredfold.
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 0.05, "22-09-2025"}});

        service.importRates(file, "09/2025-CT(Rate)", "priya@cauverystore.in");

        assertEquals(5.0, staged().get(0).getGstRate());
    }

    @Test
    void shouldKeepALeadingZeroOnAHeadingReadAsANumber() throws Exception {
        // Excel stores 0207 as the number 207. Chapter 2 is meat; chapter 20 is preserved
        // vegetables - the rate would attach to entirely different goods.
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"0207", 5, "22-09-2025"}});

        service.importRates(file, "09/2025-CT(Rate)", "priya@cauverystore.in");

        assertEquals("0207", staged().get(0).getHsnCode(),
                "a numeric cell must not be allowed to shorten the code");
    }

    @Test
    void shouldRejectARowWithNoEffectiveDate() throws Exception {
        // Without one there is no way to apply it to future invoices without rewriting past
        // ones, which is the opposite of what effective dating is for.
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, null}});

        Map<String, Object> result = service.importRates(file, "09/2025-CT(Rate)", "priya@cauverystore.in");

        assertEquals(1, rejected(result).size());
        assertTrue(String.valueOf(rejected(result).get(0).get("reason")).contains("effective date"));
        verify(rateRepo, never()).saveAll(any());
    }

    @Test
    void shouldRejectARateThatIsNotAPercentage() throws Exception {
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 150, "22-09-2025"}});

        assertEquals(1, rejected(service.importRates(file, "09/2025-CT(Rate)", "p@c.in")).size());
    }

    @Test
    void shouldRejectACodeThatIsNotInTheOfficialMaster() throws Exception {
        when(hsnRepo.existsById("99999999")).thenReturn(false);
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"99999999", 5, "22-09-2025"}});

        Map<String, Object> result = service.importRates(file, "09/2025-CT(Rate)", "p@c.in");

        assertEquals(1, rejected(result).size());
    }

    @Test
    void shouldAcceptAChapterCode_whichIsNotInTheHsnMasterByDesign() throws Exception {
        // The master holds only 4, 6 and 8 digits because an invoice never carries a 2-digit
        // HSN - but CBIC does publish some rates against a whole chapter.
        when(hsnRepo.existsById("61")).thenReturn(false);
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"61", 5, "22-09-2025"}});

        Map<String, Object> result = service.importRates(file, "09/2025-CT(Rate)", "p@c.in");

        assertTrue(rejected(result).isEmpty());
        assertEquals("61", staged().get(0).getHsnCode());
    }

    @Test
    void shouldCloseOffTheRateItReplaces_ratherThanOverwriteIt() throws Exception {
        // An invoice raised last month has to keep resolving to last month's rate, so the old
        // row is ended the day before the new one starts and both are kept.
        GstRateMaster current = new GstRateMaster();
        current.setHsnCode("5209");
        current.setGstRate(12.0);
        current.setEffectiveFrom(LocalDate.of(2017, 7, 1));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("5209")).thenReturn(List.of(current));

        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}});
        service.importRates(file, "09/2025-CT(Rate)", "p@c.in");

        assertEquals(LocalDate.of(2025, 9, 21), current.getEffectiveTo());
        verify(rateRepo).save(current);
    }

    @Test
    void shouldNotCloseOffARateThatAlreadyEnded() throws Exception {
        GstRateMaster ended = new GstRateMaster();
        ended.setHsnCode("5209");
        ended.setGstRate(12.0);
        ended.setEffectiveFrom(LocalDate.of(2017, 7, 1));
        ended.setEffectiveTo(LocalDate.of(2020, 1, 1));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("5209")).thenReturn(List.of(ended));

        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}});
        service.importRates(file, "09/2025-CT(Rate)", "p@c.in");

        assertEquals(LocalDate.of(2020, 1, 1), ended.getEffectiveTo());
    }

    @Test
    void shouldRefuseAnImportWithNoNotificationNamed() throws Exception {
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}});

        assertThrows(GstRateImportService.ImportException.class,
                () -> service.importRates(file, "  ", "p@c.in"));
        assertThrows(GstRateImportService.ImportException.class,
                () -> service.importRates(file, null, "p@c.in"));
    }

    @Test
    void shouldRefuseAnUnattributedImport() throws Exception {
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}});

        assertThrows(GstRateImportService.ImportException.class,
                () -> service.importRates(file, "09/2025-CT(Rate)", null));
    }

    @Test
    void shouldExplainWhatTheSheetNeeds_whenTheColumnsAreUnrecognisable() throws Exception {
        MultipartFile file = sheet(new String[]{"Item", "Cost"}, new Object[][]{{"a", 1}});

        GstRateImportService.ImportException ex = assertThrows(
                GstRateImportService.ImportException.class,
                () -> service.importRates(file, "09/2025-CT(Rate)", "p@c.in"));
        assertTrue(ex.getMessage().contains("HSN column"));
    }

    @Test
    void shouldRecordTheImportInTheAuditLog() throws Exception {
        MultipartFile file = sheet(new String[]{"HSN", "Rate", "Effective From"},
                new Object[][]{{"5209", 5, "22-09-2025"}});

        service.importRates(file, "09/2025-CT(Rate)", "priya@cauverystore.in");

        verify(logRepo).save(argThat(entry ->
                "rates.xlsx".equals(entry.getFileName())
                        && "09/2025-CT(Rate)".equals(entry.getVersion())
                        && entry.getChangesDetected().contains("priya@cauverystore.in")));
    }
}
