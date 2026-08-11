package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The preview's only job is to agree with what the invoice will later say. These cover the ways
 * it could disagree - wrong tax head, a line quietly dropped, or a total presented as payable
 * when part of the basket cannot be taxed at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxPreviewServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private GstRateResolver rateResolver;

    private TaxPreviewService service;

    @BeforeEach
    void setUp() {
        service = new TaxPreviewService(productRepo, rateResolver);
    }

    private Product product(Long id, String name, double price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setHsnCode("52095110");
        when(productRepo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private GstRateResolver.Resolved resolved(double total, boolean interState) {
        return new GstRateResolver.Resolved(total, 0.0, interState, "52095110", true);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lines(Map<String, Object> out) {
        return (List<Map<String, Object>>) out.get("lines");
    }

    @Test
    void shouldSplitCgstAndSgst_whenSellerAndDeliveryStateMatch() {
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), eq(false), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));

        Map<String, Object> out = service.preview(
                List.of(new TaxPreviewService.Line(1L, 2, 450.0)), "33", "33", null);

        assertEquals("CGST+SGST", out.get("taxType"));
        assertEquals(false, out.get("interState"));
        assertEquals(900.0, out.get("taxableValue"));
        assertEquals(22.5, out.get("cgstAmount"));
        assertEquals(22.5, out.get("sgstAmount"));
        assertEquals(0.0, out.get("igstAmount"));
        assertEquals(945.0, out.get("grandTotal"));
    }

    @Test
    void shouldChargeIgst_whenTheGoodsCrossAStateBorder() {
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), eq(true), any(), anyDouble()))
                .thenReturn(resolved(5.0, true));

        Map<String, Object> out = service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", "29", null);

        assertEquals("IGST", out.get("taxType"));
        assertEquals(22.5, out.get("igstAmount"));
        assertEquals(0.0, out.get("cgstAmount"));
    }

    @Test
    void shouldFollowTheDeliveryState_notTheSellersConvenience() {
        // Place of supply is where the goods go. Reading it from anywhere else swaps the tax
        // heads, and a wrongly-headed tax is not cured by the total being right.
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenAnswer(i -> resolved(5.0, i.getArgument(1)));

        assertEquals("IGST", service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", "27", null).get("taxType"));
        assertEquals("CGST+SGST", service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "27", "27", null).get("taxType"));
    }

    @Test
    void shouldRefuseToGuessTheTaxHead_whenAStateIsMissing() {
        // Defaulting to intra-state would split two ways a supply that may owe IGST.
        assertThrows(IllegalArgumentException.class, () -> service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), null, "33", null));
    }

    @Test
    void shouldReportAnUntaxableLine_ratherThanDropItFromTheTotal() {
        // Silently omitting it understates the basket, and the customer would be shown a total
        // the invoice will not match.
        product(1L, "Cotton Lungi", 450.0);
        product(2L, "Unclassified thing", 100.0);
        when(rateResolver.resolve(argThat(p -> p != null && p.getId() == 1L), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));
        when(rateResolver.resolve(argThat(p -> p != null && p.getId() == 2L), anyBoolean(), any(), anyDouble()))
                .thenThrow(new GstRateResolver.GstRateUnresolvedException("no published rate"));

        Map<String, Object> out = service.preview(List.of(
                new TaxPreviewService.Line(1L, 1, 450.0),
                new TaxPreviewService.Line(2L, 1, 100.0)), "33", "33", null);

        assertEquals(1, lines(out).size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unresolved = (List<Map<String, Object>>) out.get("unresolved");
        assertEquals(1, unresolved.size());
        assertEquals(2L, unresolved.get(0).get("productId"));
        assertEquals(false, out.get("canProceed"), "the order must not be placeable");
        assertTrue(String.valueOf(out.get("message")).contains("must not be placed"));
    }

    @Test
    void shouldSayItCanProceed_onlyWhenEveryLineResolved() {
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));

        assertEquals(true, service.preview(
                List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", "33", null).get("canProceed"));
    }

    @Test
    void shouldPriceAtTheSuppliedDate_soAPastOrderRepricesCorrectly() {
        // Rates are effective-dated. Quoting today's rate for a past supply would contradict
        // the invoice, which resolves at the supply date.
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));
        LocalDate then = LocalDate.of(2025, 10, 1);

        service.preview(List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", "33", then);

        verify(rateResolver).resolve(any(), eq(false), eq(then), eq(450.0));
    }

    @Test
    void shouldPassTheQuotedPrice_notTheCataloguePrice() {
        // Apparel is banded on sale value, so a discounted basket resolves to a different rate
        // than the shelf price would.
        product(1L, "Silk Saree", 4999.0);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));

        service.preview(List.of(new TaxPreviewService.Line(1L, 1, 2400.0)), "33", "33", null);

        verify(rateResolver).resolve(any(), anyBoolean(), any(), eq(2400.0));
    }

    @Test
    void shouldReportAMissingProduct_ratherThanIgnoreTheLine() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> out = service.preview(
                List.of(new TaxPreviewService.Line(99L, 1, 100.0)), "33", "33", null);

        assertEquals(false, out.get("canProceed"));
        assertTrue(lines(out).isEmpty());
    }

    @Test
    void shouldNotWriteAnything() {
        // If this could persist, a client would be deciding the tax on the invoice.
        product(1L, "Cotton Lungi", 450.0);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false));

        service.preview(List.of(new TaxPreviewService.Line(1L, 1, 450.0)), "33", "33", null);

        verify(productRepo, never()).save(any());
        verify(productRepo, never()).saveAll(any());
    }
}
