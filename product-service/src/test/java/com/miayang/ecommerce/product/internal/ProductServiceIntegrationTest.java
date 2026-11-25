package com.miayang.ecommerce.product.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.miayang.ecommerce.common.security.JwtProperties;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.product.TestTokens;
import com.miayang.ecommerce.product.TestcontainersConfig;
import com.miayang.ecommerce.product.internal.StockDtos.StockLine;
import com.miayang.ecommerce.product.storage.ImageStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** product-service on its own: real Postgres (Testcontainers), no Eureka, no config server. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ProductServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    StockService stockService;

    // Blob Storage is replaced by a mock: these tests check OUR rules, not Azure.
    @MockitoBean
    ImageStorage imageStorage;

    String admin;
    String user;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestTokens.bearer(jwtProperties, 1L, "ADMIN", "USER");
        user = TestTokens.bearer(jwtProperties, 2L, "USER");
        categoryId = readJson(mvc.perform(post("/api/categories").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Test category " + UUID.randomUUID()))))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    // ---------- public catalog ----------

    @Test
    void browsingIsPublicPagedAndFilterable() throws Exception {
        mvc.perform(get("/api/products").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        long productId = createProduct("Filter Lamp", "19.99", 4);
        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].available").value(4));
    }

    @Test
    void onlyAdminsCanChangeTheCatalog() throws Exception {
        String body = productJson("Nope", "9.99", 1);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ---------- internal stock API (used by order-service) ----------

    @Test
    void internalEndpointsNeedAToken() throws Exception {
        mvc.perform(post("/internal/products/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookupReturnsOnlyActiveProducts() throws Exception {
        long active = createProduct("Active", "5.00", 1);
        long deleted = createProduct("Deleted", "5.00", 1);
        mvc.perform(delete("/api/products/{id}", deleted).header("Authorization", admin))
                .andExpect(status().isNoContent());

        mvc.perform(post("/internal/products/lookup").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("productIds", List.of(active, deleted)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(active))
                .andExpect(jsonPath("$[0].price").value(5.0));
    }

    @Test
    void reservationIsAllOrNothingAndReleaseGivesStockBack() throws Exception {
        long plenty = createProduct("Plenty", "5.00", 5);
        long scarce = createProduct("Scarce", "5.00", 1);

        // One line can't be reserved -> 409 and NOTHING is reserved.
        stock("reservations", Map.of(plenty, 2, scarce, 3)).andExpect(status().isConflict());
        assertThat(availableStock(plenty)).isEqualTo(5);

        stock("reservations", Map.of(plenty, 2, scarce, 1)).andExpect(status().isNoContent());
        assertThat(availableStock(plenty)).isEqualTo(3);
        assertThat(availableStock(scarce)).isZero();

        stock("releases", Map.of(plenty, 2, scarce, 1)).andExpect(status().isNoContent());
        assertThat(availableStock(plenty)).isEqualTo(5);
        assertThat(availableStock(scarce)).isEqualTo(1);
    }

    /** 10 buyers, 5 items, all at once: exactly 5 reservations succeed and stock never goes negative. */
    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long productId = createProduct("Limited Edition", "99.00", 5);
        int buyers = 10;

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            Callable<Boolean> reserveOne = () -> {
                startTogether.await();
                try {
                    stockService.reserveAll(List.of(new StockLine(productId, 1)));
                    return true;
                } catch (ApiException outOfStock) {
                    return false;
                }
            };
            results.add(pool.submit(reserveOne));
        }
        startTogether.countDown();

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

    // ---------- product images (Session 5) ----------

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Test
    void adminUploadsAnImageAndTheProductShowsItsUrl() throws Exception {
        long productId = createProduct("Photo Lamp", "29.99", 2);
        when(imageStorage.store(startsWith("products/" + productId + "/"), any(), eq("image/png")))
                .thenReturn("https://example.blob.core.windows.net/product-images/lamp.png");

        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "lamp.png", "image/png", PNG))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://example.blob.core.windows.net/product-images/lamp.png"));

        mvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.imageUrl").value("https://example.blob.core.windows.net/product-images/lamp.png"));
    }

    @Test
    void normalUsersCannotUploadImages() throws Exception {
        long productId = createProduct("No Photo Lamp", "29.99", 2);

        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "lamp.png", "image/png", PNG))
                        .header("Authorization", user))
                .andExpect(status().isForbidden());
        verify(imageStorage, never()).store(any(), any(), any());
    }

    @Test
    void aFileThatIsNotReallyAnImageIsRejected() throws Exception {
        long productId = createProduct("Fake Photo Lamp", "29.99", 2);
        byte[] notAnImage = "MZ this is really a program".getBytes();

        // Named .png and labelled image/png - but the bytes say otherwise.
        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "cat.png", "image/png", notAnImage))
                        .header("Authorization", admin))
                .andExpect(status().isUnsupportedMediaType());
        verify(imageStorage, never()).store(any(), any(), any());
    }

    @Test
    void imageForAnUnknownProductIs404() throws Exception {
        mvc.perform(multipart("/api/products/999999/image")
                        .file(new MockMultipartFile("file", "lamp.png", "image/png", PNG))
                        .header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    private long createProduct(String name, String price, int stock) throws Exception {
        return readJson(mvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, stock)))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private String productJson(String name, String price, int stock) throws Exception {
        return json.writeValueAsString(Map.of(
                "sku", "T-" + UUID.randomUUID().toString().substring(0, 8),
                "name", name,
                "price", price,
                "categoryId", categoryId,
                "initialStock", stock));
    }

    private ResultActions stock(String action, Map<Long, Integer> quantities) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        quantities.forEach((id, qty) -> items.add(Map.of("productId", id, "quantity", qty)));
        return mvc.perform(post("/internal/stock/" + action).header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("items", items))));
    }

    private int availableStock(long productId) throws Exception {
        return readJson(mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isOk()))
                .get("available").asInt();
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
