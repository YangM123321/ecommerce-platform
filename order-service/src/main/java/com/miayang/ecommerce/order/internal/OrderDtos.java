package com.miayang.ecommerce.order.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the order module's REST API. */
public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    /** @Valid on the list makes the validator check every item too. */
    public record PlaceOrderRequest(
            @NotEmpty @Size(max = 50) @Valid List<OrderItemRequest> items) {
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getProductId(), item.getProductName(), item.getUnitPrice(),
                    item.getQuantity(), item.getLineTotal());
        }
    }

    public record OrderResponse(
            Long id,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemResponse> items) {

        static OrderResponse from(CustomerOrder order) {
            return new OrderResponse(
                    order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(),
                    order.getCreatedAt(), order.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    /** For lists: no items, so listing orders doesn't load every order line. */
    public record OrderSummary(
            Long id,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant createdAt) {

        static OrderSummary from(CustomerOrder order) {
            return new OrderSummary(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
        }
    }
}
