package com.miayang.ecommerce.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.miayang.ecommerce.common.security.CurrentUser;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderItemRequest;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.order.internal.ProductCatalog.ProductInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Order rules without Spring, a database or product-service.
 * ProductCatalog (the remote product-service) is a Mockito mock, so we can make it fail on purpose.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    static final Long USER_ID = 7L;
    static final CurrentUser OWNER = new CurrentUser(USER_ID, false);
    static final CurrentUser STRANGER = new CurrentUser(99L, false);

    @Mock
    OrderRepository orders;

    @Mock
    ProductCatalog catalog;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        // A TransactionTemplate over a mock manager just runs the code - enough for unit tests.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        orderService = new OrderService(orders, catalog, tx);
    }

    private void productsExist() {
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(
                1L, new ProductInfo(1L, "Mouse", new BigDecimal("10.00")),
                2L, new ProductInfo(2L, "Book", new BigDecimal("5.50"))));
    }

    @Test
    void placeOrderMergesDuplicateLinesReservesOnceAndComputesTotal() {
        productsExist();
        when(orders.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse order = orderService.placeOrder(USER_ID, new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1),
                new OrderItemRequest(1L, 1))));   // same product again -> merged into 3

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.items()).hasSize(2);
        assertThat(order.totalAmount()).isEqualByComparingTo("35.50");   // 3 x 10.00 + 1 x 5.50
        verify(catalog).reserve(Map.of(1L, 3, 2L, 1));                     // ONE remote call for all lines
        verify(catalog, never()).release(any());
    }

    @Test
    void placeOrderFailsWithoutReservingWhenAProductDoesNotExist() {
        productsExist();

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(999L, 1)))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
                .hasMessageContaining("999");

        verify(catalog, never()).reserve(any());
        verify(orders, never()).save(any());
    }

    @Test
    void outOfStockFromProductServiceMeansNoOrderIsSaved() {
        productsExist();
        doThrow(new ApiException(HttpStatus.CONFLICT, "Not enough stock for product 1"))
                .when(catalog).reserve(any());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID,
                new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 50)))))
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(orders, never()).save(any());
    }

    @Test
    void productServiceDownMeans503AndNoOrder() {
        when(catalog.findActiveProducts(any()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product service is unavailable"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID,
                new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 1)))))
                .hasFieldOrPropertyWithValue("status", HttpStatus.SERVICE_UNAVAILABLE);

        verify(catalog, never()).reserve(any());
        verify(orders, never()).save(any());
    }

    @Test
    void whenSavingTheOrderFailsTheReservedStockIsReleased() {
        productsExist();
        when(orders.save(any(CustomerOrder.class))).thenThrow(new DataAccessResourceFailureException("orders_db down"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID,
                new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)))))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(catalog).reserve(Map.of(1L, 2));
        verify(catalog).release(Map.of(1L, 2));   // the compensating action
    }

    @Test
    void ifCompensationAlsoFailsTheOriginalErrorIsKeptAndTheReleaseErrorAttached() {
        productsExist();
        when(orders.save(any(CustomerOrder.class))).thenThrow(new DataAccessResourceFailureException("orders_db down"));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down")).when(catalog).release(any());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID,
                new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)))))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void cancelReleasesStockAndMarksTheOrderCancelled() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 2);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(5L, OWNER);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(catalog).release(Map.of(1L, 2));
    }

    @Test
    void cancelKeepsTheOrderConfirmedIfProductServiceIsDown() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 2);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down")).when(catalog).release(any());

        assertThatThrownBy(() -> orderService.cancel(5L, OWNER))
                .hasFieldOrPropertyWithValue("status", HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancellingTwiceIsAConflict() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        order.addItem(1L, "Mouse", new BigDecimal("10.00"), 1);
        order.cancel();
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(5L, OWNER))
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        verify(catalog, never()).release(any());
    }

    @Test
    void someoneElsesOrderLooksLikeItDoesNotExist() {
        CustomerOrder order = new CustomerOrder(USER_ID);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(5L, STRANGER))
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }
}
