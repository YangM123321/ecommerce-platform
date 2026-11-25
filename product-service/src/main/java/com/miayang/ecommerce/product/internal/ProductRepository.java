package com.miayang.ecommerce.product.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** JpaSpecificationExecutor lets ProductService combine optional filters (category, search text). */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);

    List<Product> findByIdInAndActiveTrue(Collection<Long> ids);
}
