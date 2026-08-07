package com.cauverystore.service;

import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.TradeSynonym;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.TradeSynonymRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The instrument exists so a seller sees the tax, not just a code. These cover the two ways it
 * could do harm: proposing a code as settled when it is not, and asserting a translation the
 * store cannot back up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductTermMapperTest {

    @Mock private HsnMasterRepository hsnRepo;
    @Mock private TradeSynonymRepository synonymRepo;
    @Mock private GstRateResolver rateResolver;

    private ProductTermMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProductTermMapper(hsnRepo, synonymRepo, rateResolver);
        when(synonymRepo.findByTermIgnoreCase(any())).thenReturn(Optional.empty());
    }

    private HsnMaster hsn(String code, String description) {
        return new HsnMaster(code, description);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> candidates(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("candidates");
    }

    @Test
    void shouldMapCottonLungiToItsPublishedCodeAndRate() {
        // The case this was built for: a plain trade name in, a code and the tax out.
        when(hsnRepo.search(eq("cotton"), any())).thenReturn(List.of(
                hsn("52095110", "LUNGI OF COTTON, WOVEN, WEIGHING MORE THAN 200 G/M2"),
                hsn("52010010", "COTTON, NOT CARDED OR COMBED")));
        when(hsnRepo.search(eq("lungi"), any())).thenReturn(List.of(
                hsn("52095110", "LUNGI OF COTTON, WOVEN, WEIGHING MORE THAN 200 G/M2"),
                hsn("54078260", "LUNGI OF SYNTHETIC FILAMENT YARN")));
        when(rateResolver.findRate(eq("52095110"), any(), any(), any())).thenReturn(Optional.of(5.0));
        when(rateResolver.findRate(eq("54078260"), any(), any(), any())).thenReturn(Optional.of(5.0));
        when(rateResolver.findRate(eq("52010010"), any(), any(), any())).thenReturn(Optional.of(5.0));

        List<Map<String, Object>> out = candidates(mapper.map("Cotton Lungi", 450.0, null));

        Map<String, Object> best = out.get(0);
        assertEquals("52095110", best.get("hsnCode"), "the cotton lungi code matched both words "
                + "and must outrank the codes that matched only one");
        assertEquals(5.0, best.get("gstRate"));
        assertEquals(Boolean.TRUE, best.get("taxable"));
    }

    @Test
    void shouldNotPresentAnUnresolvableCodeAsUntaxed() {
        // "No rate" reads as "no tax". It means the opposite - the rate exists in law and this
        // system cannot yet say which. Showing a blank would invite the seller to pick it.
        when(hsnRepo.search(eq("idol"), any())).thenReturn(List.of(
                hsn("69120040", "IDOLS OF CLAY")));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.empty());

        Map<String, Object> only = candidates(mapper.map("clay idol", null, null)).get(0);

        assertNull(only.get("gstRate"));
        assertEquals(Boolean.FALSE, only.get("taxable"));
        assertTrue(String.valueOf(only.get("rateNote")).contains("hold the product back from sale"),
                "the consequence of choosing it has to be stated");
    }

    @Test
    void shouldTranslateASellersWordIntoTheTariffsWord() {
        // Veshti is not in the master; dhoti is. The translation is what makes the search work,
        // and it is reported so the seller can see why they were shown dhoti codes.
        when(synonymRepo.findByTermIgnoreCase("veshti")).thenReturn(Optional.of(
                new TradeSynonym("veshti", "dhoti", "Tamil for the same garment", "priya@cauverystore.in")));
        when(hsnRepo.search(eq("veshti"), any())).thenReturn(List.of());
        when(hsnRepo.search(eq("dhoti"), any())).thenReturn(List.of(
                hsn("52081110", "DHOTI OF COTTON, PLAIN WEAVE")));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.of(5.0));

        Map<String, Object> result = mapper.map("Veshti", 300.0, null);

        assertEquals("52081110", candidates(result).get(0).get("hsnCode"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> translations = (List<Map<String, Object>>) result.get("translations");
        assertEquals("dhoti", translations.get(0).get("officialTerm"));
        assertEquals("priya@cauverystore.in", translations.get(0).get("addedBy"));
    }

    @Test
    void shouldIgnoreWordsThatCarryNoMeaningInTheTariff() {
        // "Premium 2 Piece Set Cotton Lungi Combo" must search on cotton and lungi. Matching
        // "set" or "piece" pulls in half the master and buries the words that mean something.
        mapper.map("Premium 2 Piece Set Cotton Lungi Combo Offer", null, null);

        verify(hsnRepo).search(eq("cotton"), any());
        verify(hsnRepo).search(eq("lungi"), any());
        verify(hsnRepo, never()).search(eq("piece"), any());
        verify(hsnRepo, never()).search(eq("premium"), any());
        verify(hsnRepo, never()).search(eq("combo"), any());
    }

    @Test
    void shouldNotMatchAWordFoundOnlyInsideAnotherWord() {
        // The repository search is a LIKE, so "tea" hits "protease". Offering a chemical to
        // someone selling tea is how a wrong code gets picked by someone trusting the list.
        when(hsnRepo.search(eq("tea"), any())).thenReturn(List.of(
                hsn("35079061", "PROTEASE ENZYMES"),
                hsn("09024010", "BLACK TEA, LEAF")));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.of(5.0));

        List<Map<String, Object>> out = candidates(mapper.map("tea", null, null));

        assertEquals(1, out.size());
        assertEquals("09024010", out.get(0).get("hsnCode"));
    }

    @Test
    void shouldSayThePriceDecides_forGoodsBandedBySaleValue() {
        // Apparel is 5% to Rs 2,500 and 18% above. Showing 5% with no qualification would read
        // as the rate for that garment at any price.
        when(hsnRepo.search(eq("shirt"), any())).thenReturn(List.of(
                hsn("62052000", "MEN'S SHIRTS OF COTTON")));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.of(5.0));

        Map<String, Object> only = candidates(mapper.map("shirt", 799.0, null)).get(0);

        assertEquals(5.0, only.get("gstRate"));
        assertTrue(String.valueOf(only.get("rateNote")).contains("2,500"));
    }

    @Test
    void addSynonym_shouldRefuseAWordTheTariffDoesNotUse() {
        // Pointing at a word absent from the master leaves the seller with the same empty
        // result while the store's records claim the word is handled.
        when(hsnRepo.search(any(), any())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.addSynonym("veshti", "veshtee", null, "priya@cauverystore.in"));
        assertTrue(ex.getMessage().contains("does not appear in any official HSN description"));
        verify(synonymRepo, never()).save(any());
    }

    @Test
    void addSynonym_shouldRefuseAnUnattributedTranslation() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.addSynonym("veshti", "dhoti", null, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.addSynonym("veshti", "dhoti", null, null));
    }

    @Test
    void addSynonym_shouldRefuseAWordPointingAtItself() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.addSynonym("dhoti", "Dhoti", null, "priya@cauverystore.in"));
    }

    @Test
    void shouldAskForADescription_ratherThanReturnEverything() {
        Map<String, Object> result = mapper.map("  ", null, null);

        assertTrue(candidates(result).isEmpty());
        verify(hsnRepo, never()).search(any(), any());
    }

    @Test
    void shouldNeverAssignACodeItself() {
        // A cotton lungi is a different code handloom or not, and by fabric weight. Only the
        // seller knows which. There is deliberately no method that picks one.
        assertTrue(java.util.Arrays.stream(ProductTermMapper.class.getDeclaredMethods())
                        .noneMatch(m -> m.getName().toLowerCase().contains("assign")
                                || m.getName().toLowerCase().contains("classify")),
                "this proposes candidates; choosing among them is the seller's judgement");
    }

    @Test
    void shouldPassThePriceThroughToTheResolver() {
        // Value-banded goods resolve differently by price. Dropping it here would show 5% for
        // a Rs 4,999 garment that owes 18%.
        when(hsnRepo.search(eq("saree"), any())).thenReturn(List.of(hsn("62114210", "SAREE")));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.of(18.0));

        mapper.map("saree", 4999.0, Boolean.FALSE);

        verify(rateResolver).findRate(eq("62114210"), any(LocalDate.class), eq(4999.0), eq(Boolean.FALSE));
    }
}
