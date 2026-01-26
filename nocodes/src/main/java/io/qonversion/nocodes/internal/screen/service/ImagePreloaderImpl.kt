package io.qonversion.nocodes.internal.screen.service

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Implementation of ImagePreloader that downloads images and converts them to base64 data URIs.
 */
internal class ImagePreloaderImpl(
    private val maxConcurrentDownloads: Int = 5,
    private val connectionTimeout: Int = 10000,
    private val readTimeout: Int = 10000
) : ImagePreloader {

    companion object {
        // Pattern for <img src="..."> or <img src='...'>
        private val IMG_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )

        // Pattern for background-image: url(...) with optional quotes
        private val BG_PATTERN = Pattern.compile(
            "background-image\\s*:\\s*url\\s*\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)",
            Pattern.CASE_INSENSITIVE
        )
    }

    override suspend fun preloadImages(html: String): String {
        val imageUrls = extractImageUrls(html)

        if (imageUrls.isEmpty()) {
            return html
        }

        val replacements = downloadImages(imageUrls)

        return replaceUrls(html, replacements)
    }

    /**
     * Extracts image URLs from HTML content.
     * Handles both <img src="..."> tags and background-image: url(...) CSS.
     */
    private fun extractImageUrls(html: String): Set<String> {
        val urls = mutableSetOf<String>()

        // Extract from img tags
        val imgMatcher = IMG_PATTERN.matcher(html)
        while (imgMatcher.find()) {
            imgMatcher.group(1)?.let { urls.add(it) }
        }

        // Extract from background-image
        val bgMatcher = BG_PATTERN.matcher(html)
        while (bgMatcher.find()) {
            bgMatcher.group(1)?.let { urls.add(it) }
        }

        // Filter only valid HTTP(S) URLs (skip data URIs, relative paths, etc.)
        return urls.filter { url ->
            url.startsWith("http://") || url.startsWith("https://")
        }.toSet()
    }

    /**
     * Downloads images and converts them to base64 data URIs.
     */
    private suspend fun downloadImages(urls: Set<String>): Map<String, String> = coroutineScope {
        val urlList = urls.toList()
        val results = mutableMapOf<String, String>()

        // Process in batches to limit concurrent downloads
        urlList.chunked(maxConcurrentDownloads).forEach { batch ->
            val deferredResults = batch.map { url ->
                async {
                    downloadAndConvert(url)
                }
            }

            deferredResults.awaitAll().forEach { (url, dataUri) ->
                if (dataUri != null) {
                    results[url] = dataUri
                }
            }
        }

        results
    }

    /**
     * Downloads a single image and converts it to base64 data URI.
     */
    private suspend fun downloadAndConvert(urlString: String): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = connectionTimeout
            connection.readTimeout = readTimeout
            connection.requestMethod = "GET"

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return@withContext urlString to null
                }

                val contentType = connection.contentType ?: detectMimeType(urlString)
                val data = connection.inputStream.use { it.readBytes() }
                val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
                val dataUri = "data:$contentType;base64,$base64"

                urlString to dataUri
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            urlString to null
        }
    }

    /**
     * Detects MIME type from file extension.
     */
    private fun detectMimeType(url: String): String {
        val ext = url.substringAfterLast('.').lowercase().substringBefore('?')
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }

    /**
     * Replaces image URLs in HTML with base64 data URIs.
     */
    private fun replaceUrls(html: String, replacements: Map<String, String>): String {
        var result = html
        for ((originalUrl, dataUri) in replacements) {
            result = result.replace(originalUrl, dataUri)
        }
        return result
    }
}
