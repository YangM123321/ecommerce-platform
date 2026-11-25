package com.miayang.ecommerce.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.miayang.ecommerce.order.internal.OrderDtos.OrderItemRequest;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.product.api.ProductCatalogApi;
import com.miayang.ecommerce.product.api.ProductSnapshot;
import com.miayang.ecommerce.shared.security.CurrentUser;
import com.miayang.ecommerce.shared.web.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Order rules without Spring or a database: the product module is a Mockito mock. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    static final Long USER_ID = 7L;

    @Mock
    OrderRepository orders;

    @Mock
    ProductCatalogApi catalog;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orders, catalog);
    }

    @Test
    void placeOrderMergesDuplicateLinesAndComputesTotal() {
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(
                1L, new ProductSnapshot(1L, "Mouse", new BigDecimal("10.00")),
                2L, new ProductSnapshot(2L, "Book", new BigDecimal("5.50"))));
        when(catalog.reserveStock(1L, 3)).thenReturn(true);
        when(catalog.reserveStock(2L, 1)).thenReturn(true);
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse order = orderService.placeOrder(USER_ID, new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1),
                new OrderItemRequest(1L, 1))));   // same product again -> merged into 3

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(0).quantity()).isEqualTo(3);
        assertThat(order.totalAmount()).isEqualByComparingTo("35.50");   // 3 x 10.00 + 1 x 5.50
    }

    @Test
    void placeOrderFailsWhenAProductDoesNotExist() {
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(
                1L, new ProductSnapshot(1L, "Mouse", new BigDecimal("10.00"))));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(999L, 1)))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
                .hasMessageContaining("999");

        verify(catalog, never()).reserveStock(anyLong(), anyInt());
        verify(orders, never()).save(any());
    }

    @Test
    void placeOrderFailsWhenStockRunsOut() {
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(
                1L, new ProductSnapshot(1L, "Mouse", new BigDecimal("10.00")),
                2L, new ProductSnapshot(2L, "Book", new BigDecimal("5.50"))));
        when(catalog.reserveStock(1L, 1)).thenReturn(true);
        when(catalog.reserveStock(2L, 5)).thenReturn(false);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(2L, 5)))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        // No order is saved. (Undoing the reservation of product 1 is the database transaction's
        // job - CatalogAndOrderIntegrationTest proves that against a real Postgres.)
        verify(orders, never()).save(any());
    }

    @Test
    void cancelReleasesStockAndMarksTheOrderCancelled() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(2L, "Book", new BigDecimal("5.50"), 1);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 3);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(5L, new CurrentUser(USER_ID, false));

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(catalog).releaseStock(1L, 3);
        verify(catalog).releaseStock(2L, 1);
    }

    @Test
    void cancellingSomeoneElsesOrderLooksLikeItDoesNotExist() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 1);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(5L, new CurrentUser(99L, false)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(catalog, never()).releaseStock(anyLong(), anyInt());
    }

    @Test
    void adminCanCancelAnyOrder() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 1);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(5L, new CurrentUser(1L, true));

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingTwiceIsRejected() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 1);
        order.cancel();
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(5L, new CurrentUser(USER_ID, false)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(catalog, never()).releaseStock(anyLong(), anyInt());
    }
}
