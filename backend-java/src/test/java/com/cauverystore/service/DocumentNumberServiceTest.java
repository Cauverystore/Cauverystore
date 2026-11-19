package com.cauverystore.service;

import com.cauverystore.entities.DocumentSeries;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.repository.DocumentSeriesRepository;
import com.cauverystore.repository.GstConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentNumberServiceTest {
    private static final String A = "33AAAAA0000A1Z5";
    private static final String B = "29BBBBB1111B1Z3";
    private final String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));

    private DocumentSeriesRepository seriesRepo;
    private GstConfigurationRepository configRepo;
    private DocumentNumberService service;
    /** Stands in for the table, keyed by docType|prefix|period. */
    private final Map<String, DocumentSeries> table = new HashMap<>();

    @BeforeEach
    void setUp() {
        seriesRepo = mock(DocumentSeriesRepository.class);
        configRepo = mock(GstConfigurationRepository.class);
        service = new DocumentNumberService(seriesRepo, configRepo, mock(PlatformTransactionManager.class));
        when(seriesRepo.lockSeries(anyString(), anyString(), anyString())).thenAnswer(inv ->
                Optional.ofNullable(table.get(inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2))));
        when(seriesRepo.findFirstByDocTypeAndPrefix(anyString(), anyString())).thenAnswer(inv ->
                table.values().stream().filter(s -> s.getDocType().equals(inv.getArgument(0))
                        && s.getPrefix().equals(inv.getArgument(1))).findFirst());
        when(seriesRepo.saveAndFlush(any())).thenAnswer(inv -> {
            DocumentSeries s = inv.getArgument(0);
            table.put(s.getDocType() + "|" + s.getPrefix() + "|" + s.getPeriod(), s);
            return s;
        });
        config(A, 1L, "CS");
        config(B, 2L, null);
    }

    private void config(String gstin, long id, String prefix) {
        GstConfiguration c = new GstConfiguration();
        c.setId(id);
        c.setGstin(gstin);
        c.setInvoicePrefix(prefix);
        when(configRepo.findByGstin(gstin)).thenReturn(Optional.of(c));
    }

    @Test
    void eachSellerGetsItsOwnConsecutiveSeries() {
        assertEquals("CS1" + period + "/00001", service.next(A, DocumentNumberService.INVOICE, null));
        assertEquals("CS2" + period + "/00001", service.next(B, DocumentNumberService.INVOICE, null));
        assertEquals("CS1" + period + "/00002", service.next(A, DocumentNumberService.INVOICE, null));
        assertEquals("CN2" + period + "/00001", service.next(B, DocumentNumberService.CREDIT_NOTE, null));
    }

    @Test
    void aNewSeriesCarriesOnFromNumbersAlreadyIssued() {
        config(A, 1L, "NOYYAL");
        String n = service.next(A, DocumentNumberService.INVOICE, pattern -> pattern + "00041");
        assertEquals("NOYYAL" + period + "/00042", n);
    }

    @Test
    void aPrefixUsedByAnotherSellerIsRefused() {
        config(A, 1L, "SHOP");
        config(B, 2L, "SHOP");
        service.next(A, DocumentNumberService.INVOICE, null);
        assertThrows(IllegalStateException.class, () -> service.next(B, DocumentNumberService.INVOICE, null));
    }

    @Test
    void numbersFitTheSixteenCharacterLimit() {
        config(A, 1L, "ABCDEFGHI");
        String n = service.next(A, DocumentNumberService.INVOICE, null);
        assertEquals(16, n.length());
        assertTrue(n.startsWith("ABCDEF"));
    }
}
