package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.HsnAssignment;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.Product;
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

    private HsnClassificationService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new HsnClassificationService(hsnRepo, assignmentRepo);
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
}
