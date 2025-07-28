package io.qonversion.nocodes.internal.screen.service

import android.content.Context
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.logger.Logger

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class FallbackServiceImpl(
    private val context: Context,
    private val fallbackFileName: String,
    private val mapper: Mapper<NoCodeScreen?>,
    logger: Logger
) : FallbackService, BaseClass(logger) {

    // Lazy loaded and cached fallback data
    private val fallbackData by lazy {
        try {
            logger.verbose("FallbackServiceImpl -> Loading fallback data from file: $fallbackFileName")
            val jsonString = loadFallbackFile()
            val jsonObject = JSONObject(jsonString)
            val screensObject = jsonObject.getJSONObject("screens")

            val screens = mutableMapOf<String, NoCodeScreen>()
            val contextKeyToScreen = mutableMapOf<String, NoCodeScreen>()

            val keys = screensObject.keys()
            while (keys.hasNext()) {
                try {
                    val screenKey = keys.next()
                    val screenObject = screensObject.getJSONObject(screenKey)
                    val mappedScreen = mapper.fromMap(screenObject.toMap())

                    mappedScreen?.let { screen ->
                        // Store by screen Id
                        screens[screen.id] = screen
                        // Store by context key
                        screen.contextKey?.let { contextKey ->
                            contextKeyToScreen[contextKey] = screen
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("FallbackServiceImpl -> Skipping invalid screen object: ${e.message}")
                    continue
                }
            }

            logger.info("FallbackServiceImpl -> Successfully loaded ${screens.size} fallback screens")
            FallbackData(screens, contextKeyToScreen)
        } catch (e: Exception) {
            logger.error("FallbackServiceImpl -> Failed to load fallback data: ${e.message}")
            FallbackData(emptyMap(), emptyMap())
        }
    }

    override suspend fun loadScreen(contextKey: String): NoCodeScreen? {
        logger.verbose("loadScreen() -> Looking for fallback screen with context key: $contextKey")

        val screen = fallbackData.contextKeyToScreen[contextKey]
        if (screen != null) {
            logger.info("loadScreen() -> Found fallback screen for context key: $contextKey")
        } else {
            logger.warn("loadScreen() -> No fallback screen found for context key: $contextKey")
        }

        return screen
    }

    override suspend fun loadScreenById(screenId: String): NoCodeScreen? {
        logger.verbose("loadScreenById() -> Looking for fallback screen with Id: $screenId")

        val screen = fallbackData.screens[screenId]
        if (screen != null) {
            logger.info("loadScreenById() -> Found fallback screen for Id: $screenId")
        } else {
            logger.warn("loadScreenById() -> No fallback screen found for Id: $screenId")
        }

        return screen
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

    private data class FallbackData(
        val screens: Map<String, NoCodeScreen>,
        val contextKeyToScreen: Map<String, NoCodeScreen>
    )

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
