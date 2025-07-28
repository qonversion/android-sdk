package io.qonversion.nocodes.internal.screen.service

import android.content.Context
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class FallbackServiceImpl(
    private val context: Context,
    private val fallbackFileName: String,
    private val mapper: Mapper<NoCodeScreen?>,
    logger: Logger
) : FallbackService, BaseClass(logger) {

    override suspend fun loadScreen(contextKey: String): NoCodeScreen? = withContext(Dispatchers.IO) {
        try {
            logger.verbose("loadScreen() -> Loading fallback screen for context key: $contextKey")
            
            val jsonString = loadFallbackFile()
            val jsonObject = JSONObject(jsonString)
            val screensObject = jsonObject.getJSONObject("screens")
            
            // Итерируемся по ключам объекта screens
            val keys = screensObject.keys()
            while (keys.hasNext()) {
                try {
                    val screenKey = keys.next()
                    val screenObject = screensObject.getJSONObject(screenKey)
                    val mappedScreen = mapper.fromMap(screenObject.toMap())
                    
                    if (mappedScreen?.contextKey == contextKey) {
                        logger.info("loadScreen() -> Found fallback screen for context key: $contextKey")
                        return@withContext mappedScreen
                    }
                } catch (e: Exception) {
                    logger.warn("loadScreen() -> Skipping invalid screen object with context key $contextKey: ${e.message}")
                    continue
                }
            }
            
            logger.warn("loadScreen() -> No fallback screen found for context key: $contextKey")
            null
        } catch (e: Exception) {
            logger.error("loadScreen() -> Failed to load fallback screen: ${e.message}")
            null
        }
    }

    override suspend fun loadScreenById(screenId: String): NoCodeScreen? = withContext(Dispatchers.IO) {
        try {
            logger.verbose("loadScreenById() -> Loading fallback screen for screen ID: $screenId")
            
            val jsonString = loadFallbackFile()
            val jsonObject = JSONObject(jsonString)
            val screensObject = jsonObject.getJSONObject("screens")
            
            // Итерируемся по ключам объекта screens
            val keys = screensObject.keys()
            while (keys.hasNext()) {
                try {
                    val screenKey = keys.next()
                    val screenObject = screensObject.getJSONObject(screenKey)
                    val mappedScreen = mapper.fromMap(screenObject.toMap())
                    
                    if (mappedScreen?.id == screenId) {
                        logger.info("loadScreenById() -> Found fallback screen for screen ID: $screenId")
                        return@withContext mappedScreen
                    }
                } catch (e: Exception) {
                    logger.warn("loadScreenById() -> Skipping invalid screen object with screen Id $screenId: ${e.message}")
                    continue
                }
            }
            
            logger.warn("loadScreenById() -> No fallback screen found for screen ID: $screenId")
            null
        } catch (e: Exception) {
            logger.error("loadScreenById() -> Failed to load fallback screen: ${e.message}")
            null
        }
    }

    private fun loadFallbackFile(): String {
        return try {
            context.assets.open(fallbackFileName).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to load fallback file: $fallbackFileName", e)
        }
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = this.get(key)
            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until this.length()) {
            val value = this.get(i)
            list.add(when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                JSONObject.NULL -> null
                else -> value
            })
        }
        return list
    }
} 