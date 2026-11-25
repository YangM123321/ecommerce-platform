package com.miayang.ecommerce.product.internal;

import java.util.List;

import com.miayang.ecommerce.product.internal.StockDtos.ProductLookupRequest;
import com.miayang.ecommerce.product.internal.StockDtos.ProductSnapshot;
import com.miayang.ecommerce.product.internal.StockDtos.StockRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints, used by order-service through OpenFeign.
 * Not reachable from outside: the gateway only routes /api/**.
 * In Session 3 these were plain Java method calls (ProductCatalogApi).
 */
@RestController
@RequestMapping("/internal")
public class InternalStockController {

    private final StockService stockService;

    public InternalStockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/products/lookup")
    public List<ProductSnapshot> lookup(@Valid @RequestBody ProductLookupRequest request) {
        return stockService.findActiveProducts(request.productIds());
    }

    /** 204 = every line reserved; 409 = nothing reserved (not enough stock for some line). */
    @PostMapping("/stock/reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@Valid @RequestBody StockRequest request) {
        stockService.reserveAll(request.items());
    }

    @PostMapping("/stock/releases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockRequest request) {
        stockService.releaseAll(request.items());
    }
}
