package com.miayang.ecommerce.product.storage;

import com.miayang.ecommerce.common.web.ApiException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Without a connection string the service still runs (browsing and orders work),
     * and only image uploads answer 503.
     */
    @Bean
    ImageStorage imageStorage(StorageProperties properties) {
        if (properties.configured()) {
            return new AzureBlobImageStorage(properties);
        }
        return (path, content, contentType) -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        };
    }
}
