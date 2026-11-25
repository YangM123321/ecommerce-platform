package com.miayang.ecommerce.order.internal;

/**
 * Order lifecycle.
 * Session 3 (one database): stock is reserved immediately, so a new order is CONFIRMED.
 * Session 7 (Kafka): new orders start PENDING and become CONFIRMED when the inventory service replies.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    CANCELLED
}
