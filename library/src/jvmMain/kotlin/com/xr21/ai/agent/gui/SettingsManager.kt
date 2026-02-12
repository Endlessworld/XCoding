package com.xr21.ai.agent.gui

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File

/**
 * 模型设置数据类
 */
data class ModelSettings(
    val modelName: String = "",
    val baseUrl: String = "",
    val apiKey: String = ""
)

/**
 * 应用设置数据类
 */
data class AppSettings(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val homeSendMessageBehavior: String = HomeSendMessageBehavior.REFRESH_LIST.name,
    val modelSettings: ModelSettings = ModelSettings()
)

/**
 * 系统设置管理器
 * 所有系统设置项保存在 System.getProperty("user.home") + File.separator + ".agi_working" + File.separator 目录下
 */
object SettingsManager {
    private val settingsDir: File = File(System.getProperty("user.home") + File.separator + ".agi_working" + File.separator)
    private val settingsFile: File = File(settingsDir, "settings.json")

    private val objectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .findAndRegisterModules()

    private var cachedSettings: AppSettings? = null

    init {
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }
    }

    /**
     * 加载设置
     */
    fun loadSettings(): AppSettings {
        cachedSettings?.let { return it }

        return try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                if (content.isNotBlank()) {
                    cachedSettings = objectMapper.readValue(content, AppSettings::class.java)
                    cachedSettings!!
                } else {
                    AppSettings()
                }
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    /**
     * 保存设置
     */
    fun saveSettings(settings: AppSettings) {
        try {
            val content = objectMapper.writeValueAsString(settings)
            settingsFile.writeText(content)
            cachedSettings = settings
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新模型设置
     */
    fun updateModelSettings(modelSettings: ModelSettings) {
        val settings = loadSettings()
        saveSettings(settings.copy(modelSettings = modelSettings))
    }

    /**
     * 更新主题设置
     */
    fun updateThemeMode(themeMode: ThemeMode) {
        val settings = loadSettings()
        saveSettings(settings.copy(themeMode = themeMode.name))
    }

    /**
     * 更新首页发送消息行为
     */
    fun updateHomeSendMessageBehavior(behavior: HomeSendMessageBehavior) {
        val settings = loadSettings()
        saveSettings(settings.copy(homeSendMessageBehavior = behavior.name))
    }

    /**
     * 获取模型设置
     */
    fun getModelSettings(): ModelSettings {
        return loadSettings().modelSettings
    }

    /**
     * 获取主题设置
     */
    fun getThemeMode(): ThemeMode {
        return try {
            ThemeMode.valueOf(loadSettings().themeMode)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    /**
     * 获取首页发送消息行为
     */
    fun getHomeSendMessageBehavior(): HomeSendMessageBehavior {
        return try {
            HomeSendMessageBehavior.valueOf(loadSettings().homeSendMessageBehavior)
        } catch (e: Exception) {
            HomeSendMessageBehavior.REFRESH_LIST
        }
    }

    /**
     * 清除缓存，重新从文件加载
     */
    fun clearCache() {
        cachedSettings = null
    }
}
