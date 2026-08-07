package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingLabelServiceTest {

    @Mock
    private OrderRepository orderRepo;

    @Mock
    private SellerRegistrationRepository sellerRegRepo;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthorizationService authorizationService;

    private ShippingLabelService service;

    @BeforeEach
    void setUp() {
        service = new ShippingLabelService(orderRepo, sellerRegRepo, auditService, authorizationService);
        when(sellerRegRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(authorizationService.getCurrentUserId()).thenReturn(1L);
        when(authorizationService.getCurrentUserEmail()).thenReturn("admin@cauverystore.in");
    }

    private Order orderWith(int itemCount, String paymentMethod, boolean withTracking, boolean withInstructions) {
        Order order = new Order();
        order.setId(itemCount * 100L + paymentMethod.length());
        order.setStatus("PROCESSING");
        order.setPaymentMethod(paymentMethod);
        order.setSellerId(1L);
        if (withTracking) {
            order.setTrackingNumber("TRK" + order.getId());
        }
        order.setCourier("FastGo Couriers");

        Address addr = new Address();
        addr.setFullName("Arun Kumar");
        addr.setStreet("12, First Main Road, Mylapore, Teynampet, Chennai - 600004, Tamil Nadu, India");
        addr.setCity("Chennai");
        addr.setState("Tamil Nadu");
        addr.setPincode("600004");
        addr.setPhone("+91 98765 43210");
        if (withInstructions) {
            addr.setDeliveryInstructions("Please call before delivery and ring the doorbell twice");
        }
        order.setAddress(addr);

        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Product product = new Product();
            product.setName("Product number " + (i + 1) + " - deliberately long name that would wrap");
            product.setWeight(1.5);
            product.setPackageWeight(2.0);
            product.setShippingClass("fragile");
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(i + 1);
            items.add(item);
        }
        order.setItems(items);
        return order;
    }

    @Test
    void largeCodOrderWithInstructionsFitsOnOnePage() throws Exception {
        Order order = orderWith(8, "COD", true, true);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        byte[] pdf = service.generateLabelPdf(order.getId());

        assertEquals(1, pageCount(pdf), "a large COD order must still produce a one-page label");
    }

    @Test
    void prepaidOrderWithoutTrackingFitsOnOnePage() throws Exception {
        Order order = orderWith(1, "PREPAID", false, false);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        byte[] pdf = service.generateLabelPdf(order.getId());

        assertEquals(1, pageCount(pdf));
    }

    private int pageCount(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }
}
