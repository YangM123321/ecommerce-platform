package com.miayang.ecommerce.product.internal;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/** Reusable query filters. ProductService combines only the ones the caller asked for. */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Product> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** Case-insensitive "name contains text". */
    static Specification<Product> nameContains(String text) {
        String pattern = "%" + text.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }
}
