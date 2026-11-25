package com.miayang.ecommerce.order.internal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.miayang.ecommerce.common.security.CurrentUser;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.common.web.PageResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderSummary;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.order.internal.ProductCatalog.ProductInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Placing and cancelling orders - now across TWO services and TWO databases.
 *
 * Session 3 had one database transaction around everything, so a failure undid it all.
 * That's impossible now: orders_db and catalog_db are separate. Instead:
 *   1. product-service reserves all lines in ITS transaction (all or nothing there).
 *   2. order-service saves the order in ITS transaction.
 *   3. If step 2 fails, we undo step 1 by calling release - a "compensating action".
 *
 * Remaining gap (fixed in Session 7 with Kafka + the outbox pattern): if product-service
 * reserves the stock but the reply is lost (timeout), we report an error but the stock stays
 * reserved. That's why distributed systems use events and idempotency.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ProductCatalog catalog;
    private final TransactionTemplate transaction;

    public OrderService(OrderRepository orders, ProductCatalog catalog, TransactionTemplate transaction) {
        this.orders = orders;
        this.catalog = catalog;
        this.transaction = transaction;
    }

    /**
     * Deliberately NOT @Transactional: holding a database transaction open while waiting
     * on network calls ties up a connection and row locks for as long as the network is slow.
     */
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        // 1. Merge duplicate lines; sorted by product id.
        Map<Long, Integer> quantities = new TreeMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));

        // 2. Current names and prices from product-service.
        Map<Long, ProductInfo> products = catalog.findActiveProducts(quantities.keySet());
        List<Long> missing = quantities.keySet().stream().filter(id -> !products.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Products not found: " + missing);
        }

        // 3. Reserve everything in one call (product-service does it all-or-nothing).
        catalog.reserve(quantities);

        // 4. Save the order locally. If that fails, compensate by releasing the stock.
        try {
            return transaction.execute(status -> {
                CustomerOrder order = new CustomerOrder(userId);
                quantities.forEach((productId, quantity) -> {
                    ProductInfo product = products.get(productId);
                    order.addItem(productId, product.name(), product.price(), quantity);
                });
                return OrderResponse.from(orders.save(order));
            });
        } catch (RuntimeException saveFailed) {
            compensate(quantities, saveFailed);
            throw saveFailed;
        }
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

    /**
     * Lock the order, give the stock back, then mark it cancelled - all before the local commit.
     * If product-service is down, release throws, the local transaction rolls back and the order
     * stays CONFIRMED, so the customer can simply try again.
     */
    public OrderResponse cancel(Long orderId, CurrentUser user) {
        return transaction.execute(status -> {
            CustomerOrder order = orders.findByIdForUpdate(orderId)
                    .filter(o -> user.admin() || o.isOwnedBy(user.id()))
                    .orElseThrow(() -> orderNotFound(orderId));

            if (!order.isCancellable()) {
                throw new ApiException(HttpStatus.CONFLICT, "Order " + orderId + " is already " + order.getStatus());
            }

            Map<Long, Integer> quantities = new LinkedHashMap<>();
            order.getItems().stream()
                    .sorted(Comparator.comparing(OrderItem::getProductId))
                    .forEach(item -> quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum));
            catalog.release(quantities);

            order.cancel();
            return OrderResponse.from(order);
        });
    }

    private void compensate(Map<Long, Integer> quantities, RuntimeException cause) {
        try {
            catalog.release(quantities);
            log.warn("Order could not be saved; released reserved stock {}", quantities);
        } catch (RuntimeException releaseFailed) {
            // Needs manual attention (or, from Session 7, an automatic retry from the outbox).
            log.error("Order could not be saved AND stock {} could not be released", quantities, releaseFailed);
            cause.addSuppressed(releaseFailed);
        }
    }

    /** 404, not 403, for someone else's order: a 403 would confirm that the order id exists. */
    private static ApiException orderNotFound(Long orderId) {
        return new ApiException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found");
    }
}
