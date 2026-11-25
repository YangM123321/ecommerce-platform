package com.miayang.ecommerce.product.internal;

import java.util.Comparator;
import java.util.List;

import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.product.internal.StockDtos.ProductSnapshot;
import com.miayang.ecommerce.product.internal.StockDtos.StockLine;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock operations for other services.
 *
 * reserveAll() reserves EVERY line of an order in one local transaction: if one product is
 * short, the exception rolls back the others. So order-service makes one call, and the
 * all-or-nothing guarantee from Session 3 still holds inside product-service.
 */
@Service
@Transactional
public class StockService {

    private final ProductRepository products;
    private final InventoryRepository inventory;

    public StockService(ProductRepository products, InventoryRepository inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    /** Active products only; ids that don't exist or are deactivated are left out. */
    @Transactional(readOnly = true)
    public List<ProductSnapshot> findActiveProducts(List<Long> productIds) {
        return products.findByIdInAndActiveTrue(productIds).stream()
                .map(p -> new ProductSnapshot(p.getId(), p.getName(), p.getPrice()))
                .toList();
    }

    public void reserveAll(List<StockLine> lines) {
        // Product-id order, so two concurrent orders never lock rows in opposite order (no deadlocks).
        sortedByProduct(lines).forEach(line -> {
            if (inventory.reserve(line.productId(), line.quantity()) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Not enough stock for product " + line.productId());
            }
        });
    }

    public void releaseAll(List<StockLine> lines) {
        sortedByProduct(lines).forEach(line -> {
            if (inventory.release(line.productId(), line.quantity()) == 0) {
                // Should never happen: reserved stock and orders are out of sync.
                throw new IllegalStateException(
                        "Cannot release " + line.quantity() + " of product " + line.productId());
            }
        });
    }

    private static List<StockLine> sortedByProduct(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }
}
