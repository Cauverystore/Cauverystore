package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.User;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Admin and super admin rights over a seller.
 *
 * The distinction is the whole reason both roles exist: an admin does day-to-day approvals and
 * their decision stands, while a super admin is the only one who can undo it. Before this, both
 * roles hit the same endpoints with the same rights and neither could undo anything at all - a
 * rejection was permanent for everybody, and there was no suspension.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerApprovalOverrideTest {

    @Mock private SellerRegistrationRepository regRepo;
    @Mock private SellerDocumentRepository docRepo;
    @Mock private SellerComplianceRepository complianceRepo;
    @Mock private SellerStoreRepository storeRepo;
    @Mock private UserRepository userRepo;
    @Mock private ProductRepository productRepo;
    @Mock private AuditService auditService;
    @Mock private EmailService emailService;
    @Mock private AccountRestrictionService accountRestrictionService;

    private SellerApprovalService service;
    private SellerRegistration reg;
    private User seller;

    private static final long ADMIN = 10L;
    private static final long SUPER = 99L;

    @BeforeEach
    void setUp() {
        service = new SellerApprovalService(regRepo, docRepo, complianceRepo, storeRepo,
                userRepo, productRepo, auditService, emailService, accountRestrictionService);

        seller = new User();
        seller.setId(5L);
        seller.setEmail("seller@example.com");

        reg = new SellerRegistration();
        reg.setId(1L);
        reg.setUser(seller);
        reg.setBusinessName("Kaveri Textiles");
        reg.setStatus("SUBMITTED");
        reg.setGstinStatus("VERIFIED");
        reg.setBankStatus("VERIFIED");

        when(regRepo.findById(1L)).thenReturn(Optional.of(reg));
        // Approval re-reads the registration by user id to check GSTIN, bank and compliance.
        when(regRepo.findByUserId(5L)).thenReturn(Optional.of(reg));
        when(regRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(complianceRepo.findByUserIdAndIsCompletedFalse(anyLong())).thenReturn(List.of());
        when(productRepo.findBySellerId(anyLong())).thenReturn(new ArrayList<>());
    }

    private Product product(long id, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setSellerId(seller.getId());
        p.setActive(active);
        return p;
    }

    @Test
    void anAdminCannotOverturnARejection() {
        reg.setStatus("REJECTED");

        assertThrows(SellerApprovalService.OverrideNotPermittedException.class,
                () -> service.approve(ADMIN, 1L, false));
        assertEquals("REJECTED", reg.getStatus());
    }

    @Test
    void aSuperAdminCanOverturnARejection() {
        reg.setStatus("REJECTED");
        reg.setRejectionReason("Documents unreadable");

        service.approve(SUPER, 1L, true);

        assertEquals("APPROVED", reg.getStatus());
        // The old refusal must not linger, or the seller's own status screen keeps telling them
        // they were turned down while they are trading.
        assertNull(reg.getRejectionReason());
        assertNull(reg.getRejectedAt());
        verify(auditService).log(eq(SUPER), contains("super-admin"), eq("SELLER_APPROVAL_OVERRIDDEN"),
                any(), eq(1L), any(), any());
    }

    @Test
    void anAdminCannotWithdrawAnApproval() {
        reg.setStatus("APPROVED");

        assertThrows(SellerApprovalService.OverrideNotPermittedException.class,
                () -> service.reject(ADMIN, 1L, "changed my mind", false));
        assertEquals("APPROVED", reg.getStatus());
    }

    @Test
    void aSuperAdminWithdrawingAnApprovalAlsoTakesTheSellerOffTheSite() {
        reg.setStatus("APPROVED");
        when(productRepo.findBySellerId(5L)).thenReturn(new ArrayList<>(List.of(product(100L, true))));

        service.reject(SUPER, 1L, "Fraudulent documents", true);

        assertEquals("REJECTED", reg.getStatus());
        assertEquals("SUSPENDED", seller.getStatus());
        assertFalse(seller.isActive());
        verify(auditService).log(eq(SUPER), contains("super-admin"), eq("SELLER_APPROVAL_WITHDRAWN"),
                any(), eq(1L), any(), any());
    }

    @Test
    void suspensionActuallyStopsTheSellerSelling() {
        // Nothing in the catalogue reads seller status - products list on their own active flag -
        // so a suspension that only relabelled the registration would leave every listing up and
        // the seller still taking orders.
        reg.setStatus("APPROVED");
        Product live = product(100L, true);
        when(productRepo.findBySellerId(5L)).thenReturn(new ArrayList<>(List.of(live)));

        service.suspend(SUPER, 1L, "Counterfeit goods reported");

        assertEquals("SUSPENDED", reg.getStatus());
        assertFalse(live.isActive(), "listing was left on sale");
        assertFalse(seller.isActive());
        verify(emailService).sendSellerSuspended(eq("seller@example.com"), any(), any());
    }

    @Test
    void reinstatingRestoresOnlyWhatTheSuspensionTookDown() {
        // A seller can have delisted stock themselves. Blanket-reactivating everything would put
        // those back on sale without anybody asking for it.
        reg.setStatus("APPROVED");
        Product wasLive = product(100L, true);
        Product sellerHadDelisted = product(200L, false);
        when(productRepo.findBySellerId(5L))
                .thenReturn(new ArrayList<>(List.of(wasLive, sellerHadDelisted)));

        service.suspend(SUPER, 1L, "under review");
        assertFalse(wasLive.isActive());

        when(productRepo.findById(100L)).thenReturn(Optional.of(wasLive));
        when(productRepo.findById(200L)).thenReturn(Optional.of(sellerHadDelisted));

        service.reinstate(SUPER, 1L);

        assertEquals("APPROVED", reg.getStatus());
        assertTrue(wasLive.isActive(), "suspension took this down and should have put it back");
        assertFalse(sellerHadDelisted.isActive(), "this was the seller's own choice");
        assertEquals("ACTIVE", seller.getStatus());
        assertNull(reg.getSuspendedAt());
    }

    @Test
    void aSellerWhoIsNotApprovedCannotBeSuspended() {
        reg.setStatus("SUBMITTED");

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.suspend(SUPER, 1L, "no"));
        assertTrue(e.getMessage().contains("SUBMITTED"));
    }

    @Test
    void reinstatingSurvivesAProductDeletedDuringTheSuspension() {
        reg.setStatus("APPROVED");
        when(productRepo.findBySellerId(5L))
                .thenReturn(new ArrayList<>(List.of(product(100L, true), product(200L, true))));
        service.suspend(SUPER, 1L, "under review");

        when(productRepo.findById(100L)).thenReturn(Optional.empty());          // deleted meanwhile
        when(productRepo.findById(200L)).thenReturn(Optional.of(product(200L, false)));

        // One missing product must not strand the rest of the catalogue offline.
        assertDoesNotThrow(() -> service.reinstate(SUPER, 1L));
        assertEquals("APPROVED", reg.getStatus());
    }

    @Test
    void theOrdinaryAdminPathIsUnchanged() {
        service.approve(ADMIN, 1L, false);

        assertEquals("APPROVED", reg.getStatus());
        verify(auditService).log(eq(ADMIN), contains("admin:"), eq("SELLER_APPROVED"),
                any(), eq(1L), any(), any());
        verify(emailService).sendSellerApproved(eq("seller@example.com"), any());
    }

    @Test
    void aSuspendedSellerRowCarriesWhatIsStillRunning() {
        // The admin has to be able to tell a suspension that is genuinely finished from one with
        // a delivery still out, without opening each seller in turn.
        reg.setStatus("SUSPENDED");
        when(regRepo.findAll()).thenReturn(List.of(reg));
        when(docRepo.findByUserId(anyLong())).thenReturn(List.of());
        when(complianceRepo.findByUserId(anyLong())).thenReturn(List.of());
        when(accountRestrictionService.windDown(seller))
                .thenReturn(java.util.Map.of("complete", false, "ordersAwaitingFulfilment", 3));

        var row = service.listByStatus("SUSPENDED").get(0);

        assertNotNull(row.get("windDown"));
    }

    @Test
    void aSellerWhoIsStillTradingIsNotAskedAboutAtAll() {
        // Walking every seller's orders to answer a question nobody has about the active ones
        // would make the list crawl as the marketplace grows.
        reg.setStatus("APPROVED");
        when(regRepo.findAll()).thenReturn(List.of(reg));
        when(docRepo.findByUserId(anyLong())).thenReturn(List.of());
        when(complianceRepo.findByUserId(anyLong())).thenReturn(List.of());

        var row = service.listByStatus("APPROVED").get(0);

        assertNull(row.get("windDown"));
        verify(accountRestrictionService, never()).windDown(any());
    }

    @Test
    void listingCanSeeEveryStateNotJustPending() {
        SellerRegistration approved = new SellerRegistration();
        approved.setId(2L);
        approved.setUser(seller);
        approved.setStatus("APPROVED");
        when(regRepo.findAll()).thenReturn(List.of(reg, approved));
        when(docRepo.findByUserId(anyLong())).thenReturn(List.of());
        when(complianceRepo.findByUserId(anyLong())).thenReturn(List.of());
        when(regRepo.findByUserId(anyLong())).thenReturn(Optional.of(reg));

        assertEquals(1, service.listByStatus("SUBMITTED").size());
        assertEquals(1, service.listByStatus("APPROVED").size());
        assertEquals(2, service.listByStatus("ALL").size());
        assertEquals("SUBMITTED", service.listByStatus("SUBMITTED").get(0).get("status"));
    }
}
