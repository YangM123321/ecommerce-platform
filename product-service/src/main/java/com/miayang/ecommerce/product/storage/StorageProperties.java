package com.miayang.ecommerce.product.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.storage.* (see config-repo/product-service.yml).
 *
 * @param connectionString Azure Storage connection string - a SECRET, comes from an env variable
 * @param container        blob container for product images
 * @param publicBaseUrl    optional: base of the URL handed to browsers. Needed locally because
 *                         the app reaches Azurite as "azurite:10000" but your browser uses
 *                         "localhost:10000". Empty on Azure: the real blob URL is already public.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String connectionString, String container, String publicBaseUrl) {

    public boolean configured() {
        return connectionString != null && !connectionString.isBlank();
    }
}
