package com.miayang.ecommerce.product.api;

import java.math.BigDecimal;

/** The product facts another module needs at order time: what it's called and what it costs now. */
public record ProductSnapshot(Long id, String name, BigDecimal price) {
}
