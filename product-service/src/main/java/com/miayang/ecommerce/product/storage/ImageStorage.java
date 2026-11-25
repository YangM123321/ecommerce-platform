package com.miayang.ecommerce.product.storage;

/**
 * Where product images are kept. The product code only knows this interface:
 * AzureBlobImageStorage on Azure and with Azurite locally; a mock in tests.
 */
public interface ImageStorage {

    /**
     * Saves the bytes under the given path and returns the public URL a browser can load.
     */
    String store(String path, byte[] content, String contentType);
}
