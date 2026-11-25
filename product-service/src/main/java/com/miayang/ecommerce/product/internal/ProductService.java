package com.miayang.ecommerce.product.internal;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.miayang.ecommerce.product.internal.ProductDtos.CategoryResponse;
import com.miayang.ecommerce.product.internal.ProductDtos.CreateCategoryRequest;
import com.miayang.ecommerce.product.internal.ProductDtos.CreateProductRequest;
import com.miayang.ecommerce.product.internal.ProductDtos.ProductResponse;
import com.miayang.ecommerce.product.internal.ProductDtos.UpdateProductRequest;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.common.web.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog management: browse, create, update, deactivate products and add stock. */
@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryRepository inventory;

    public ProductService(ProductRepository products, CategoryRepository categories, InventoryRepository inventory) {
        this.products = products;
        this.categories = categories;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(Long categoryId, String text, Pageable pageable) {
        Specification<Product> filter = ProductSpecifications.isActive();
        if (categoryId != null) {
            filter = filter.and(ProductSpecifications.inCategory(categoryId));
        }
        if (text != null && !text.isBlank()) {
            filter = filter.and(ProductSpecifications.nameContains(text.trim()));
        }

        Page<Product> page = products.findAll(filter, pageable);

        // One query for all stock on this page, instead of one query per product (the "N+1" problem).
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> stock = inventory.findAllById(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getAvailable));

        return PageResponse.from(page.map(p -> ProductResponse.from(p, stock.getOrDefault(p.getId(), 0))));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Product product = products.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> productNotFound(id));
        return ProductResponse.from(product, availableStock(id));
    }

    public ProductResponse create(CreateProductRequest request) {
        if (products.existsBySkuIgnoreCase(request.sku())) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU " + request.sku() + " already exists");
        }
        Category category = findCategory(request.categoryId());

        Product product = products.save(new Product(
                request.sku().trim(), request.name().trim(), request.description(), request.price(), category));
        inventory.save(new Inventory(product.getId(), request.initialStock()));
        return ProductResponse.from(product, request.initialStock());
    }

    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = products.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> productNotFound(id));
        product.update(request.name().trim(), request.description(), request.price(), findCategory(request.categoryId()));
        return ProductResponse.from(product, availableStock(id));
    }

    public void deactivate(Long id) {
        Product product = products.findById(id).orElseThrow(() -> productNotFound(id));
        product.deactivate();
    }

    public ProductResponse addStock(Long id, int quantity) {
        if (inventory.addStock(id, quantity) == 0) {
            throw productNotFound(id);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Category " + name + " already exists");
        }
        return CategoryResponse.from(categories.save(new Category(name)));
    }

    private int availableStock(Long productId) {
        return inventory.findById(productId).map(Inventory::getAvailable).orElse(0);
    }

    private Category findCategory(Long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Category " + categoryId + " does not exist"));
    }

    private static ApiException productNotFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Product " + id + " not found");
    }
}
