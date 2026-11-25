package com.miayang.ecommerce.product.internal;

import com.miayang.ecommerce.product.internal.ProductDtos.AddStockRequest;
import com.miayang.ecommerce.product.internal.ProductDtos.CreateProductRequest;
import com.miayang.ecommerce.product.internal.ProductDtos.ProductResponse;
import com.miayang.ecommerce.product.internal.ProductDtos.UpdateProductRequest;
import com.miayang.ecommerce.common.web.PageResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsing is public (anyone can look at products). Changing the catalog is admin-only:
 * enforced by URL rules in SecurityConfig AND @PreAuthorize here.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** e.g. GET /api/products?categoryId=1&q=mouse&page=0&size=20&sort=price,desc */
    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return productService.search(categoryId, q, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        productService.deactivate(id);
    }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse addStock(@PathVariable Long id, @Valid @RequestBody AddStockRequest request) {
        return productService.addStock(id, request.quantity());
    }
}
