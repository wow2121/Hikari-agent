package com.xiaoguang.assistant.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.xiaoguang.assistant.domain.model.MonitoringMode
import com.xiaoguang.assistant.domain.model.RecognitionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore 扩展
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    // Preference Keys
    private object Keys {
        val MONITORING_MODE = stringPreferencesKey("monitoring_mode")
        val RECOGNITION_METHOD = stringPreferencesKey("recognition_method")
        val AUTO_CREATE_CALENDAR = booleanPreferencesKey("auto_create_calendar")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val SHOW_TASK_NOTIFICATIONS = booleanPreferencesKey("show_task_notifications")
        val SHOW_EXTRACTION_NOTIFICATIONS = booleanPreferencesKey("show_extraction_notifications")
        val REMINDER_BEFORE_HOURS = intPreferencesKey("reminder_before_hours")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")
        val LISTENING_ENABLED = booleanPreferencesKey("listening_enabled")

        // Embedding和记忆配置
        val EMBEDDING_DIMENSION = intPreferencesKey("embedding_dimension")
        val EMBEDDING_MODEL = stringPreferencesKey("embedding_model")
        val USE_LOCAL_EMBEDDING = booleanPreferencesKey("use_local_embedding")
        val MEMORY_RETENTION_DAYS = intPreferencesKey("memory_retention_days")
        val AUTO_GENERATE_EMBEDDINGS = booleanPreferencesKey("auto_generate_embeddings")

        // 定时监听配置（简化存储，使用JSON字符串）
        val SCHEDULE_CONFIG = stringPreferencesKey("schedule_config")

        // 唤醒词配置
        val PORCUPINE_ACCESS_KEY = stringPreferencesKey("porcupine_access_key")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")

        // 主动插话配置
        val INTERRUPTION_MODE = stringPreferencesKey("interruption_mode")
    }

    // ========== 监听模式 ==========

    val monitoringMode: Flow<MonitoringMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.MONITORING_MODE] ?: MonitoringMode.FOREGROUND_ONLY.name
        try {
            MonitoringMode.valueOf(modeString)
        } catch (e: Exception) {
            MonitoringMode.FOREGROUND_ONLY
        }
    }

    suspend fun setMonitoringMode(mode: MonitoringMode) {
        dataStore.edit { preferences ->
            preferences[Keys.MONITORING_MODE] = mode.name
        }
    }

    // ========== 语音识别方案 ==========

    val recognitionMethod: Flow<RecognitionMethod> = dataStore.data.map { preferences ->
        val methodString = preferences[Keys.RECOGNITION_METHOD] ?: RecognitionMethod.HYBRID.name
        try {
            RecognitionMethod.valueOf(methodString)
        } catch (e: Exception) {
            RecognitionMethod.HYBRID
        }
    }

    suspend fun setRecognitionMethod(method: RecognitionMethod) {
        dataStore.edit { preferences ->
            preferences[Keys.RECOGNITION_METHOD] = method.name
        }
    }

    // ========== 自动创建日历 ==========

    val autoCreateCalendar: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_CREATE_CALENDAR] ?: false
    }

    suspend fun setAutoCreateCalendar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_CREATE_CALENDAR] = enabled
        }
    }

    // ========== 通知设置 ==========

    val notificationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.NOTIFICATION_ENABLED] ?: true
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATION_ENABLED] = enabled
        }
    }

    val showTaskNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SHOW_TASK_NOTIFICATIONS] ?: true
    }

    suspend fun setShowTaskNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_TASK_NOTIFICATIONS] = enabled
        }
    }

    val showExtractionNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SHOW_EXTRACTION_NOTIFICATIONS] ?: true
    }

    suspend fun setShowExtractionNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_EXTRACTION_NOTIFICATIONS] = enabled
        }
    }

    val reminderBeforeHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.REMINDER_BEFORE_HOURS] ?: 24 // 默认提前24小时提醒
    }

    suspend fun setReminderBeforeHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.REMINDER_BEFORE_HOURS] = hours
        }
    }

    // ========== 信息提取设置 ==========

    val confidenceThreshold: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.CONFIDENCE_THRESHOLD] ?: 0.6f // 默认置信度阈值60%
    }

    suspend fun setConfidenceThreshold(threshold: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.CONFIDENCE_THRESHOLD] = threshold
        }
    }

    // ========== 数据保留 ==========

    val dataRetentionDays: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.DATA_RETENTION_DAYS] ?: 90 // 默认保留90天
    }

    suspend fun setDataRetentionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.DATA_RETENTION_DAYS] = days
        }
    }

    // ========== 监听开关 ==========

    val listeningEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LISTENING_ENABLED] ?: false
    }

    suspend fun setListeningEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LISTENING_ENABLED] = enabled
        }
    }

    // ========== Embedding和记忆设置 ==========

    val embeddingDimension: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.EMBEDDING_DIMENSION] ?: 1024 // 默认1024维(平衡性能和效果)
    }

    suspend fun setEmbeddingDimension(dimension: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.EMBEDDING_DIMENSION] = dimension
        }
    }

    val embeddingModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.EMBEDDING_MODEL] ?: "Qwen/Qwen3-Embedding-0.6B"
    }

    suspend fun setEmbeddingModel(model: String) {
        dataStore.edit { preferences ->
            preferences[Keys.EMBEDDING_MODEL] = model
        }
    }

    val useLocalEmbedding: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.USE_LOCAL_EMBEDDING] ?: false
    }

    suspend fun setUseLocalEmbedding(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.USE_LOCAL_EMBEDDING] = enabled
        }
    }

    val memoryRetentionDays: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.MEMORY_RETENTION_DAYS] ?: 365 // 默认保留1年记忆
    }

    suspend fun setMemoryRetentionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.MEMORY_RETENTION_DAYS] = days
        }
    }

    val autoGenerateEmbeddings: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_GENERATE_EMBEDDINGS] ?: true
    }

    suspend fun setAutoGenerateEmbeddings(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_GENERATE_EMBEDDINGS] = enabled
        }
    }

    // ========== 定时配置 (TODO: 实现完整的Schedule配置) ==========

    val scheduleConfig: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.SCHEDULE_CONFIG]
    }

    suspend fun setScheduleConfig(jsonConfig: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SCHEDULE_CONFIG] = jsonConfig
        }
    }

    // ========== 唤醒词设置 ==========

    val porcupineAccessKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PORCUPINE_ACCESS_KEY] ?: ""
    }

    suspend fun setPorcupineAccessKey(accessKey: String) {
        dataStore.edit { preferences ->
            preferences[Keys.PORCUPINE_ACCESS_KEY] = accessKey
        }
    }

    val wakeWordEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.WAKE_WORD_ENABLED] ?: false
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    val wakeWordSensitivity: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.WAKE_WORD_SENSITIVITY] ?: 0.5f  // 默认中等灵敏度
    }

    suspend fun setWakeWordSensitivity(sensitivity: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.WAKE_WORD_SENSITIVITY] = sensitivity
        }
    }

    // ========== 主动插话设置 ==========

    val interruptionMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.INTERRUPTION_MODE] ?: "need_confirmation"  // 默认需要确认
    }

    suspend fun setInterruptionMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.INTERRUPTION_MODE] = mode
        }
    }

    // ========== 清除所有数据 ==========

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
