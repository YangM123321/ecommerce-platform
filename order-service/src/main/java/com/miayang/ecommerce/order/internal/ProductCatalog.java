package com.miayang.ecommerce.order.internal;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * What the order logic needs from the product side. OrderService only knows this interface;
 * RemoteProductCatalog implements it with HTTP calls to product-service.
 * (Session 3: same idea, but the implementation was a plain Java call.)
 *
 * Every method can now fail in ways a Java call couldn't: product-service down, slow, or
 * the network dropping the reply. Those become ApiException 503 (see RemoteProductCatalog).
 */
public interface ProductCatalog {

    /** Active products by id; unknown or deactivated ids are missing from the map. */
    Map<Long, ProductInfo> findActiveProducts(Collection<Long> productIds);

    /** Reserves every line or none. Throws ApiException 409 if any line is short of stock. */
    void reserve(Map<Long, Integer> quantities);

    /** Gives reserved stock back. */
    void release(Map<Long, Integer> quantities);

    record ProductInfo(Long id, String name, BigDecimal price) {
    }
}
