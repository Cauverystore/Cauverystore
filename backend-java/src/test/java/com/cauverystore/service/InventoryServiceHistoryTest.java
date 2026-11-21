package com.cauverystore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.entities.StockMovement;
import com.cauverystore.repository.InventoryRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import com.cauverystore.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceHistoryTest {
    @Mock private InventoryRepository inventoryRepo;
    @Mock private ProductRepository productRepo;
    @Mock private ProductService productService;
    @Mock private ProductVariantRepository variantRepo;
    @Mock private StockMovementRepository movementRepo;

    private InventoryService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepo, productRepo, productService, variantRepo,
                new StockMovementService(movementRepo));
        product = new Product();
        product.setId(5L);
        when(movementRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryRepo.findStockByProductId(5L)).thenReturn(null);
    }

    private StockMovement saved() {
        ArgumentCaptor<StockMovement> row = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepo).save(row.capture());
        return row.getValue();
    }

    @Test
    void aSaleIsRecordedWithTheNewBalance() {
        when(productRepo.decrementStockIfAvailable(5L, 2)).thenReturn(1);
        when(productRepo.findStockById(5L)).thenReturn(8);

        service.deductStock(product, null, 2);

        StockMovement row = saved();
        assertEquals("OUT", row.getType());
        assertEquals(2, row.getQuantity());
        assertEquals(8, row.getBalanceAfter());
    }

    @Test
    void aRefusedSaleLeavesNoHistory() {
        when(productRepo.decrementStockIfAvailable(5L, 2)).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.deductStock(product, null, 2));

        verify(movementRepo, never()).save(any());
    }

    @Test
    void aSizeIsRecordedWithItsOwnBalance() {
        ProductVariant size = new ProductVariant();
        size.setId(9L);
        when(variantRepo.decrementStockIfAvailable(9L, 1)).thenReturn(1);
        when(productRepo.decrementStockIfAvailable(5L, 1)).thenReturn(1);
        when(variantRepo.findStockById(9L)).thenReturn(3);

        service.deductStock(product, size, 1);

        assertEquals(3, saved().getBalanceAfter());
    }

    @Test
    void aCancellationNamesTheOrder() {
        when(productRepo.findStockById(5L)).thenReturn(10);

        service.restoreForCancelledOrder(product, null, 2, 77L);

        verify(productRepo).incrementStock(5L, 2);
        StockMovement row = saved();
        assertEquals("IN", row.getType());
        assertEquals("ORDER", row.getReferenceType());
        assertEquals(77L, row.getReferenceId());
    }

    @Test
    void aReturnAndAPurchaseAreRecordedAsSuch() {
        service.restoreForReturn(product, null, 1, 4L);
        service.receivePurchase(product, null, 6, 3L);

        ArgumentCaptor<StockMovement> rows = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepo, times(2)).save(rows.capture());
        assertEquals("RETURN", rows.getAllValues().get(0).getType());
        assertEquals("PURCHASE_ORDER", rows.getAllValues().get(1).getReferenceType());
    }

    @Test
    void anEditRecordsTheDifferenceAndNoChangeRecordsNothing() {
        StockMovementService history = new StockMovementService(movementRepo);

        history.recordEdit(product, null, 10, 10);
        verify(movementRepo, never()).save(any());

        history.recordEdit(product, null, 10, 7);
        StockMovement row = saved();
        assertEquals("ADJUSTMENT", row.getType());
        assertEquals(-3, row.getQuantity());
        assertEquals(7, row.getBalanceAfter());
    }
}
