package com.miayang.ecommerce.shared.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A stable JSON shape for paged results:
 * { "content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
 * (Spring's own Page object isn't meant to be serialized directly.)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
