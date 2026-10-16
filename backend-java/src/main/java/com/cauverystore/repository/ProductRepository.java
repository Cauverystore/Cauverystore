package com.cauverystore.repository;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrue();

    /** Every listing a seller has, active or not - used when suspending and reinstating them. */
    List<Product> findBySellerId(Long sellerId);

    List<Product> findByChannelAndActiveTrue(String channel);

    // Atomic conditional decrement, mirroring InventoryRepository.decrementStockIfAvailable -
    // used when a product has no dedicated Inventory row and Product.stock is authoritative.
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :productId AND p.stock >= :qty")
    int decrementStockIfAvailable(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :productId")
    int incrementStock(@Param("productId") Long productId, @Param("qty") int qty);

    List<Product> findByCategoryAndActiveTrue(Category category);

    // What a shopper types is matched against everything a seller fills in to describe the
    // product - brand, category, description and the search keywords - not just its name, so
    // a search for "cotton" finds a tee whose name never says so.
    @Query("SELECT p FROM Product p LEFT JOIN p.category c WHERE p.active=true AND (p.approvalStatus IS NULL OR LOWER(p.approvalStatus) = 'approved')"
            + " AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.brand) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(c.name) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.description) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.metaKeywords) LIKE LOWER(CONCAT('%',:name,'%')))"
            + " AND (:minPrice IS NULL OR p.price >= :minPrice) AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchProducts(@Param("name") String name,
                                 @Param("minPrice") Double minPrice,
                                 @Param("maxPrice") Double maxPrice,
                                 Pageable pageable);

    // Same matching as searchProducts, within one category.
    @Query("SELECT p FROM Product p LEFT JOIN p.category c WHERE p.active=true AND (p.approvalStatus IS NULL OR LOWER(p.approvalStatus) = 'approved')"
            + " AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.brand) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(c.name) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.description) LIKE LOWER(CONCAT('%',:name,'%'))"
            + " OR LOWER(p.metaKeywords) LIKE LOWER(CONCAT('%',:name,'%')))"
            + " AND (:minPrice IS NULL OR p.price >= :minPrice) AND (:maxPrice IS NULL OR p.price <= :maxPrice) AND (:category IS NULL OR p.category = :category)")
    Page<Product> searchProductsByCategory(@Param("name") String name,
                                           @Param("minPrice") Double minPrice,
                                           @Param("maxPrice") Double maxPrice,
                                           @Param("category") Category category,
                                           Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active=true AND (p.approvalStatus IS NULL OR LOWER(p.approvalStatus) = 'approved') AND (:category IS NULL OR p.category = :category) AND (:minPrice IS NULL OR p.price >= :minPrice) AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchAllByCategory(@Param("category") Category category,
                                      @Param("minPrice") Double minPrice,
                                      @Param("maxPrice") Double maxPrice,
                                      Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active=true AND (p.approvalStatus IS NULL OR LOWER(p.approvalStatus) = 'approved') AND (:minPrice IS NULL OR p.price >= :minPrice) AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchAllProducts(@Param("minPrice") Double minPrice,
                                    @Param("maxPrice") Double maxPrice,
                                    Pageable pageable);

    List<Product> findByStockLessThan(int threshold);

    long countByCategory(Category category);

    /** Only products a shopper can actually see - used to decide whether a category/section is worth listing (sitemap, homepage). */
    long countByCategoryAndActiveTrue(Category category);

    long countBySellerId(Long sellerId);

    List<Product> findByApprovalStatus(String approvalStatus);

    @Query("SELECT p.sellerId, COUNT(p) FROM Product p GROUP BY p.sellerId")
    List<Object[]> countBySellerGrouped();

    long countByStock(int stock);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stock < :threshold AND p.active = true")
    long countLowStockProducts(@Param("threshold") int threshold);
}
