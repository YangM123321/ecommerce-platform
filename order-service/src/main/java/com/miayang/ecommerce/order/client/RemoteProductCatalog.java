package com.miayang.ecommerce.order.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.order.client.ProductServiceClient.ProductLookupRequest;
import com.miayang.ecommerce.order.client.ProductServiceClient.StockLine;
import com.miayang.ecommerce.order.client.ProductServiceClient.StockRequest;
import com.miayang.ecommerce.order.internal.ProductCatalog;

import feign.FeignException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * ProductCatalog over HTTP. Its main job is turning network failures into clear API errors:
 *
 *   product-service says 409 (not enough stock)   -> 409 to the customer, with its message
 *   product-service says 404                      -> 404
 *   product-service down / timeout / 5xx          -> 503 "try again later"
 *   anything else (e.g. it rejected our token)    -> 502 bad gateway
 */
@Component
public class RemoteProductCatalog implements ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(RemoteProductCatalog.class);

    private final ProductServiceClient client;
    private final ObjectMapper objectMapper;

    public RemoteProductCatalog(ProductServiceClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, ProductInfo> findActiveProducts(Collection<Long> productIds) {
        try {
            return client.lookup(new ProductLookupRequest(productIds)).stream()
                    .collect(Collectors.toMap(
                            ProductServiceClient.ProductSnapshot::id,
                            p -> new ProductInfo(p.id(), p.name(), p.price())));
        } catch (FeignException e) {
            throw translate(e, "look up products");
        }
    }

    @Override
    public void reserve(Map<Long, Integer> quantities) {
        try {
            client.reserve(toRequest(quantities));
        } catch (FeignException e) {
            throw translate(e, "reserve stock");
        }
    }

    @Override
    public void release(Map<Long, Integer> quantities) {
        try {
            client.release(toRequest(quantities));
        } catch (FeignException e) {
            throw translate(e, "release stock");
        }
    }

    private static StockRequest toRequest(Map<Long, Integer> quantities) {
        List<StockLine> lines = quantities.entrySet().stream()
                .map(entry -> new StockLine(entry.getKey(), entry.getValue()))
                .toList();
        return new StockRequest(lines);
    }

    ApiException translate(FeignException e, String action) {
        int status = e.status();   // -1 = no HTTP response at all (connection refused, timeout)
        if (status == HttpStatus.CONFLICT.value()) {
            return new ApiException(HttpStatus.CONFLICT, detailOf(e, "Not enough stock"));
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new ApiException(HttpStatus.NOT_FOUND, detailOf(e, "Product not found"));
        }
        if (status < 0 || status >= 500) {
            log.warn("product-service unavailable while trying to {}: {}", action, e.getMessage());
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Product service is unavailable, please try again shortly");
        }
        log.error("product-service rejected request to {} with status {}: {}", action, status, e.contentUTF8());
        return new ApiException(HttpStatus.BAD_GATEWAY, "Product service could not " + action);
    }

    /** product-service answers errors as ProblemDetail JSON; reuse its "detail" message. */
    private String detailOf(FeignException e, String fallback) {
        try {
            JsonNode body = objectMapper.readTree(e.contentUTF8());
            JsonNode detail = body == null ? null : body.get("detail");
            return detail != null && detail.isTextual() ? detail.asText() : fallback;
        } catch (Exception notJson) {
            return fallback;
        }
    }
}
