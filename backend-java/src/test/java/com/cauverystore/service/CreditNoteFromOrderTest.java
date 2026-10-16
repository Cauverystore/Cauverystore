package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
 * Credit notes for orders that never got a GST invoice are built from the order itself. They
 * used to take the rate from the product's free-text percentage, which defaults to 12% - a rate
 * clothing is never taxed at - and ignored any coupon. They now resolve the rate the way the
 * invoice does and, on GST-inclusive orders, take the tax out of what was paid.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditNoteFromOrderTest {

    @Mock private CreditNoteRepository creditNoteRepo;
    @Mock private CreditNoteItemRepository creditNoteItemRepo;
    @Mock private GstInvoiceRepository invoiceRepo;
    @Mock private GstInvoiceItemRepository invoiceItemRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private ReturnRequestRepository returnRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditService auditService;
    @Mock private TcsRecordRepository tcsRepo;
    @Mock private GstRateResolver gstRateResolver;

    @InjectMocks private CreditNoteService service;

    private Order order;
    private Product kurta;

    @BeforeEach
    void setUp() {
        // Clothing: 5% up to 2,625 a piece (GST inside), 18% above.
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), any())).thenAnswer(i -> {
            Double price = i.getArgument(3);
            double rate = price != null && price > 2625 ? 18.0 : 5.0;
            return new GstRateResolver.Resolved(rate, 0.0, i.getArgument(1), "6109", true);
        });
        when(invoiceRepo.findByOrderId(anyLong())).thenReturn(Optional.empty());
        when(creditNoteRepo.findByOrderIdAndNoteType(anyLong(), anyString())).thenReturn(Optional.empty());
        when(creditNoteRepo.findByReferenceTypeAndReferenceId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(creditNoteRepo.save(any(CreditNote.class))).thenAnswer(i -> i.getArgument(0));

        User buyer = new User();
        buyer.setId(1L);
        buyer.setFullName("Ravi Kumar");
        Address addr = new Address();
        addr.setState("Tamil Nadu");

        kurta = new Product();
        kurta.setId(1L);
        kurta.setName("Cotton Kurta");
        kurta.setHsnCode("6109");
        // Left unset on purpose: this is what used to drag every line to 12%.
        kurta.setGstPercentage(null);

        order = new Order();
        order.setId(7L);
        order.setUser(buyer);
        order.setAddress(addr);
        order.setDeliveryCharge(0.0);
        when(orderRepo.findById(7L)).thenReturn(Optional.of(order));
    }

    private OrderItem line(double price, int qty) {
        OrderItem oi = new OrderItem();
        oi.setProduct(kurta);
        oi.setPrice(price);
        oi.setQuantity(qty);
        return oi;
    }

    private CreditNote fullNote() {
        Map<String, Object> result = service.generateFullCreditNote(7L, "Cancelled", "admin");
        return (CreditNote) result.get("creditNote");
    }

    @Test
    void inclusiveOrder_shouldTakeTheTaxOutOfWhatWasPaid_atTheRateForThePriceAfterCoupon() {
        // 2 x 1,500 with a 600 coupon: 2,400 paid, 1,200 a piece - the 5% band, not 12%.
        order.setItems(List.of(line(1500.0, 2)));
        order.setPricesIncludeTax(true);
        order.setTotalAmount(2400.0);

        CreditNote cn = fullNote();
        CreditNoteItem it = cn.getItems().get(0);

        verify(gstRateResolver).resolve(any(), eq(false), any(), eq(1200.0));
        assertEquals(2.5, it.getCgstRate(), 0.001);
        assertEquals(2.5, it.getSgstRate(), 0.001);
        assertEquals(57.14, it.getCgstAmount(), 0.001);
        assertEquals(2285.72, it.getTaxableValue(), 0.001);
        assertEquals(2400.0, it.getTotalAmount(), 0.001, "never refund more tax than was inside the price");
        assertEquals(2400.0, cn.getTotalAmount(), 0.001);
    }

    @Test
    void inclusiveOrder_withGiftWrap_shouldNotReadTheWrapAsALowerCoupon() {
        // Same order plus 49 gift wrap: 2,449 paid. The goods still cost 2,400 of it.
        order.setItems(List.of(line(1500.0, 2)));
        order.setPricesIncludeTax(true);
        order.setGiftWrapCharge(49.0);
        order.setTotalAmount(2449.0);

        CreditNote cn = fullNote();

        verify(gstRateResolver).resolve(any(), anyBoolean(), any(), eq(1200.0));
        assertEquals(2400.0, cn.getItems().get(0).getTotalAmount(), 0.001);
    }

    @Test
    void fullRefund_shouldCreditTheTaxInsideDeliveryAndGiftWrapToo() {
        // 1,050 goods + 40 delivery + 49 gift wrap = 1,139 paid, all of it refunded.
        order.setItems(List.of(line(1050.0, 1)));
        order.setPricesIncludeTax(true);
        order.setDeliveryCharge(40.0);
        order.setGiftWrapCharge(49.0);
        order.setTotalAmount(1139.0);

        CreditNote cn = fullNote();

        assertEquals(3, cn.getItems().size());
        CreditNoteItem delivery = cn.getItems().get(1);
        CreditNoteItem wrap = cn.getItems().get(2);
        assertTrue(delivery.getProductName().startsWith("Delivery"));
        assertEquals("6109", delivery.getHsnCode(), "taxed with the goods, under their HSN");
        assertEquals(2.5, delivery.getCgstRate(), 0.001);
        assertEquals(40.0, delivery.getTotalAmount(), 0.001, "the tax is inside the 40");
        assertTrue(wrap.getProductName().startsWith("Gift wrapping"));
        assertEquals(49.0, wrap.getTotalAmount(), 0.001);
        assertEquals(1139.0, cn.getTotalAmount(), 0.001, "the note must equal what was refunded");
        assertEquals(cn.getTaxableAmount() + cn.getTotalTax(), cn.getTotalAmount(), 0.001);
    }

    @Test
    void preInclusiveOrder_shouldAddTaxOnTopAtTheResolvedRate() {
        // An older order: 1,000 before tax, 50 GST added on top, 1,050 paid.
        order.setItems(List.of(line(1000.0, 1)));
        order.setTax(50.0);
        order.setTotalAmount(1050.0);

        CreditNote cn = fullNote();
        CreditNoteItem it = cn.getItems().get(0);

        assertEquals(1000.0, it.getTaxableValue(), 0.001);
        assertEquals(25.0, it.getCgstAmount(), 0.001);
        assertEquals(25.0, it.getSgstAmount(), 0.001);
        assertEquals(1050.0, cn.getTotalAmount(), 0.001);
    }

    @Test
    void returnOfOnePiece_shouldUseTheResolvedRateToo() {
        // 1 of 2 pieces at 3,540 returned: above 2,625, so 18% - 540 of the 3,540 is tax.
        OrderItem oi = line(3540.0, 2);
        order.setItems(List.of(oi));
        order.setPricesIncludeTax(true);
        order.setTotalAmount(7080.0);
        ReturnRequest rr = new ReturnRequest();
        rr.setOrder(order);
        rr.setOrderItem(oi);
        rr.setQuantity(1);
        when(returnRepo.findById(9L)).thenReturn(Optional.of(rr));

        CreditNote cn = (CreditNote) service.generateReturnCreditNote(9L, "admin").get("creditNote");
        CreditNoteItem it = cn.getItems().get(0);

        assertEquals(9.0, it.getCgstRate(), 0.001);
        assertEquals(3000.0, it.getTaxableValue(), 0.001);
        assertEquals(270.0, it.getCgstAmount(), 0.001);
        assertEquals(3540.0, cn.getTotalAmount(), 0.001);
    }
}
