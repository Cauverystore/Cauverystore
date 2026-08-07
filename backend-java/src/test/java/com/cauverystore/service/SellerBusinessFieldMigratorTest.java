package com.cauverystore.service;

import com.cauverystore.entities.BusinessCategory;
import com.cauverystore.entities.BusinessType;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the field whose meaning changed under existing data.
 *
 * business_type used to hold the legal constitution - Sole Proprietorship, LLP, Private
 * Limited. It now means how the seller trades. Every seller registered before the change has
 * the old kind of value in the column, and the constitution is on their GST registration and
 * cannot be reconstructed once overwritten.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerBusinessFieldMigratorTest {

    @Mock private SellerRegistrationRepository regRepo;

    private SellerBusinessFieldMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new SellerBusinessFieldMigrator(regRepo);
        when(regRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SellerRegistration seller(String businessType, String constitution) {
        SellerRegistration reg = new SellerRegistration();
        reg.setBusinessType(businessType);
        reg.setConstitutionOfBusiness(constitution);
        return reg;
    }

    @Test
    void shouldMoveTheConstitutionOutOfBusinessType() {
        SellerRegistration legacy = seller("Private Limited Company", null);
        when(regRepo.findAll()).thenReturn(List.of(legacy));

        migrator.migrate();

        assertEquals("Private Limited Company", legacy.getConstitutionOfBusiness());
        assertNull(legacy.getBusinessType(),
                "left blank for the seller to answer rather than guessed at");
    }

    @Test
    void shouldNotGuessATradeTypeFromTheConstitution() {
        // A Private Limited company might be retail, wholesale or manufacturing. Inventing one
        // would put a fabricated value on their invoices and in the reports segmented by it.
        SellerRegistration legacy = seller("Partnership", null);
        when(regRepo.findAll()).thenReturn(List.of(legacy));

        migrator.migrate();

        assertNull(legacy.getBusinessType());
    }

    @Test
    void shouldLeaveARealTradeTypeAlone() {
        SellerRegistration current = seller("Wholesale", null);
        when(regRepo.findAll()).thenReturn(List.of(current));

        migrator.migrate();

        assertEquals("Wholesale", current.getBusinessType());
        assertNull(current.getConstitutionOfBusiness());
        verify(regRepo, never()).saveAll(any());
    }

    @Test
    void shouldBeIdempotent_leavingAnAlreadyMovedRecordUntouched() {
        // A restart must not wipe a trade type the seller has since supplied.
        SellerRegistration moved = seller("Retail", "Sole Proprietorship");
        when(regRepo.findAll()).thenReturn(List.of(moved));

        migrator.migrate();

        assertEquals("Retail", moved.getBusinessType());
        assertEquals("Sole Proprietorship", moved.getConstitutionOfBusiness());
    }

    @Test
    void shouldNotOverwriteAConstitutionAlreadyRecorded() {
        SellerRegistration odd = seller("Something unrecognised", "Partnership");
        when(regRepo.findAll()).thenReturn(List.of(odd));

        migrator.migrate();

        assertEquals("Partnership", odd.getConstitutionOfBusiness());
    }

    @Test
    void shouldNotStopTheApplicationStartingIfItFails() {
        // Reference data is never worth refusing to boot over.
        when(regRepo.findAll()).thenThrow(new RuntimeException("database down"));

        assertDoesNotThrow(() -> migrator.migrate());
    }

    @Test
    void businessType_shouldAcceptItsOwnLabelsAndRejectTheOldConstitutions() {
        assertTrue(BusinessType.isValid("Retail"));
        assertTrue(BusinessType.isValid("  manufacturing "), "case and spacing must not matter");
        assertFalse(BusinessType.isValid("Sole Proprietorship"));
        assertEquals(6, BusinessType.labels().length);
    }

    @Test
    void businessCategory_shouldMatchRegardlessOfSpacingAroundSeparators() {
        // The spec writes "Automobiles/Auto Parts" and the screen shows
        // "Automobiles / Auto Parts". Rejecting one of them would be arbitrary.
        assertTrue(BusinessCategory.isValid("Automobiles / Auto Parts"));
        assertTrue(BusinessCategory.isValid("Automobiles/Auto Parts"));
        assertTrue(BusinessCategory.isValid("kirana / general merchant"));
        assertFalse(BusinessCategory.isValid("Spaceship Repair"));
        assertEquals(40, BusinessCategory.labels().length, "39 trades plus Others");
    }
}
