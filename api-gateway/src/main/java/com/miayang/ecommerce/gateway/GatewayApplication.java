package com.miayang.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single front door (port 8080). Clients only ever talk to the gateway; it checks the JWT
 * and forwards each request to a healthy instance of the right service, found through Eureka.
 *
 * Spring Cloud Gateway is reactive (WebFlux/Netty), which is why it does NOT use the common
 * module: common brings Spring MVC, and MVC and WebFlux can't run in the same app.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
