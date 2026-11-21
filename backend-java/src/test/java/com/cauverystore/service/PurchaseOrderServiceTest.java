package com.cauverystore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.entities.PurchaseOrder;
import com.cauverystore.entities.PurchaseOrderItem;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import com.cauverystore.repository.PurchaseOrderRepository;
import com.cauverystore.repository.SupplierRepository;
import com.cauverystore.repository.WarehouseRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceTest {
    @Mock private PurchaseOrderRepository poRepo;
    @Mock private SupplierRepository supplierRepo;
    @Mock private WarehouseRepository warehouseRepo;
    @Mock private ProductRepository productRepo;
    @Mock private ProductVariantRepository variantRepo;
    @Mock private InventoryService inventoryService;
    @InjectMocks private PurchaseOrderService service;

    private PurchaseOrder po;
    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(5L);
        product.setName("Steel Bottle");
        po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus("SHIPPED");
        po.getItems().add(line(product, null, 12));
        when(poRepo.findForUpdate(1L)).thenReturn(Optional.of(po));
        when(poRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(variantRepo.findByProduct_Id(5L)).thenReturn(List.of());
    }

    private PurchaseOrderItem line(Product p, ProductVariant v, int qty) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        item.setProduct(p);
        item.setVariant(v);
        item.setQuantity(qty);
        return item;
    }

    @Test
    void receivingAddsTheGoodsToStock() {
        Map<String, Object> result = service.changeStatus(1L, "RECEIVED");

        verify(inventoryService).receivePurchase(product, null, 12, 1L);
        assertEquals("RECEIVED", result.get("status"));
        assertEquals(12, po.getItems().get(0).getReceivedQuantity());
        assertNotNull(po.getActualDelivery());
    }

    @Test
    void aReceivedOrderCannotBeReceivedOrChangedAgain() {
        service.changeStatus(1L, "RECEIVED");

        assertThrows(RuntimeException.class, () -> service.changeStatus(1L, "RECEIVED"));
        assertThrows(RuntimeException.class, () -> service.changeStatus(1L, "CANCELLED"));
        assertThrows(RuntimeException.class, () -> service.delete(1L));
        verify(inventoryService, times(1)).receivePurchase(any(), any(), anyInt(), any());
    }

    @Test
    void otherStatusesDoNotTouchStock() {
        service.changeStatus(1L, "CONFIRMED");

        verify(inventoryService, never()).receivePurchase(any(), any(), anyInt(), any());
    }

    @Test
    void aProductWithSizesNeedsASizeAndNothingIsAddedUntilItHasOne() {
        Product sized = new Product();
        sized.setId(6L);
        sized.setName("Shirt");
        when(variantRepo.findByProduct_Id(6L)).thenReturn(List.of(new ProductVariant()));
        po.getItems().add(line(sized, null, 3));

        RuntimeException refused = assertThrows(RuntimeException.class, () -> service.changeStatus(1L, "RECEIVED"));

        assertTrue(refused.getMessage().contains("size"));
        verify(inventoryService, never()).receivePurchase(any(), any(), anyInt(), any());
    }

    @Test
    void anOrderWithNoItemsCannotBeReceived() {
        po.getItems().clear();

        assertThrows(RuntimeException.class, () -> service.changeStatus(1L, "RECEIVED"));
    }

    @Test
    void creatingSavesTheChosenProductQuantityAndTotal() {
        when(productRepo.findById(5L)).thenReturn(Optional.of(product));
        var request = new PurchaseOrderService.OrderRequest("PO-1", null, null, "PENDING", null, null, null, null,
                List.of(new PurchaseOrderService.ItemRequest(5L, null, 4, 25.0)));

        Map<String, Object> created = service.create(request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) created.get("items");
        assertEquals(5L, items.get(0).get("productId"));
        assertEquals(4, items.get(0).get("quantity"));
        assertEquals(100.0, created.get("totalAmount"));
        verify(inventoryService, never()).receivePurchase(any(), any(), anyInt(), any());
    }
}
