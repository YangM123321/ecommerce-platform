package com.miayang.ecommerce.product.internal;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The contract other services use to talk to product-service (/internal/**).
 * order-service has its own copy of these shapes: services share JSON, not Java classes.
 */
public final class StockDtos {

    private StockDtos() {
    }

    public record ProductLookupRequest(@NotEmpty @Size(max = 100) List<Long> productIds) {
    }

    public record ProductSnapshot(Long id, String name, BigDecimal price) {
    }

    public record StockLine(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {
    }

    public record StockRequest(@NotEmpty @Size(max = 100) @Valid List<StockLine> items) {
    }
}
