package com.miayang.ecommerce.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miayang.ecommerce.TestcontainersConfig;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderItemRequest;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.shared.web.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Catalog + orders end to end: real app, real security, real Postgres (Testcontainers).
 * Every test creates its own category, products and users, so tests don't affect each other.
 */
@SpringBootTest(properties = {
        "app.admin.email=admin@test.local",
        "app.admin.password=AdminPass123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CatalogAndOrderIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    OrderService orderService;

    String adminToken;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin@test.local", "AdminPass123");
        categoryId = createCategory("Test category " + UUID.randomUUID());
    }

    // ---------- catalog ----------

    @Test
    void browsingProductsIsPublicPagedAndFilterable() throws Exception {
        // Seed data from V3 has 8 products, so page size 2 gives a partial page.
        mvc.perform(get("/api/products").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2));

        long productId = createProduct("Searchable Lamp", "19.99", 4);

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].available").value(4));

        mvc.perform(get("/api/products").param("q", "searchable lamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Searchable Lamp"));
    }

    @Test
    void onlyAdminsCanChangeTheCatalog() throws Exception {
        String userToken = registerAndLogin();
        String body = productJson("Not allowed", "9.99", 1);

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/products").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ---------- orders ----------

    @Test
    void placingAnOrderReservesStockAndCancellingGivesItBack() throws Exception {
        long productId = createProduct("Desk Lamp", "19.99", 5);
        String userToken = registerAndLogin();

        JsonNode order = readJson(placeOrder(userToken, Map.of(productId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(39.98))
                .andExpect(jsonPath("$.items[0].productName").value("Desk Lamp")));
        long orderId = order.get("id").asLong();

        assertThat(availableStock(productId)).isEqualTo(3);

        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(availableStock(productId)).isEqualTo(5);

        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", bearer(userToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void anOrderIsAllOrNothing() throws Exception {
        long plenty = createProduct("Plenty", "5.00", 5);
        long scarce = createProduct("Scarce", "5.00", 1);
        String userToken = registerAndLogin();

        // "plenty" can be reserved, "scarce" can't -> the whole order fails...
        placeOrder(userToken, Map.of(plenty, 2, scarce, 3))
                .andExpect(status().isConflict());

        // ...and the reservation for "plenty" was rolled back with it.
        assertThat(availableStock(plenty)).isEqualTo(5);
        assertThat(availableStock(scarce)).isEqualTo(1);
    }

    @Test
    void orderingAnUnknownProductReturns404() throws Exception {
        placeOrder(registerAndLogin(), Map.of(999_999L, 1))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidOrderReturnsFieldErrors() throws Exception {
        String userToken = registerAndLogin();

        mvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());

        mvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists());
    }

    @Test
    void usersOnlySeeTheirOwnOrdersButAdminsSeeAll() throws Exception {
        long productId = createProduct("Notebook", "3.50", 10);
        String alice = registerAndLogin();
        String bob = registerAndLogin();

        long orderId = readJson(placeOrder(alice, Map.of(productId, 1)).andExpect(status().isCreated()))
                .get("id").asLong();

        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId));
        mvc.perform(get("/api/orders").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ordersRequireLogin() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    /**
     * The classic overselling bug: 10 customers try to buy the last 5 items at the same moment.
     * Because stock is checked and decremented in one atomic UPDATE, exactly 5 succeed.
     */
    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        long productId = createProduct("Limited Edition", "99.00", 5);
        int customers = 10;

        ExecutorService pool = Executors.newFixedThreadPool(customers);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            long userId = 10_000L + i;
            Callable<Boolean> buyOne = () -> {
                startTogether.await();
                try {
                    orderService.placeOrder(userId, new PlaceOrderRequest(List.of(new OrderItemRequest(productId, 1))));
                    return true;
                } catch (ApiException outOfStock) {
                    return false;
                }
            };
            results.add(pool.submit(buyOne));
        }

        startTogether.countDown();   // release all threads at once
        int successful = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                successful++;
            }
        }
        pool.shutdown();

        assertThat(successful).isEqualTo(5);
        assertThat(availableStock(productId)).isZero();
    }

    // ---------- helpers ----------

    private long createCategory(String name) throws Exception {
        return readJson(mvc.perform(post("/api/categories").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private long createProduct(String name, String price, int stock) throws Exception {
        return readJson(mvc.perform(post("/api/products").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, stock)))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private String productJson(String name, String price, int stock) throws Exception {
        return json.writeValueAsString(Map.of(
                "sku", "T-" + UUID.randomUUID().toString().substring(0, 8),
                "name", name,
                "description", "test product",
                "price", price,
                "categoryId", categoryId,
                "initialStock", stock));
    }

    private int availableStock(long productId) throws Exception {
        return readJson(mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isOk()))
                .get("available").asInt();
    }

    private ResultActions placeOrder(String token, Map<Long, Integer> quantities) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        quantities.forEach((productId, quantity) -> items.add(Map.of("productId", productId, "quantity", quantity)));
        return mvc.perform(post("/api/orders").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("items", items))));
    }

    private String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", "Password123", "fullName", "Test User"))))
                .andExpect(status().isCreated());
        return login(email, "Password123");
    }

    private String login(String email, String password) throws Exception {
        return readJson(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
