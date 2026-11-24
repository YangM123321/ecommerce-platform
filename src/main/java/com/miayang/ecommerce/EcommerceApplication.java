package com.miayang.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the e-commerce modular monolith.
 *
 * Modules live in their own top-level packages (user, product, order).
 * Each module exposes only its "api" package to the others; everything in
 * "internal" is private to the module. ModuleBoundariesTest enforces this,
 * which is what makes the Session 4 split into microservices straightforward.
 */
@SpringBootApplication
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
