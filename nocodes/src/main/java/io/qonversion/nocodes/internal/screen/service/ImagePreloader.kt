package io.qonversion.nocodes.internal.screen.service

/**
 * Interface for preloading images in HTML content.
 * Extracts image URLs, downloads them, and replaces with base64 data URIs.
 */
internal interface ImagePreloader {

    /**
     * Preloads images in HTML content by replacing image URLs with base64 data URIs.
     *
     * @param html The HTML content containing image URLs
     * @return Modified HTML with image URLs replaced by base64 data URIs
     */
    suspend fun preloadImages(html: String): String
}
