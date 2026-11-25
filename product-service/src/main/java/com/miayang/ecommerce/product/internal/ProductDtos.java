package com.miayang.ecommerce.product.internal;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the product module's REST API. */
public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 50) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Long categoryId,
            @NotNull @Min(0) @Max(1_000_000) Integer initialStock) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Long categoryId) {
    }

    public record AddStockRequest(@NotNull @Min(1) @Max(1_000_000) Integer quantity) {
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record CategoryResponse(Long id, String name) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName());
        }
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            Long categoryId,
            String categoryName,
            int available,
            String imageUrl) {

        public static ProductResponse from(Product product, int available) {
            return new ProductResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    available,
                    product.getImageUrl());
        }
    }
}
