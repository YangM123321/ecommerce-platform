package com.miayang.ecommerce.order.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.miayang.ecommerce.order.internal.OrderDtos.OrderResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderSummary;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.product.api.ProductCatalogApi;
import com.miayang.ecommerce.product.api.ProductSnapshot;
import com.miayang.ecommerce.shared.security.CurrentUser;
import com.miayang.ecommerce.shared.web.ApiException;
import com.miayang.ecommerce.shared.web.PageResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placing and cancelling orders.
 *
 * Everything in placeOrder runs in ONE database transaction: if any item is out of stock,
 * the exception rolls back the stock already reserved for the other items. That
 * all-or-nothing guarantee is easy here because it's one app and one database - and it's
 * exactly what gets hard after the Session 4 split (then: Kafka + compensating actions).
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final ProductCatalogApi catalog;

    public OrderService(OrderRepository orders, ProductCatalogApi catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }

    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        // 1. Merge duplicate lines (2 x mouse + 1 x mouse = 3 x mouse). A TreeMap keeps product ids
        //    sorted, so concurrent orders always lock inventory rows in the same order - no deadlocks.
        Map<Long, Integer> quantities = new TreeMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));

        // 2. Look up current names and prices through the product module's public API.
        Map<Long, ProductSnapshot> products = catalog.findActiveProducts(quantities.keySet());
        List<Long> missing = quantities.keySet().stream().filter(id -> !products.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Products not found: " + missing);
        }

        // 3. Reserve stock. One failure throws, and the transaction undoes every earlier reservation.
        quantities.forEach((productId, quantity) -> {
            if (!catalog.reserveStock(productId, quantity)) {
                throw new ApiException(HttpStatus.CONFLICT, "Not enough stock for product " + productId);
            }
        });

        // 4. Save the order with a copy of each product's name and price.
        CustomerOrder order = new CustomerOrder(userId);
        quantities.forEach((productId, quantity) -> {
            ProductSnapshot product = products.get(productId);
            order.addItem(productId, product.name(), product.price(), quantity);
        });
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, CurrentUser user) {
        CustomerOrder order = orders.findById(orderId)
                .filter(o -> user.admin() || o.isOwnedBy(user.id()))
                .orElseThrow(() -> orderNotFound(orderId));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> myOrders(Long userId, Pageable pageable) {
        return PageResponse.from(orders.findByUserId(userId, pageable).map(OrderSummary::from));
    }

    public OrderResponse cancel(Long orderId, CurrentUser user) {
        CustomerOrder order = orders.findByIdForUpdate(orderId)
                .filter(o -> user.admin() || o.isOwnedBy(user.id()))
                .orElseThrow(() -> orderNotFound(orderId));

        if (!order.isCancellable()) {
            throw new ApiException(HttpStatus.CONFLICT, "Order " + orderId + " is already " + order.getStatus());
        }

        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> catalog.releaseStock(item.getProductId(), item.getQuantity()));
        order.cancel();
        return OrderResponse.from(order);
    }

    /**
     * 404, not 403, for someone else's order: a 403 would confirm that the order id exists.
     */
    private static ApiException orderNotFound(Long orderId) {
        return new ApiException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found");
    }
}
