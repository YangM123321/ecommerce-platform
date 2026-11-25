package com.miayang.ecommerce.product.api;

import java.util.Collection;
import java.util.Map;

/**
 * The ONLY way other modules talk to the product module (ModuleBoundariesTest enforces this).
 *
 * Today these are plain Java calls inside one app and one database transaction.
 * In Session 4 this interface becomes a REST client to the product service, and in
 * Session 7 stock reservation becomes a Kafka event - callers barely change.
 */
public interface ProductCatalogApi {

    /** Active products by id. Ids that don't exist or are deactivated are simply missing from the map. */
    Map<Long, ProductSnapshot> findActiveProducts(Collection<Long> productIds);

    /**
     * Atomically moves {@code quantity} from available to reserved stock.
     *
     * @return false if there isn't enough stock (nothing is changed in that case)
     */
    boolean reserveStock(Long productId, int quantity);

    /** Gives reserved stock back, e.g. when an order is cancelled. */
    void releaseStock(Long productId, int quantity);
}
