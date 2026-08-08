package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 357 rates awaiting review reads as a project, and a project does not get started. Only the
 * headings something on sale resolves through actually hold anything up, and these cover the
 * ordering that turns the queue into an hour's work.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstReviewPriorityTest {

    @Mock private ProductRepository productRepo;
    @Mock private HsnMasterRepository hsnRepo;
    @Mock private GstRateResolver rateResolver;
    @Mock private GstInvoiceRepository invoiceRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private GstRateFreshnessService freshnessService;
    @Mock private GstRateMasterRepository rateRepo;

    private GstComplianceReadinessService service;

    @BeforeEach
    void setUp() {
        service = new GstComplianceReadinessService(productRepo, hsnRepo, rateResolver,
                invoiceRepo, configRepo, freshnessService, rateRepo);
        when(hsnRepo.existsById(any())).thenReturn(true);
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc(any())).thenReturn(List.of());
    }

    private Product live(long id, String name, String hsn) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setHsnCode(hsn);
        p.setPrice(100.0);
        p.setActive(true);
        p.setProductStatus("published");
        p.setPrePackagedAndLabelled(Boolean.TRUE);
        return p;
    }

    @Test
    void shouldRankHeadingsByHowManyProductsTheyWouldRelease() {
        // The whole point: an hour of review should be spent where it buys the most.
        when(productRepo.findAll()).thenReturn(List.of(
                live(1, "Biscuits", "1905"), live(2, "Cakes", "1905"), live(3, "Rusk", "1905"),
                live(4, "Talcum powder", "3304")));

        List<Map<String, Object>> priority = service.reviewPriority();

        assertEquals(2, priority.size());
        assertEquals("1905", priority.get(0).get("hsnCode"));
        assertEquals(3, priority.get(0).get("productsBlocked"));
        assertEquals("3304", priority.get(1).get("hsnCode"));
    }

    @Test
    void shouldListOnlyHeadingsSomethingOnSaleUses() {
        // 175 headings await review; if the shop sells under two of them, the other 173 are not
        // holding anything up and putting them on the list only buries the two that are.
        when(productRepo.findAll()).thenReturn(List.of(live(1, "Biscuits", "1905")));

        List<Map<String, Object>> priority = service.reviewPriority();

        assertEquals(1, priority.size());
        assertEquals("1905", priority.get(0).get("hsnCode"));
    }

    @Test
    void shouldOfferBothWaysOut() {
        // A seller can clear their own product by picking the wording, without waiting for a
        // review at all. A heading blocking one product is a message to that seller.
        when(productRepo.findAll()).thenReturn(List.of(live(1, "Biscuits", "1905")));

        Map<String, Object> row = service.reviewPriority().get(0);

        assertTrue(String.valueOf(row.get("fixedByReview")).contains("review desk"));
        assertTrue(String.valueOf(row.get("fixedBySeller")).contains("without waiting"));
    }

    @Test
    void shouldShowTheCompetingWordingsToChooseBetween() {
        // A reviewer decides on the notification's words, so the words have to be in front of
        // them - "0% or 5%?" is not a question anyone can answer.
        GstRateMaster nil = new GstRateMaster();
        nil.setHsnCode("1905");
        nil.setGstRate(0.0);
        nil.setStatus(GstRateMaster.STATUS_UNVERIFIED);
        nil.setConditionText("Pappad, by whatever name it is known");
        GstRateMaster five = new GstRateMaster();
        five.setHsnCode("1905");
        five.setGstRate(5.0);
        five.setStatus(GstRateMaster.STATUS_UNVERIFIED);
        five.setConditionText("Pastry, cakes, biscuits and other bakers' wares");
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("1905")).thenReturn(List.of(nil, five));
        when(productRepo.findAll()).thenReturn(List.of(live(1, "Biscuits", "1905")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) service.reviewPriority().get(0).get("choices");

        assertEquals(2, choices.size());
        assertTrue(choices.stream().anyMatch(c -> String.valueOf(c.get("wording")).contains("Pappad")));
        assertTrue(choices.stream().anyMatch(c -> String.valueOf(c.get("wording")).contains("biscuits")));
    }

    @Test
    void shouldNotListAProductTheSellerHasAlreadyAnswered() {
        // Choosing the wording clears the product even on a heading nobody has reviewed, so it
        // is not waiting on anything and must not inflate the queue.
        Product answered = live(1, "Biscuits", "1905");
        answered.setGstRateSelectionId(42L);
        when(productRepo.findAll()).thenReturn(List.of(answered));

        assertTrue(service.reviewPriority().isEmpty());
    }

    @Test
    void shouldIgnoreDrafts() {
        // Nothing is being sold at the wrong rate, so it is not a compliance problem yet.
        Product draft = live(1, "Biscuits", "1905");
        draft.setProductStatus("draft");
        when(productRepo.findAll()).thenReturn(List.of(draft));

        assertTrue(service.reviewPriority().isEmpty());
    }

    @Test
    void shouldNotGroupProductsWithNoCodeUnderABlankHeading() {
        // Those need classifying, not a rate review, and a "" bucket at the top of the list
        // would point the reviewer at work that is not theirs.
        Product noCode = live(1, "Unclassified", null);
        when(productRepo.findAll()).thenReturn(List.of(noCode, live(2, "Biscuits", "1905")));

        List<Map<String, Object>> priority = service.reviewPriority();

        assertEquals(1, priority.size());
        assertEquals("1905", priority.get(0).get("hsnCode"));
    }

    @Test
    void shouldFlagATurnoverThatMakesEInvoicingCompulsory() {
        // Order 48 declared "HSN/SAC digits: 6" over the four-digit code 8517, because the
        // recorded turnover was above five crore. The same figure makes e-invoicing compulsory,
        // and rule 48(5) says a document that should have been e-invoiced but was not is not an
        // invoice at all - so with a simulated IRP the store would be issuing things that are
        // not invoices while the PDF prints something that looks like one.
        com.cauverystore.entities.GstConfiguration config = new com.cauverystore.entities.GstConfiguration();
        config.setGstin("33ABCDE1234F1Z5");
        config.setCin("U12345TN2020PTC123456");
        config.setNodalAccountNumber("1234567890");
        config.setAnnualTurnover(60_000_000.0);
        when(configRepo.findAll()).thenReturn(List.of(config));
        when(productRepo.findAll()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<String> gaps = (List<String>) service.readiness().get("marketplaceGaps");

        assertTrue(gaps.stream().anyMatch(g -> g.contains("e-invoicing compulsory")),
                "a turnover above the threshold has to be surfaced, not left implicit: " + gaps);
        assertTrue(gaps.stream().anyMatch(g -> g.contains("48(5)")));
    }

    @Test
    void shouldNotFlagEInvoicingBelowTheThreshold() {
        // A new store is nowhere near five crore, and a false alarm here would train someone to
        // ignore the screen.
        com.cauverystore.entities.GstConfiguration config = new com.cauverystore.entities.GstConfiguration();
        config.setGstin("33ABCDE1234F1Z5");
        config.setCin("U12345TN2020PTC123456");
        config.setNodalAccountNumber("1234567890");
        config.setAnnualTurnover(2_000_000.0);
        when(configRepo.findAll()).thenReturn(List.of(config));
        when(productRepo.findAll()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<String> gaps = (List<String>) service.readiness().get("marketplaceGaps");

        assertTrue(gaps.stream().noneMatch(g -> g.contains("e-invoicing compulsory")));
    }
}
