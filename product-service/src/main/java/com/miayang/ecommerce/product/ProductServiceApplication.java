package com.miayang.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Catalog and stock. Owns the catalog_db database. */
@SpringBootApplication(scanBasePackages = {"com.miayang.ecommerce.product", "com.miayang.ecommerce.common"})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
