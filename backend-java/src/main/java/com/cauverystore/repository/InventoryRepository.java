package com.cauverystore.repository;

import com.cauverystore.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Inventory findByProduct_Id(Long productId);

    // Atomic conditional decrement - the WHERE clause makes the check-and-decrement a single
    // statement, so two concurrent requests can't both read the same pre-decrement stock value
    // and both succeed. Returns the number of rows updated: 0 means insufficient stock.
    // Deliberately NOT clearAutomatically=true: that detaches every entity already loaded in
    // the caller's persistence context (e.g. an Order fetched earlier in the same transaction),
    // breaking its lazy collections for the rest of that transaction.
    @Modifying
    @Query("UPDATE Inventory i SET i.stock = i.stock - :qty WHERE i.product.id = :productId AND i.stock >= :qty")
    int decrementStockIfAvailable(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Inventory i SET i.stock = i.stock + :qty WHERE i.product.id = :productId")
    int incrementStock(@Param("productId") Long productId, @Param("qty") int qty);
}
