package com.xiaoguang.assistant.domain.memory.sensory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 感官记忆缓冲层
 *
 * 功能：
 * 1. 1-5秒超短期缓冲区 - 模拟人类感官记忆的瞬时存储
 * 2. 环境感知集成 - 整合视觉、听觉、触觉等环境信息
 * 3. 自动衰减 - 超过保留时间的记忆自动清除
 * 4. 选择性注意 - 重要的感官信息可以晋升到工作记忆
 *
 * 基于 Baddeley 工作记忆模型中的感官记忆概念
 */
@Singleton
class SensoryMemoryBuffer @Inject constructor(
    private val coroutineScope: CoroutineScope
) {

    private val mutex = Mutex()
    private var config = SensoryConfig()

    // 感官记忆缓冲区（按模态分类）
    private val visualBuffer = ConcurrentLinkedQueue<SensoryMemory>()
    private val auditoryBuffer = ConcurrentLinkedQueue<SensoryMemory>()
    private val tactileBuffer = ConcurrentLinkedQueue<SensoryMemory>()
    private val environmentalBuffer = ConcurrentLinkedQueue<SensoryMemory>()

    // 当前缓冲区状态
    private val _bufferState = MutableStateFlow(
        BufferState(
            visualCount = 0,
            auditoryCount = 0,
            tactileCount = 0,
            environmentalCount = 0,
            totalCapacity = config.maxCapacityPerModality * 4
        )
    )
    val bufferState: StateFlow<BufferState> = _bufferState.asStateFlow()

    // 晋升到工作记忆的回调
    private val _promotionEvents = MutableStateFlow<List<PromotionEvent>>(emptyList())
    val promotionEvents: StateFlow<List<PromotionEvent>> = _promotionEvents.asStateFlow()

    // 统计数据
    private val _stats = MutableStateFlow(
        SensoryStats(
            totalReceived = 0,
            totalExpired = 0,
            totalPromoted = 0,
            averageRetentionTime = 0f,
            lastCleanupTime = null
        )
    )
    val stats: StateFlow<SensoryStats> = _stats.asStateFlow()

    init {
        // 启动自动清理任务
        startAutoCleanup()
    }

    /**
     * 添加感官记忆
     *
     * @param sensoryInput 感官输入数据
     * @return 是否成功添加
     */
    suspend fun addSensoryMemory(sensoryInput: SensoryInput): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                mutex.withLock {
                    val memory = SensoryMemory(
                        id = generateId(),
                        modality = sensoryInput.modality,
                        content = sensoryInput.content,
                        intensity = sensoryInput.intensity,
                        salience = calculateSalience(sensoryInput),
                        timestamp = LocalDateTime.now(),
                        expiryTime = LocalDateTime.now().plusSeconds(config.retentionSeconds)
                    )

                    // 根据模态添加到对应缓冲区
                    val buffer = getBufferForModality(memory.modality)

                    // 检查容量
                    if (buffer.size >= config.maxCapacityPerModality) {
                        // 移除最旧的记忆
                        buffer.poll()
                    }

                    buffer.offer(memory)

                    // 检查是否需要晋升
                    if (shouldPromote(memory)) {
                        promoteToWorkingMemory(memory)
                    }

                    // 更新状态
                    updateBufferState()
                    updateStats(received = 1)

                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取当前活跃的感官记忆
     *
     * @param modality 可选的模态过滤
     * @return 当前未过期的感官记忆列表
     */
    suspend fun getActiveMemories(modality: SensoryModality? = null): List<SensoryMemory> {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val now = LocalDateTime.now()
                val allMemories = mutableListOf<SensoryMemory>()

                // 收集所有缓冲区的记忆
                allMemories.addAll(visualBuffer.filter { !it.isExpired(now) })
                allMemories.addAll(auditoryBuffer.filter { !it.isExpired(now) })
                allMemories.addAll(tactileBuffer.filter { !it.isExpired(now) })
                allMemories.addAll(environmentalBuffer.filter { !it.isExpired(now) })

                // 根据模态过滤
                if (modality != null) {
                    allMemories.filter { it.modality == modality }
                } else {
                    allMemories
                }.sortedByDescending { it.salience }
            }
        }
    }

    /**
     * 整合环境感知信息
     *
     * @param environmentalData 环境数据
     * @return 整合后的环境摘要
     */
    suspend fun integrateEnvironmentalAwareness(
        environmentalData: EnvironmentalData
    ): Result<EnvironmentalSummary> {
        return withContext(Dispatchers.Default) {
            try {
                // 添加环境信息到缓冲区
                val sensoryInput = SensoryInput(
                    modality = SensoryModality.ENVIRONMENTAL,
                    content = environmentalData.toString(),
                    intensity = calculateEnvironmentalIntensity(environmentalData)
                )
                addSensoryMemory(sensoryInput)

                // 生成环境摘要
                val summary = EnvironmentalSummary(
                    location = environmentalData.location,
                    timeOfDay = environmentalData.timeOfDay,
                    lightLevel = environmentalData.lightLevel,
                    noiseLevel = environmentalData.noiseLevel,
                    temperature = environmentalData.temperature,
                    activeModalitiesCount = getActiveMemories().groupBy { it.modality }.size,
                    dominantModality = getDominantModality(),
                    attentionLevel = calculateAttentionLevel(),
                    timestamp = LocalDateTime.now()
                )

                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 清理过期的感官记忆
     */
    suspend fun cleanup(): Result<Int> {
        return withContext(Dispatchers.Default) {
            try {
                mutex.withLock {
                    val now = LocalDateTime.now()
                    var expiredCount = 0

                    expiredCount += removeExpired(visualBuffer, now)
                    expiredCount += removeExpired(auditoryBuffer, now)
                    expiredCount += removeExpired(tactileBuffer, now)
                    expiredCount += removeExpired(environmentalBuffer, now)

                    updateBufferState()
                    updateStats(expired = expiredCount)

                    Result.success(expiredCount)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 清空所有缓冲区
     */
    suspend fun clear(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                mutex.withLock {
                    visualBuffer.clear()
                    auditoryBuffer.clear()
                    tactileBuffer.clear()
                    environmentalBuffer.clear()

                    updateBufferState()

                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 启动自动清理任务
     */
    private fun startAutoCleanup() {
        coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                delay(config.cleanupIntervalMs)
                cleanup()
            }
        }
    }

    /**
     * 计算显著性
     */
    private fun calculateSalience(input: SensoryInput): Float {
        var salience = input.intensity

        // 根据模态调整
        salience *= when (input.modality) {
            SensoryModality.VISUAL -> 1.2f      // 视觉信息更显著
            SensoryModality.AUDITORY -> 1.0f    // 听觉信息
            SensoryModality.TACTILE -> 0.8f     // 触觉信息
            SensoryModality.ENVIRONMENTAL -> 0.5f // 环境信息较不显著
        }

        return salience.coerceIn(0f, 1f)
    }

    /**
     * 判断是否应该晋升到工作记忆
     */
    private fun shouldPromote(memory: SensoryMemory): Boolean {
        return memory.salience >= config.promotionThreshold
    }

    /**
     * 晋升到工作记忆
     */
    private fun promoteToWorkingMemory(memory: SensoryMemory) {
        val event = PromotionEvent(
            memoryId = memory.id,
            modality = memory.modality,
            content = memory.content,
            salience = memory.salience,
            timestamp = LocalDateTime.now()
        )

        _promotionEvents.value = (_promotionEvents.value + event).takeLast(50)
        updateStats(promoted = 1)
    }

    /**
     * 获取指定模态的缓冲区
     */
    private fun getBufferForModality(modality: SensoryModality): ConcurrentLinkedQueue<SensoryMemory> {
        return when (modality) {
            SensoryModality.VISUAL -> visualBuffer
            SensoryModality.AUDITORY -> auditoryBuffer
            SensoryModality.TACTILE -> tactileBuffer
            SensoryModality.ENVIRONMENTAL -> environmentalBuffer
        }
    }

    /**
     * 移除过期记忆
     */
    private fun removeExpired(
        buffer: ConcurrentLinkedQueue<SensoryMemory>,
        now: LocalDateTime
    ): Int {
        val iterator = buffer.iterator()
        var count = 0

        while (iterator.hasNext()) {
            val memory = iterator.next()
            if (memory.isExpired(now)) {
                iterator.remove()
                count++
            }
        }

        return count
    }

    /**
     * 更新缓冲区状态
     */
    private fun updateBufferState() {
        _bufferState.value = BufferState(
            visualCount = visualBuffer.size,
            auditoryCount = auditoryBuffer.size,
            tactileCount = tactileBuffer.size,
            environmentalCount = environmentalBuffer.size,
            totalCapacity = config.maxCapacityPerModality * 4
        )
    }

    /**
     * 更新统计数据
     */
    private fun updateStats(received: Int = 0, expired: Int = 0, promoted: Int = 0) {
        _stats.value = _stats.value.copy(
            totalReceived = _stats.value.totalReceived + received,
            totalExpired = _stats.value.totalExpired + expired,
            totalPromoted = _stats.value.totalPromoted + promoted,
            lastCleanupTime = if (expired > 0) LocalDateTime.now() else _stats.value.lastCleanupTime
        )
    }

    /**
     * 计算环境强度
     */
    private fun calculateEnvironmentalIntensity(data: EnvironmentalData): Float {
        var intensity = 0.5f

        // 根据噪音水平调整
        data.noiseLevel?.let {
            intensity += (it - 0.5f) * 0.3f
        }

        // 根据光照水平调整
        data.lightLevel?.let {
            if (it < 0.2f || it > 0.8f) {
                intensity += 0.2f  // 极端光照更显著
            }
        }

        return intensity.coerceIn(0f, 1f)
    }

    /**
     * 获取主导模态
     */
    private fun getDominantModality(): SensoryModality? {
        val counts = mapOf(
            SensoryModality.VISUAL to visualBuffer.size,
            SensoryModality.AUDITORY to auditoryBuffer.size,
            SensoryModality.TACTILE to tactileBuffer.size,
            SensoryModality.ENVIRONMENTAL to environmentalBuffer.size
        )

        return counts.maxByOrNull { it.value }?.key
    }

    /**
     * 计算注意力水平
     */
    private fun calculateAttentionLevel(): Float {
        val totalMemories = visualBuffer.size + auditoryBuffer.size +
                           tactileBuffer.size + environmentalBuffer.size

        val highSalienceCount = (visualBuffer + auditoryBuffer + tactileBuffer + environmentalBuffer)
            .count { it.salience > 0.7f }

        return if (totalMemories > 0) {
            (highSalienceCount.toFloat() / totalMemories).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * 生成唯一ID
     */
    private fun generateId(): String {
        return "sensory_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: SensoryConfig) {
        config = newConfig
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): SensoryConfig = config
}

// ========== 数据模型 ==========

/**
 * 感官输入
 */
data class SensoryInput(
    val modality: SensoryModality,
    val content: String,
    val intensity: Float              // 强度 0-1
)

/**
 * 感官记忆
 */
data class SensoryMemory(
    val id: String,
    val modality: SensoryModality,
    val content: String,
    val intensity: Float,             // 强度 0-1
    val salience: Float,              // 显著性 0-1
    val timestamp: LocalDateTime,
    val expiryTime: LocalDateTime
) {
    fun isExpired(now: LocalDateTime): Boolean {
        return now.isAfter(expiryTime)
    }

    fun getAge(now: LocalDateTime): Long {
        return java.time.Duration.between(timestamp, now).toMillis()
    }
}

/**
 * 感官模态
 */
enum class SensoryModality {
    VISUAL,          // 视觉
    AUDITORY,        // 听觉
    TACTILE,         // 触觉
    ENVIRONMENTAL    // 环境
}

/**
 * 环境数据
 */
data class EnvironmentalData(
    val location: String? = null,
    val timeOfDay: String? = null,
    val lightLevel: Float? = null,    // 0-1
    val noiseLevel: Float? = null,    // 0-1
    val temperature: Float? = null    // 摄氏度
)

/**
 * 环境摘要
 */
data class EnvironmentalSummary(
    val location: String?,
    val timeOfDay: String?,
    val lightLevel: Float?,
    val noiseLevel: Float?,
    val temperature: Float?,
    val activeModalitiesCount: Int,
    val dominantModality: SensoryModality?,
    val attentionLevel: Float,
    val timestamp: LocalDateTime
)

/**
 * 晋升事件
 */
data class PromotionEvent(
    val memoryId: String,
    val modality: SensoryModality,
    val content: String,
    val salience: Float,
    val timestamp: LocalDateTime
)

/**
 * 缓冲区状态
 */
data class BufferState(
    val visualCount: Int,
    val auditoryCount: Int,
    val tactileCount: Int,
    val environmentalCount: Int,
    val totalCapacity: Int
) {
    val totalCount: Int
        get() = visualCount + auditoryCount + tactileCount + environmentalCount

    val utilizationRate: Float
        get() = if (totalCapacity > 0) totalCount.toFloat() / totalCapacity else 0f
}

/**
 * 感官配置
 */
data class SensoryConfig(
    val retentionSeconds: Long = 5L,        // 保留时间（秒）
    val maxCapacityPerModality: Int = 10,   // 每个模态的最大容量
    val promotionThreshold: Float = 0.7f,   // 晋升阈值
    val cleanupIntervalMs: Long = 1000L     // 清理间隔（毫秒）
)

/**
 * 感官统计
 */
data class SensoryStats(
    val totalReceived: Int,
    val totalExpired: Int,
    val totalPromoted: Int,
    val averageRetentionTime: Float,
    val lastCleanupTime: LocalDateTime?
)