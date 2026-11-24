package com.miayang.ecommerce.shared.web;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny endpoint to prove the app is up and reachable through Docker.
 * GET /api/info
 */
@RestController
@RequestMapping("/api")
public class InfoController {

    private final String appName;

    public InfoController(@Value("${spring.application.name}") String appName) {
        this.appName = appName;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {

        return Map.of(
                "app", appName,
                "version", "0.1.0",
                "stage", "monolith",
                "modules", new String[] {"user", "product", "order"});

    }
}
