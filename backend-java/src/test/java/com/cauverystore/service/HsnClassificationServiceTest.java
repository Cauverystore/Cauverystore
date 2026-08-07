package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.HsnAssignment;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.ChapterMaster;
import com.cauverystore.repository.ChapterMasterRepository;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnAssignmentRepository;
import com.cauverystore.repository.HsnMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HsnClassificationServiceTest {

    @Mock private HsnMasterRepository hsnRepo;
    @Mock private HsnAssignmentRepository assignmentRepo;
    @Mock private GstRateMasterRepository rateRepo;
    @Mock private GstRateResolver rateResolver;
    @Mock private ChapterMasterRepository chapterRepo;

    private HsnClassificationService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new HsnClassificationService(hsnRepo, assignmentRepo, rateRepo, rateResolver,
                chapterRepo);
        product = new Product();
        product.setName("Basmati Rice 5kg");
        when(assignmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void validate_shouldRejectACodeThatIsNotInTheOfficialMaster() {
        // The catalogue already contains "123", which is not an HSN code at all. Accepting it
        // silently is how a product ends up taxed at the fallback rate forever.
        product.setHsnCode("123");
        when(hsnRepo.existsById("123")).thenReturn(false);

        HsnClassificationService.UnknownHsnException ex = assertThrows(
                HsnClassificationService.UnknownHsnException.class, () -> service.validate(product));
        assertTrue(ex.getMessage().contains("123"), "should name the offending code");
        assertTrue(ex.getMessage().toLowerCase().contains("fallback"),
                "should say what goes wrong, not just that it is invalid");
    }

    @Test
    void validate_shouldAcceptAndNormaliseARealCode() {
        product.setHsnCode(" 1006 ");
        when(hsnRepo.existsById("1006")).thenReturn(true);

        service.validate(product);

        assertEquals("1006", product.getHsnCode(), "whitespace must not defeat the lookup later");
    }

    @Test
    void validate_shouldAllowABlankCode() {
        // Much of the catalogue predates any of this. Refusing to save an existing product
        // because it lacks a code would be a worse failure than the missing code itself.
        product.setHsnCode(null);
        assertDoesNotThrow(() -> service.validate(product));

        product.setHsnCode("   ");
        assertDoesNotThrow(() -> service.validate(product));
    }

    @Test
    void rememberAssignment_shouldRecordTheCodeAgainstTheCategory() {
        Category groceries = new Category();
        groceries.setId(3L);
        product.setCategory(groceries);
        product.setHsnCode("1006");
        when(assignmentRepo.findByCategoryIdAndHsnCode(3L, "1006")).thenReturn(Optional.empty());

        service.rememberAssignment(product, "seller@cauverystore.in");

        ArgumentCaptor<HsnAssignment> captor = ArgumentCaptor.forClass(HsnAssignment.class);
        verify(assignmentRepo).save(captor.capture());
        HsnAssignment saved = captor.getValue();
        assertEquals(3L, saved.getCategoryId());
        assertEquals("1006", saved.getHsnCode());
        assertEquals(1, saved.getTimesUsed());
        assertEquals("seller@cauverystore.in", saved.getLastUsedBy());
    }

    @Test
    void rememberAssignment_shouldCountRepeatUseSoTheCommonCodeRisesToTheTop() {
        Category groceries = new Category();
        groceries.setId(3L);
        product.setCategory(groceries);
        product.setHsnCode("1006");

        HsnAssignment existing = new HsnAssignment();
        existing.setCategoryId(3L);
        existing.setHsnCode("1006");
        existing.setTimesUsed(4);
        when(assignmentRepo.findByCategoryIdAndHsnCode(3L, "1006")).thenReturn(Optional.of(existing));

        service.rememberAssignment(product, "seller@cauverystore.in");

        assertEquals(5, existing.getTimesUsed());
    }

    @Test
    void rememberAssignment_shouldNeverFailTheProductSave() {
        // A lost suggestion costs a seller one search; a failed save costs them their work.
        product.setHsnCode("1006");
        when(assignmentRepo.findByCategoryIdAndHsnCode(any(), anyString()))
                .thenThrow(new RuntimeException("database down"));

        assertDoesNotThrow(() -> service.rememberAssignment(product, "seller@cauverystore.in"));
    }

    @Test
    void suggestionsFor_shouldReturnCategoryCodesWithTheirOfficialDescription() {
        HsnAssignment used = new HsnAssignment();
        used.setCategoryId(3L);
        used.setHsnCode("1006");
        used.setTimesUsed(9);
        when(assignmentRepo.findByCategoryIdOrderByTimesUsedDescLastUsedAtDesc(3L))
                .thenReturn(List.of(used));
        when(hsnRepo.findById("1006")).thenReturn(Optional.of(new HsnMaster("1006", "Rice")));

        var out = service.suggestionsFor(3L);

        assertEquals(1, out.size());
        assertEquals("1006", out.get(0).get("hsnCode"));
        assertEquals("Rice", out.get(0).get("description"),
                "a bare code tells a seller nothing; the description is the point");
        assertEquals(9, out.get(0).get("timesUsed"));
    }

    @Test
    void suggestionsFor_shouldSkipARememberedCodeThatIsNoLongerInTheMaster() {
        // Codes are withdrawn between master refreshes; offering a dead one would walk the
        // seller straight into the validation error.
        HsnAssignment stale = new HsnAssignment();
        stale.setCategoryId(3L);
        stale.setHsnCode("99999999");
        when(assignmentRepo.findByCategoryIdOrderByTimesUsedDescLastUsedAtDesc(3L))
                .thenReturn(List.of(stale));
        when(hsnRepo.findById("99999999")).thenReturn(Optional.empty());

        assertTrue(service.suggestionsFor(3L).isEmpty());
    }

    @Test
    void search_shouldIgnoreAQueryTooShortToMeanAnything() {
        assertTrue(service.search("r").isEmpty());
        assertTrue(service.search(null).isEmpty());
        verify(hsnRepo, never()).search(anyString(), any());
    }

    private GstRateMaster line(long id, String hsn, double pct, String description) {
        GstRateMaster r = new GstRateMaster();
        r.setId(id);
        r.setHsnCode(hsn);
        r.setGstRate(pct);
        r.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        r.setConditionText(description);
        return r;
    }

    private void coffeeIsAmbiguous() {
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.empty());
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("0901")).thenReturn(List.of(
                line(1L, "0901", 5.0, "Coffee roasted, whether or not decaffeinated"),
                line(2L, "0901", 0.0, "Coffee beans, not roasted")));
    }

    @Test
    void rateOptions_shouldOfferTheNotificationsOwnWording() {
        // "5% or nil?" is unanswerable; "roasted or not roasted?" is a question about stock.
        coffeeIsAmbiguous();

        var options = service.rateOptionsFor("0901", null, null);

        assertEquals(2, options.size());
        assertEquals("Coffee roasted, whether or not decaffeinated", options.get(0).get("description"));
        assertEquals("Coffee beans, not roasted", options.get(1).get("description"));
        assertEquals(1L, options.get(0).get("rateId"));
    }

    @Test
    void rateOptions_shouldAskNothingWhenTheRateAlreadyResolves() {
        // Rice is settled by the packaging flag, so putting the question would be noise.
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.of(5.0));

        assertTrue(service.rateOptionsFor("1006", null, true).isEmpty());
        verify(rateRepo, never()).findByHsnCodeOrderByEffectiveFromDesc(anyString());
    }

    @Test
    void rateOptions_shouldIgnoreALineThatIsNoLongerInForce() {
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.empty());
        GstRateMaster expired = line(1L, "0901", 5.0, "Old wording");
        expired.setEffectiveTo(LocalDate.of(2026, 1, 31));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("0901"))
                .thenReturn(List.of(expired, line(2L, "0901", 0.0, "Coffee beans, not roasted")));

        // One live line left, so there is no choice to put to the seller.
        assertTrue(service.rateOptionsFor("0901", null, null).isEmpty());
    }

    @Test
    void assertSellable_shouldRefuseToPublishAnUnclassifiableProduct() {
        // There is no provisional rate in GST, so the only state issuing no wrong invoice is
        // not selling yet.
        product.setHsnCode("0901");
        product.setProductStatus("published");
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.empty());

        HsnClassificationService.UnclassifiedProductException ex = assertThrows(
                HsnClassificationService.UnclassifiedProductException.class,
                () -> service.assertSellable(product));
        assertTrue(ex.getMessage().contains("0901"));
        assertTrue(ex.getMessage().toLowerCase().contains("draft"),
                "the seller must be told their work is kept, not lost");
    }

    @Test
    void assertSellable_shouldAllowADraftToBeSavedUnclassified() {
        // Blocking the save would cost the seller their work; only publishing is gated.
        product.setHsnCode("0901");
        product.setProductStatus("draft");
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.assertSellable(product));
    }

    @Test
    void assertSellable_shouldAcceptAProductWhoseSellerHasAnswered() {
        product.setHsnCode("0901");
        product.setProductStatus("published");
        product.setGstRateSelectionId(1L);
        when(rateResolver.findRate(anyString(), any(), any(), any())).thenReturn(Optional.empty());
        when(rateRepo.findById(1L)).thenReturn(Optional.of(inForceRateLine("0901", 5.0)));

        assertDoesNotThrow(() -> service.assertSellable(product));
    }

    @Test
    void assertSellable_shouldRejectARateLineThatIsNotInTheRateFile() {
        // An id that names nothing must not let the product through: the resolver would not
        // charge from it either, so treating it as "the seller answered" would publish a
        // product that cannot actually be sold.
        product.setHsnCode("0901");
        product.setProductStatus("published");
        product.setGstRateSelectionId(999L);
        when(rateRepo.findById(999L)).thenReturn(Optional.empty());

        HsnClassificationService.UnclassifiedProductException ex = assertThrows(
                HsnClassificationService.UnclassifiedProductException.class,
                () -> service.assertSellable(product));
        assertTrue(ex.getMessage().contains("not in the"), "should say the line is missing");
    }

    @Test
    void assertSellable_shouldRejectARateLinePublishedAgainstAnotherHsn() {
        product.setHsnCode("0901");
        product.setProductStatus("published");
        product.setGstRateSelectionId(1L);
        when(rateRepo.findById(1L)).thenReturn(Optional.of(inForceRateLine("0903", 5.0)));

        HsnClassificationService.UnclassifiedProductException ex = assertThrows(
                HsnClassificationService.UnclassifiedProductException.class,
                () -> service.assertSellable(product));
        assertTrue(ex.getMessage().contains("0903"), "should name the wrong code");
    }

    @Test
    void assertSellable_shouldRejectARateLineThatHasExpired() {
        product.setHsnCode("0901");
        product.setProductStatus("published");
        product.setGstRateSelectionId(1L);
        GstRateMaster expired = inForceRateLine("0901", 5.0);
        expired.setEffectiveFrom(LocalDate.now().minusDays(20));
        expired.setEffectiveTo(LocalDate.now().minusDays(1));
        when(rateRepo.findById(1L)).thenReturn(Optional.of(expired));

        HsnClassificationService.UnclassifiedProductException ex = assertThrows(
                HsnClassificationService.UnclassifiedProductException.class,
                () -> service.assertSellable(product));
        assertTrue(ex.getMessage().toLowerCase().contains("not in force"), "should say the line lapsed");
    }

    private GstRateMaster inForceRateLine(String hsn, double pct) {
        GstRateMaster r = new GstRateMaster();
        r.setId(1L);
        r.setHsnCode(hsn);
        r.setGstRate(pct);
        r.setEffectiveFrom(LocalDate.now().minusDays(30));
        return r;
    }

    @Test
    void assertSellable_shouldRefuseAPublishedProductWithNoCodeAtAll() {
        product.setHsnCode(null);
        product.setProductStatus("published");

        HsnClassificationService.UnclassifiedProductException ex = assertThrows(
                HsnClassificationService.UnclassifiedProductException.class,
                () -> service.assertSellable(product));
        assertTrue(ex.getMessage().toLowerCase().contains("no hsn code"));
    }

    @Test
    void chapters_shouldLabelAnUnnamedChapterByItsNumber() {
        // Five chapters have no published title. They still have to be enterable, or the trades
        // under them become unreachable by browsing.
        when(chapterRepo.findAllByOrderByChapterAsc()).thenReturn(List.of(
                new ChapterMaster("04", null, null),
                new ChapterMaster("61", "ARTICLES OF APPAREL AND CLOTHING ACCESSORIES, KNITTED OR CROCHETED",
                        "e-invoice HSN master, chapter title prefix")));

        List<java.util.Map<String, Object>> out = service.chapters();

        assertEquals("04", out.get(0).get("label"));
        assertEquals(Boolean.FALSE, out.get(0).get("named"));
        assertEquals("61 - ARTICLES OF APPAREL AND CLOTHING ACCESSORIES, KNITTED OR CROCHETED",
                out.get(1).get("label"));
        assertEquals(Boolean.TRUE, out.get(1).get("named"));
    }

    @Test
    void headingsInChapter_shouldPadASingleDigitChapter() {
        // Chapters are stored padded ("03"), and a picker or a URL may well send "3". Comparing
        // the two unpadded is how a lookup silently returns nothing.
        when(hsnRepo.findByChapterAndDigitsOrderByHsnCodeAsc("03", 4))
                .thenReturn(List.of(new HsnMaster("0302", "FISH, FRESH OR CHILLED")));

        assertEquals(1, service.headingsInChapter("3").size());
        assertEquals(1, service.headingsInChapter("03").size());
    }

    @Test
    void headingsInChapter_shouldOfferOnlyFourDigitHeadings() {
        // The eight-digit rows exist, but opening a chapter onto hundreds of them buries the
        // level most sellers should actually be picking.
        service.headingsInChapter("61");

        verify(hsnRepo).findByChapterAndDigitsOrderByHsnCodeAsc("61", 4);
    }

    @Test
    void headingsInChapter_shouldRejectAnythingThatIsNotAChapterNumber() {
        assertTrue(service.headingsInChapter("abc").isEmpty());
        assertTrue(service.headingsInChapter("6101").isEmpty());
        assertTrue(service.headingsInChapter(null).isEmpty());
        verify(hsnRepo, never()).findByChapterAndDigitsOrderByHsnCodeAsc(any(), any());
    }

    @Test
    void assertSellable_shouldHaveNoSwitchToDisableIt() {
        // There is deliberately no flag. One would be a switch to sell goods the system knows
        // it cannot tax correctly, which is the thing this exists to prevent.
        assertTrue(java.util.Arrays.stream(HsnClassificationService.class.getDeclaredFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("require")),
                "no configurable escape hatch may exist on this guard");
    }
}
