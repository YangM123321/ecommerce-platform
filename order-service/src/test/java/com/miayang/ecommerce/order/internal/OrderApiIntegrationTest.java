package com.miayang.ecommerce.order.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miayang.ecommerce.common.security.JwtProperties;
import com.miayang.ecommerce.order.TestTokens;
import com.miayang.ecommerce.order.TestcontainersConfig;
import com.miayang.ecommerce.order.client.ProductServiceClient;
import com.miayang.ecommerce.order.client.ProductServiceClient.ProductSnapshot;

import feign.FeignException;
import feign.Request;
import feign.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * order-service on its own: real HTTP layer, real security, real Postgres (Testcontainers).
 * product-service is replaced by a Mockito mock of the Feign client, so we can make it
 * answer, refuse, or be "down" on demand.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OrderApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JwtProperties jwtProperties;

    @MockitoBean
    ProductServiceClient productService;

    String alice;
    String bob;

    @BeforeEach
    void setUp() {
        alice = TestTokens.bearer(jwtProperties, 1001, "USER");
        bob = TestTokens.bearer(jwtProperties, 1002, "USER");
        when(productService.lookup(any())).thenReturn(List.of(
                new ProductSnapshot(1L, "Mouse", new BigDecimal("10.00")),
                new ProductSnapshot(2L, "Book", new BigDecimal("5.50"))));
    }

    private String order(Object... productIdAndQty) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < productIdAndQty.length; i += 2) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"productId\":").append(productIdAndQty[i])
                    .append(",\"quantity\":").append(productIdAndQty[i + 1]).append('}');
        }
        return "{\"items\":[" + items + "]}";
    }

    private long placeOrder(String token, String body) throws Exception {
        String response = mvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private static FeignException remoteError(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://product-service/internal/stock/reservations",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason("test").request(request).headers(Map.of())
                .body("{\"detail\":\"Not enough stock for product 1\"}", StandardCharsets.UTF_8).build();
        return FeignException.errorStatus("ProductServiceClient#reserve", response);
    }

    @Test
    void ordersNeedAToken() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void placeOrderSavesItAndReservesStockInProductService() throws Exception {
        long id = placeOrder(alice, order(1, 2, 2, 1));

        mvc.perform(get("/api/orders/" + id).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(25.50))
                .andExpect(jsonPath("$.items.length()").value(2));
        verify(productService).reserve(any());
    }

    @Test
    void outOfStockIs409WithProductServicesMessage() throws Exception {
        doThrow(remoteError(409)).when(productService).reserve(any());

        mvc.perform(post("/api/orders").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content(order(1, 99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Not enough stock for product 1"));
    }

    @Test
    void productServiceDownIs503NotACrash() throws Exception {
        doThrow(remoteError(503)).when(productService).reserve(any());

        mvc.perform(post("/api/orders").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content(order(1, 1)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownProductIs404AndNothingIsReserved() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content(order(999, 1)))
                .andExpect(status().isNotFound());
        verify(productService, never()).reserve(any());
    }

    @Test
    void otherUsersCannotSeeOrCancelYourOrder() throws Exception {
        long id = placeOrder(alice, order(1, 1));

        mvc.perform(get("/api/orders/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", bob)).andExpect(status().isNotFound());
    }

    @Test
    void cancelReleasesStockAndSecondCancelIsAConflict() throws Exception {
        long id = placeOrder(alice, order(1, 1));

        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(productService).release(any());

        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", alice))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelWhileProductServiceIsDownLeavesTheOrderConfirmed() throws Exception {
        long id = placeOrder(alice, order(1, 1));
        doThrow(remoteError(503)).when(productService).release(any());

        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", alice))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/orders/" + id).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void myOrdersListsOnlyMine() throws Exception {
        placeOrder(bob, order(2, 1));

        mvc.perform(get("/api/orders").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }
}
