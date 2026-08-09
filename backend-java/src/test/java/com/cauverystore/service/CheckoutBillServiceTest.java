package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The bill a customer reads before paying. Its job is to match the invoice they will get, so
 * these cover the ways it could differ: wrong tax heads, a line quietly left out of the total,
 * or a figure taken from somewhere other than the stored cart.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutBillServiceTest {

    @Mock private CartService cartService;
    @Mock private GstRateResolver rateResolver;
    @Mock private GstInvoiceService invoiceService;
    @Mock private GstConfigurationRepository gstConfigRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;

    private CheckoutBillService service;
    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        service = new CheckoutBillService(cartService, rateResolver, invoiceService, gstConfigRepo, sellerRegRepo);
        user = new User();
        user.setId(7L);
        cart = new Cart();
        cart.setItems(new ArrayList<>());
        when(cartService.getCart(user)).thenReturn(cart);
        when(invoiceService.resolveDeliveryState(any())).thenReturn("33");
    }

    private Product seller(long productId, long sellerId, String name, double price, String stateCode) {
        Product p = new Product();
        p.setId(productId);
        p.setSellerId(sellerId);
        p.setName(name);
        p.setPrice(price);
        GstConfiguration cfg = new GstConfiguration();
        cfg.setGstin(stateCode + "ABCDE1234F1Z5");
        when(gstConfigRepo.findBySellerId(sellerId)).thenReturn(Optional.of(cfg));
        return p;
    }

    private void addToCart(Product p, int qty) {
        CartItem item = new CartItem();
        item.setProduct(p);
        item.setQuantity(qty);
        cart.getItems().add(item);
    }

    private GstRateResolver.Resolved resolved(double rate, boolean interState, String hsn) {
        return new GstRateResolver.Resolved(rate, interState, hsn, true);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lines(Map<String, Object> bill) {
        return (List<Map<String, Object>>) bill.get("lines");
    }

    @Test
    void shouldItemiseEachLineWithItsOwnCodeRateAndTax() {
        // A basket carrying two rates is the whole reason a single "Tax" figure is not enough.
        Product lungi = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        Product charger = seller(2L, 100L, "Phone Charger", 600.0, "33");
        addToCart(lungi, 2);
        addToCart(charger, 1);
        when(rateResolver.resolve(eq(lungi), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "52095110"));
        when(rateResolver.resolve(eq(charger), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(18.0, false, "85044030"));

        Map<String, Object> bill = service.billFor(user, new Address());

        List<Map<String, Object>> rows = lines(bill);
        assertEquals(2, rows.size());
        assertEquals("52095110", rows.get(0).get("hsnCode"));
        assertEquals(900.0, rows.get(0).get("taxableValue"));
        assertEquals(45.0, rows.get(0).get("taxAmount"));
        assertEquals(945.0, rows.get(0).get("lineTotal"), "the customer needs the post-tax figure");
        assertEquals(108.0, rows.get(1).get("taxAmount"));
        assertEquals(1500.0, bill.get("taxableValue"));
        assertEquals(153.0, bill.get("totalTax"));
        assertEquals(1653.0, bill.get("payable"));
        assertEquals(Boolean.TRUE, bill.get("canProceed"));
    }

    @Test
    void shouldSplitCgstAndSgstWithinTheState() {
        Product p = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        addToCart(p, 1);
        when(rateResolver.resolve(any(), eq(false), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "52095110"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals("CGST+SGST", bill.get("taxType"));
        assertEquals(11.25, bill.get("cgstAmount"));
        assertEquals(11.25, bill.get("sgstAmount"));
        assertEquals(0.0, bill.get("igstAmount"));
    }

    @Test
    void shouldChargeIgstWhenTheSellerIsInAnotherState() {
        // Karnataka seller, Tamil Nadu delivery. Getting this backwards puts the tax in the
        // wrong state's account, which the total being right does not cure.
        Product p = seller(1L, 200L, "Silk Saree", 3000.0, "29");
        addToCart(p, 1);
        when(rateResolver.resolve(any(), eq(true), any(), anyDouble()))
                .thenReturn(resolved(18.0, true, "50072010"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals("IGST", bill.get("taxType"));
        assertEquals(540.0, bill.get("igstAmount"));
        assertEquals(0.0, bill.get("cgstAmount"));
    }

    @Test
    void shouldStopTheOrderWhenALineCannotBeTaxed() {
        // Leaving it out would quote a total lower than the invoice; taxing it at nil would be
        // a rate nobody published. Neither is a bill the customer should be shown.
        Product ok = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        Product unclassified = seller(2L, 100L, "Mystery Item", 100.0, "33");
        addToCart(ok, 1);
        addToCart(unclassified, 1);
        when(rateResolver.resolve(eq(ok), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "52095110"));
        when(rateResolver.resolve(eq(unclassified), anyBoolean(), any(), anyDouble()))
                .thenThrow(new GstRateResolver.GstRateUnresolvedException("no published rate for it"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(Boolean.FALSE, bill.get("canProceed"));
        assertEquals(1, lines(bill).size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocked = (List<Map<String, Object>>) bill.get("blocked");
        assertEquals("Mystery Item", blocked.get(0).get("description"));
        assertTrue(String.valueOf(bill.get("message")).contains("cannot be placed"));
    }

    @Test
    void shouldRefuseToGuessTheTaxHeadsWithoutADestination() {
        Product p = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        addToCart(p, 1);
        when(invoiceService.resolveDeliveryState(any()))
                .thenThrow(new IllegalStateException("no delivery address, so no place of supply"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(Boolean.FALSE, bill.get("canProceed"));
        assertNull(bill.get("taxType"));
        assertTrue(String.valueOf(bill.get("message")).contains("place of supply"));
    }

    @Test
    void shouldBlockALineWhoseSellerHasNoRegisteredState() {
        // Without the seller's state there is no way to tell IGST from CGST+SGST, and guessing
        // intra-state would misdirect the tax on every inter-state sale.
        Product p = new Product();
        p.setId(1L);
        p.setSellerId(300L);
        p.setName("Unregistered seller's goods");
        p.setPrice(200.0);
        when(gstConfigRepo.findBySellerId(300L)).thenReturn(Optional.empty());
        addToCart(p, 1);

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(Boolean.FALSE, bill.get("canProceed"));
        assertTrue(lines(bill).isEmpty());
    }

    @Test
    void shouldUseTheRegistrationGstinWhenNoGstConfigurationExists() {
        // A registered seller who has not filled in the marketplace GST form must not block
        // the order: the registration GSTIN carries the same state any invoice would use.
        Product p = new Product();
        p.setId(1L);
        p.setSellerId(400L);
        p.setName("Cotton Tshirt men");
        p.setPrice(499.0);
        when(gstConfigRepo.findBySellerId(400L)).thenReturn(Optional.empty());
        SellerRegistration reg = new SellerRegistration();
        reg.setGstin("33ABCDE1234F1Z5");
        when(sellerRegRepo.findByUserId(400L)).thenReturn(Optional.of(reg));
        addToCart(p, 1);
        when(rateResolver.resolve(any(), eq(false), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "61091000"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(Boolean.TRUE, bill.get("canProceed"));
        assertEquals("CGST+SGST", bill.get("taxType"));
        assertEquals(12.48, bill.get("cgstAmount"));
        assertEquals(12.48, bill.get("sgstAmount"));
    }

    @Test
    void shouldUseTheOfferPriceWhenOneApplies() {
        // Apparel and footwear are banded on sale value, so billing the list price could put an
        // item in the wrong band as well as overstating the tax.
        Product p = seller(1L, 100L, "Discounted Shoes", 3000.0, "33");
        p.setOfferPrice(2400.0);
        addToCart(p, 1);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "64031990"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(2400.0, lines(bill).get(0).get("unitPrice"));
        verify(rateResolver).resolve(any(), anyBoolean(), any(), eq(2400.0));
    }

    @Test
    void shouldIgnoreItemsSavedForLater() {
        Product p = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        CartItem saved = new CartItem();
        saved.setProduct(p);
        saved.setQuantity(1);
        saved.setSavedForLater(true);
        cart.getItems().add(saved);

        Map<String, Object> bill = service.billFor(user, new Address());

        assertTrue(lines(bill).isEmpty());
        assertEquals(Boolean.FALSE, bill.get("canProceed"), "an empty order is not payable");
    }

    @Test
    void shouldTakePricesFromTheCartOnly() {
        // Nothing about the bill may come from the caller, or a checkout could be edited to pay
        // tax on a figure lower than the goods actually sell for.
        Product p = seller(1L, 100L, "Cotton Lungi", 450.0, "33");
        addToCart(p, 3);
        when(rateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenReturn(resolved(5.0, false, "52095110"));

        Map<String, Object> bill = service.billFor(user, new Address());

        assertEquals(1350.0, lines(bill).get(0).get("taxableValue"),
                "3 x 450 from the stored cart, whatever the request said");
    }
}
