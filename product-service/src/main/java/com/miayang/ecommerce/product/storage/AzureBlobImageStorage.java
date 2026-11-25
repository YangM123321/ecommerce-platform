package com.miayang.ecommerce.product.storage;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.PublicAccessType;
import com.azure.storage.blob.options.BlobContainerCreateOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Product images in Azure Blob Storage. Locally the same code talks to Azurite,
 * Microsoft's Blob Storage emulator, which runs in Docker Compose.
 *
 * The container allows anonymous READ of individual blobs ("blob" access level), so a browser
 * can load an image straight from Blob Storage by URL - the image bytes never pass through
 * our services. Writing still needs the account key, which only product-service has.
 */
public class AzureBlobImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobImageStorage.class);

    private final BlobContainerClient container;
    private final String publicBaseUrl;
    private volatile boolean containerReady;

    public AzureBlobImageStorage(StorageProperties properties) {
        this.container = new BlobServiceClientBuilder()
                .connectionString(properties.connectionString())
                .buildClient()
                .getBlobContainerClient(properties.container());
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base == null || base.isBlank() ? null : base.replaceAll("/+$", "");
    }

    @Override
    public String store(String path, byte[] content, String contentType) {
        ensureContainer();
        BlobClient blob = container.getBlobClient(path);
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType)
                .setCacheControl("public, max-age=86400");   // browsers may cache images for a day
        blob.uploadWithResponse(
                new BlobParallelUploadOptions(BinaryData.fromBytes(content)).setHeaders(headers),
                null, Context.NONE);
        log.info("Stored image {} ({} bytes)", path, content.length);
        return publicBaseUrl == null ? blob.getBlobUrl() : publicBaseUrl + "/" + path;
    }

    /** Created on first upload rather than at startup, so a storage outage can't stop the service starting. */
    private void ensureContainer() {
        if (!containerReady) {
            container.createIfNotExistsWithResponse(
                    new BlobContainerCreateOptions().setPublicAccessType(PublicAccessType.BLOB),
                    null, Context.NONE);
            containerReady = true;
        }
    }
}