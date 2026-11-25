package com.miayang.ecommerce.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Users, login, JWT issuing and roles. Owns the users_db database.
 * scanBasePackages also picks up the shared code in the common library.
 */
@SpringBootApplication(scanBasePackages = {"com.miayang.ecommerce.user", "com.miayang.ecommerce.common"})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
