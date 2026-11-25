package com.miayang.ecommerce.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miayang.ecommerce.common.web.ApiException;

import feign.FeignException;
import feign.Request;
import feign.Response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** How remote failures are turned into API errors the customer sees. */
class RemoteProductCatalogTest {

    RemoteProductCatalog catalog = new RemoteProductCatalog(mock(ProductServiceClient.class), new ObjectMapper());

    private static FeignException failure(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://product-service/internal/stock/reservations",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("ProductServiceClient#reserve", response);
    }

    @Test
    void conflictKeepsProductServicesMessage() {
        ApiException e = catalog.translate(failure(409, "{\"detail\":\"Not enough stock for product 3\"}"), "reserve stock");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getMessage()).isEqualTo("Not enough stock for product 3");
    }

    @Test
    void conflictWithoutJsonBodyStillGivesAMessage() {
        ApiException e = catalog.translate(failure(409, "oops"), "reserve stock");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getMessage()).isEqualTo("Not enough stock");
    }

    @Test
    void serverErrorBecomes503() {
        ApiException e = catalog.translate(failure(500, ""), "reserve stock");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectedTokenBecomes502NotTheCustomersFault() {
        ApiException e = catalog.translate(failure(401, ""), "reserve stock");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
