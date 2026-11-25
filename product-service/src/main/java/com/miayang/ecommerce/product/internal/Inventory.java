package com.miayang.ecommerce.product.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stock for one product. Stock is never changed by "read, modify, save" in Java -
 * only by the atomic UPDATE queries in InventoryRepository, so concurrent orders can't oversell.
 */
@Entity
@Table(name = "inventory", schema = "catalog")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    protected Inventory() {
        // for JPA
    }

    public Inventory(Long productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    public Long getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}
