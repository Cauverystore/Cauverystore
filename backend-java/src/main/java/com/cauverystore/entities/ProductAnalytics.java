package com.cauverystore.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "product_analytics")
public class ProductAnalytics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    private LocalDate date;
    private Long views = 0L;
    private Long uniqueVisitors = 0L;
    private Long addToCart = 0L;
    private Long wishlistAdds = 0L;
    private Long orders = 0L;
    private Double revenue = 0.0;
    private Double ctr = 0.0;
    private Double conversionRate = 0.0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Long getViews() { return views; }
    public void setViews(Long views) { this.views = views; }
    public Long getUniqueVisitors() { return uniqueVisitors; }
    public void setUniqueVisitors(Long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }
    public Long getAddToCart() { return addToCart; }
    public void setAddToCart(Long addToCart) { this.addToCart = addToCart; }
    public Long getWishlistAdds() { return wishlistAdds; }
    public void setWishlistAdds(Long wishlistAdds) { this.wishlistAdds = wishlistAdds; }
    public Long getOrders() { return orders; }
    public void setOrders(Long orders) { this.orders = orders; }
    public Double getRevenue() { return revenue; }
    public void setRevenue(Double revenue) { this.revenue = revenue; }
    public Double getCtr() { return ctr; }
    public void setCtr(Double ctr) { this.ctr = ctr; }
    public Double getConversionRate() { return conversionRate; }
    public void setConversionRate(Double conversionRate) { this.conversionRate = conversionRate; }
}
