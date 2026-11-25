package com.miayang.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** Orders. Owns the orders_db database. Talks to product-service through OpenFeign. */
@SpringBootApplication(scanBasePackages = {"com.miayang.ecommerce.order", "com.miayang.ecommerce.common"})
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
