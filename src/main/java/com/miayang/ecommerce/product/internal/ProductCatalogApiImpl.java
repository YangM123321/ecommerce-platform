package com.miayang.ecommerce.product.internal;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.miayang.ecommerce.product.api.ProductCatalogApi;
import com.miayang.ecommerce.product.api.ProductSnapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product module's implementation of its public API.
 * Uses the default propagation (REQUIRED), so when the order module calls it inside
 * its own transaction, stock changes commit or roll back together with the order.
 */
@Service
@Transactional
public class ProductCatalogApiImpl implements ProductCatalogApi {

    private final ProductRepository products;
    private final InventoryRepository inventory;

    public ProductCatalogApiImpl(ProductRepository products, InventoryRepository inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProductSnapshot> findActiveProducts(Collection<Long> productIds) {
        return products.findByIdInAndActiveTrue(productIds).stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        p -> new ProductSnapshot(p.getId(), p.getName(), p.getPrice())));
    }

    @Override
    public boolean reserveStock(Long productId, int quantity) {
        return inventory.reserve(productId, quantity) == 1;
    }

    @Override
    public void releaseStock(Long productId, int quantity) {
        if (inventory.release(productId, quantity) == 0) {
            // Should never happen: it means reserved stock and orders are out of sync.
            throw new IllegalStateException("Cannot release " + quantity + " of product " + productId);
        }
    }
}
