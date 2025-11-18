package com.xiaoguang.assistant.domain.multimodal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 多模态融合引擎
 *
 * 功能：
 * 1. 语音情感分析 - 从语音特征提取情感信息（音调、音量、语速等）
 * 2. 面部表情识别 - 识别面部表情并映射到情感维度
 * 3. 模态加权融合 - 根据置信度融合多个模态的情感信息
 *
 * 基于 Valence-Arousal 二维情感模型
 */
@Singleton
class MultimodalFusionEngine @Inject constructor() {

    private val mutex = Mutex()
    private var config = FusionConfig()

    // 融合历史记录
    private val _fusionHistory = MutableStateFlow<List<FusionRecord>>(emptyList())
    val fusionHistory: StateFlow<List<FusionRecord>> = _fusionHistory.asStateFlow()

    // 统计数据
    private val _stats = MutableStateFlow(
        FusionStats(
            totalFusions = 0,
            voiceAnalysisCount = 0,
            faceAnalysisCount = 0,
            textAnalysisCount = 0,
            averageConfidence = 0f,
            lastFusionTime = null
        )
    )
    val stats: StateFlow<FusionStats> = _stats.asStateFlow()

    /**
     * 融合多模态输入
     *
     * @param multimodalInput 多模态输入数据
     * @return 融合后的情感结果
     */
    suspend fun fuseMultimodal(multimodalInput: MultimodalInput): Result<FusedEmotion> {
        return withContext(Dispatchers.Default) {
            try {
                mutex.withLock {
                    val modalityResults = mutableListOf<ModalityEmotionResult>()

                    // 并行分析各个模态
                    val analyses = listOf(
                        async { multimodalInput.voiceData?.let { analyzeVoice(it) } },
                        async { multimodalInput.faceData?.let { analyzeFace(it) } },
                        async { multimodalInput.textData?.let { analyzeText(it) } }
                    ).awaitAll()

                    // 收集有效结果
                    analyses.filterNotNull().forEach { modalityResults.add(it) }

                    if (modalityResults.isEmpty()) {
                        return@withContext Result.failure(
                            IllegalArgumentException("No valid modality data provided")
                        )
                    }

                    // 融合多个模态
                    val fusedEmotion = fuseEmotions(modalityResults)

                    // 记录融合结果
                    recordFusion(multimodalInput, fusedEmotion, modalityResults)

                    Result.success(fusedEmotion)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 分析语音情感
     *
     * @param voiceData 语音数据（音频特征）
     * @return 语音情感分析结果
     */
    suspend fun analyzeVoice(voiceData: VoiceData): ModalityEmotionResult {
        return withContext(Dispatchers.Default) {
            // 提取语音特征
            val pitch = voiceData.pitch ?: 0f          // 音调 (Hz)
            val volume = voiceData.volume ?: 0f        // 音量 (dB)
            val speed = voiceData.speed ?: 1.0f        // 语速 (字/秒)
            val jitter = voiceData.jitter ?: 0f        // 声音颤抖度

            // 基于语音特征推断情感
            // Valence (效价): 音调和音量的综合
            val valence = calculateVoiceValence(pitch, volume, speed)

            // Arousal (唤醒度): 音量和语速的综合
            val arousal = calculateVoiceArousal(volume, speed, jitter)

            // 计算置信度
            val confidence = calculateVoiceConfidence(voiceData)

            ModalityEmotionResult(
                modality = Modality.VOICE,
                valence = valence,
                arousal = arousal,
                confidence = confidence,
                rawFeatures = mapOf(
                    "pitch" to pitch,
                    "volume" to volume,
                    "speed" to speed,
                    "jitter" to jitter
                ),
                timestamp = LocalDateTime.now()
            )
        }
    }

    /**
     * 分析面部表情
     *
     * @param faceData 面部数据（表情特征）
     * @return 面部表情分析结果
     */
    suspend fun analyzeFace(faceData: FaceData): ModalityEmotionResult {
        return withContext(Dispatchers.Default) {
            // 面部表情识别
            val detectedExpression = detectExpression(faceData)

            // 将表情映射到 Valence-Arousal 空间
            val (valence, arousal) = mapExpressionToEmotion(detectedExpression)

            // 计算置信度（基于面部特征清晰度）
            val confidence = calculateFaceConfidence(faceData, detectedExpression)

            ModalityEmotionResult(
                modality = Modality.FACE,
                valence = valence,
                arousal = arousal,
                confidence = confidence,
                rawFeatures = mapOf(
                    "expression_${detectedExpression.name.lowercase()}" to 1f,
                    "mouth_open" to (faceData.mouthOpen ?: 0f),
                    "eyebrow_raise" to (faceData.eyebrowRaise ?: 0f),
                    "eye_squint" to (faceData.eyeSquint ?: 0f)
                ),
                timestamp = LocalDateTime.now()
            )
        }
    }

    /**
     * 分析文本情感（辅助模态）
     *
     * @param textData 文本数据
     * @return 文本情感分析结果
     */
    suspend fun analyzeText(textData: TextData): ModalityEmotionResult {
        return withContext(Dispatchers.Default) {
            val text = textData.content

            // 简化的文本情感分析（关键词匹配）
            val positiveWords = listOf("开心", "高兴", "喜欢", "棒", "好", "哈哈", "😊", "😄")
            val negativeWords = listOf("难过", "生气", "讨厌", "差", "不好", "😢", "😠")
            val excitedWords = listOf("激动", "兴奋", "哇", "！！", "!!", "太", "超")

            var valence = 0f
            var arousal = 0.3f  // 基线唤醒度

            // 计算 Valence
            val positiveCount = positiveWords.count { text.contains(it) }
            val negativeCount = negativeWords.count { text.contains(it) }
            valence = ((positiveCount - negativeCount) / 5f).coerceIn(-1f, 1f)

            // 计算 Arousal
            val excitedCount = excitedWords.count { text.contains(it) }
            val exclamationCount = text.count { it == '！' || it == '!' }
            arousal = (0.3f + excitedCount * 0.2f + exclamationCount * 0.1f).coerceIn(0f, 1f)

            // 置信度较低（文本分析不如语音和面部准确）
            val confidence = 0.6f

            ModalityEmotionResult(
                modality = Modality.TEXT,
                valence = valence,
                arousal = arousal,
                confidence = confidence,
                rawFeatures = mapOf(
                    "text_length" to text.length.toFloat(),
                    "positive_count" to positiveCount.toFloat(),
                    "negative_count" to negativeCount.toFloat(),
                    "excited_count" to excitedCount.toFloat()
                ),
                timestamp = LocalDateTime.now()
            )
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 融合多个模态的情感结果
     */
    private fun fuseEmotions(modalityResults: List<ModalityEmotionResult>): FusedEmotion {
        // 加权平均融合
        var totalWeight = 0f
        var weightedValence = 0f
        var weightedArousal = 0f

        modalityResults.forEach { result ->
            val weight = calculateModalityWeight(result)
            weightedValence += result.valence * weight
            weightedArousal += result.arousal * weight
            totalWeight += weight
        }

        val fusedValence = if (totalWeight > 0) weightedValence / totalWeight else 0f
        val fusedArousal = if (totalWeight > 0) weightedArousal / totalWeight else 0.5f

        // 计算融合置信度
        val fusedConfidence = calculateFusedConfidence(modalityResults)

        // 映射到情感标签
        val emotionLabel = mapToEmotionLabel(fusedValence, fusedArousal)

        return FusedEmotion(
            valence = fusedValence,
            arousal = fusedArousal,
            confidence = fusedConfidence,
            emotionLabel = emotionLabel,
            modalityCount = modalityResults.size,
            modalityBreakdown = modalityResults,
            timestamp = LocalDateTime.now()
        )
    }

    /**
     * 计算语音 Valence
     */
    private fun calculateVoiceValence(pitch: Float, volume: Float, speed: Float): Float {
        // 音调越高、音量适中、语速适中 → 更正面
        val pitchScore = (pitch - 150f) / 150f  // 标准化，假设150Hz为基准
        val volumeScore = when {
            volume < 40f -> -0.2f    // 音量太小可能表示消极
            volume > 80f -> 0.1f     // 音量太大可能表示激动但不一定正面
            else -> 0.3f             // 适中音量
        }
        val speedScore = when {
            speed < 2f -> -0.1f      // 语速太慢
            speed > 5f -> 0f         // 语速太快
            else -> 0.2f             // 正常语速
        }

        return (pitchScore * 0.4f + volumeScore + speedScore).coerceIn(-1f, 1f)
    }

    /**
     * 计算语音 Arousal
     */
    private fun calculateVoiceArousal(volume: Float, speed: Float, jitter: Float): Float {
        // 音量越大、语速越快、颤抖度越高 → 唤醒度越高
        val volumeContribution = (volume / 100f).coerceIn(0f, 1f) * 0.4f
        val speedContribution = (speed / 6f).coerceIn(0f, 1f) * 0.4f
        val jitterContribution = jitter.coerceIn(0f, 1f) * 0.2f

        return (volumeContribution + speedContribution + jitterContribution).coerceIn(0f, 1f)
    }

    /**
     * 计算语音分析置信度
     */
    private fun calculateVoiceConfidence(voiceData: VoiceData): Float {
        var confidence = 0.7f  // 基础置信度

        // 数据完整性
        val completeness = listOf(
            voiceData.pitch,
            voiceData.volume,
            voiceData.speed,
            voiceData.jitter
        ).count { it != null } / 4f

        confidence *= completeness

        // 信号质量
        voiceData.signalQuality?.let {
            confidence *= it
        }

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 检测面部表情
     */
    private fun detectExpression(faceData: FaceData): FacialExpression {
        // 基于面部特征检测表情（简化版本）
        val mouthOpen = faceData.mouthOpen ?: 0f
        val eyebrowRaise = faceData.eyebrowRaise ?: 0f
        val eyeSquint = faceData.eyeSquint ?: 0f
        val mouthCornerUp = faceData.mouthCornerUp ?: 0f
        val mouthCornerDown = faceData.mouthCornerDown ?: 0f

        return when {
            mouthCornerUp > 0.5f && eyeSquint < 0.3f -> FacialExpression.HAPPY
            mouthCornerDown > 0.5f -> FacialExpression.SAD
            eyebrowRaise > 0.6f && mouthOpen > 0.3f -> FacialExpression.SURPRISED
            eyeSquint > 0.6f && mouthCornerDown > 0.3f -> FacialExpression.ANGRY
            eyebrowRaise > 0.5f && mouthOpen < 0.2f -> FacialExpression.FEARFUL
            mouthCornerUp > 0.3f && eyeSquint > 0.5f -> FacialExpression.EXCITED
            else -> FacialExpression.NEUTRAL
        }
    }

    /**
     * 将表情映射到情感维度
     */
    private fun mapExpressionToEmotion(expression: FacialExpression): Pair<Float, Float> {
        // 返回 (Valence, Arousal)
        return when (expression) {
            FacialExpression.HAPPY -> Pair(0.8f, 0.6f)
            FacialExpression.SAD -> Pair(-0.7f, 0.3f)
            FacialExpression.ANGRY -> Pair(-0.8f, 0.9f)
            FacialExpression.FEARFUL -> Pair(-0.6f, 0.8f)
            FacialExpression.SURPRISED -> Pair(0.2f, 0.9f)
            FacialExpression.DISGUSTED -> Pair(-0.6f, 0.5f)
            FacialExpression.EXCITED -> Pair(0.7f, 0.9f)
            FacialExpression.NEUTRAL -> Pair(0f, 0.3f)
        }
    }

    /**
     * 计算面部分析置信度
     */
    private fun calculateFaceConfidence(faceData: FaceData, expression: FacialExpression): Float {
        var confidence = 0.8f  // 面部分析基础置信度较高

        // 检测质量
        faceData.detectionQuality?.let {
            confidence *= it
        }

        // 特征清晰度
        val featureClarity = listOf(
            faceData.mouthOpen,
            faceData.eyebrowRaise,
            faceData.eyeSquint
        ).count { it != null && abs(it) > 0.1f } / 3f

        confidence *= (0.5f + featureClarity * 0.5f)

        // 对中性表情降低置信度（难以判断）
        if (expression == FacialExpression.NEUTRAL) {
            confidence *= 0.7f
        }

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 计算模态权重
     */
    private fun calculateModalityWeight(result: ModalityEmotionResult): Float {
        // 基于置信度和模态优先级
        val baseWeight = when (result.modality) {
            Modality.VOICE -> config.voiceWeight
            Modality.FACE -> config.faceWeight
            Modality.TEXT -> config.textWeight
        }

        return baseWeight * result.confidence
    }

    /**
     * 计算融合置信度
     */
    private fun calculateFusedConfidence(modalityResults: List<ModalityEmotionResult>): Float {
        if (modalityResults.isEmpty()) return 0f

        // 多模态一致性检查
        val valences = modalityResults.map { it.valence }
        val arousals = modalityResults.map { it.arousal }

        val valenceVariance = calculateVariance(valences)
        val arousalVariance = calculateVariance(arousals)

        // 一致性分数（方差越小，一致性越高）
        val consistencyScore = 1f - (valenceVariance + arousalVariance) / 4f

        // 平均置信度
        val avgConfidence = modalityResults.map { it.confidence }.average().toFloat()

        // 融合置信度 = 平均置信度 * 一致性分数
        return (avgConfidence * consistencyScore).coerceIn(0f, 1f)
    }

    /**
     * 计算方差
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }

    /**
     * 映射到情感标签
     */
    private fun mapToEmotionLabel(valence: Float, arousal: Float): String {
        return when {
            valence > 0.3f && arousal > 0.6f -> "兴奋"   // 高激动正面
            valence > 0.3f && arousal <= 0.6f -> "平静喜悦" // 低激动正面
            valence < -0.3f && arousal > 0.6f -> "愤怒/恐惧" // 高激动负面
            valence < -0.3f && arousal <= 0.6f -> "悲伤" // 低激动负面
            abs(valence) <= 0.3f && arousal > 0.6f -> "惊讶" // 中性高激动
            else -> "中性"                           // 中性低激动
        }
    }

    /**
     * 记录融合结果
     */
    private fun recordFusion(
        input: MultimodalInput,
        fusedEmotion: FusedEmotion,
        modalityResults: List<ModalityEmotionResult>
    ) {
        val record = FusionRecord(
            timestamp = LocalDateTime.now(),
            modalities = modalityResults.map { it.modality },
            fusedValence = fusedEmotion.valence,
            fusedArousal = fusedEmotion.arousal,
            fusedConfidence = fusedEmotion.confidence,
            emotionLabel = fusedEmotion.emotionLabel
        )

        _fusionHistory.value = (_fusionHistory.value + record).takeLast(100)

        _stats.value = _stats.value.copy(
            totalFusions = _stats.value.totalFusions + 1,
            voiceAnalysisCount = _stats.value.voiceAnalysisCount +
                    if (input.voiceData != null) 1 else 0,
            faceAnalysisCount = _stats.value.faceAnalysisCount +
                    if (input.faceData != null) 1 else 0,
            textAnalysisCount = _stats.value.textAnalysisCount +
                    if (input.textData != null) 1 else 0,
            averageConfidence = (
                _stats.value.averageConfidence * _stats.value.totalFusions + fusedEmotion.confidence
            ) / (_stats.value.totalFusions + 1),
            lastFusionTime = LocalDateTime.now()
        )
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: FusionConfig) {
        config = newConfig
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): FusionConfig = config
}

// ========== 数据模型 ==========

/**
 * 多模态输入
 */
data class MultimodalInput(
    val voiceData: VoiceData? = null,
    val faceData: FaceData? = null,
    val textData: TextData? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 语音数据
 */
data class VoiceData(
    val pitch: Float? = null,           // 音调 (Hz)
    val volume: Float? = null,          // 音量 (dB)
    val speed: Float? = null,           // 语速 (字/秒)
    val jitter: Float? = null,          // 声音颤抖度 0-1
    val signalQuality: Float? = null    // 信号质量 0-1
)

/**
 * 面部数据
 */
data class FaceData(
    val mouthOpen: Float? = null,        // 嘴巴张开度 0-1
    val eyebrowRaise: Float? = null,     // 眉毛上扬度 0-1
    val eyeSquint: Float? = null,        // 眼睛眯缝度 0-1
    val mouthCornerUp: Float? = null,    // 嘴角上扬度 0-1
    val mouthCornerDown: Float? = null,  // 嘴角下垂度 0-1
    val detectionQuality: Float? = null  // 检测质量 0-1
)

/**
 * 文本数据
 */
data class TextData(
    val content: String
)

/**
 * 模态类型
 */
enum class Modality {
    VOICE,  // 语音
    FACE,   // 面部
    TEXT    // 文本
}

/**
 * 面部表情
 */
enum class FacialExpression {
    HAPPY,      // 开心
    SAD,        // 悲伤
    ANGRY,      // 愤怒
    FEARFUL,    // 恐惧
    SURPRISED,  // 惊讶
    DISGUSTED,  // 厌恶
    EXCITED,    // 兴奋
    NEUTRAL     // 中性
}

/**
 * 单个模态的情感结果
 */
data class ModalityEmotionResult(
    val modality: Modality,
    val valence: Float,                // -1.0 ~ 1.0
    val arousal: Float,                // 0.0 ~ 1.0
    val confidence: Float,             // 0.0 ~ 1.0
    val rawFeatures: Map<String, Float>,
    val timestamp: LocalDateTime
)

/**
 * 融合后的情感结果
 */
data class FusedEmotion(
    val valence: Float,                          // -1.0 ~ 1.0
    val arousal: Float,                          // 0.0 ~ 1.0
    val confidence: Float,                       // 0.0 ~ 1.0
    val emotionLabel: String,                    // 情感标签
    val modalityCount: Int,                      // 参与融合的模态数量
    val modalityBreakdown: List<ModalityEmotionResult>,
    val timestamp: LocalDateTime
)

/**
 * 融合配置
 */
data class FusionConfig(
    val voiceWeight: Float = 0.4f,      // 语音权重
    val faceWeight: Float = 0.4f,       // 面部权重
    val textWeight: Float = 0.2f,       // 文本权重
    val minConfidenceThreshold: Float = 0.3f  // 最小置信度阈值
)

/**
 * 融合记录
 */
data class FusionRecord(
    val timestamp: LocalDateTime,
    val modalities: List<Modality>,
    val fusedValence: Float,
    val fusedArousal: Float,
    val fusedConfidence: Float,
    val emotionLabel: String
)

/**
 * 融合统计
 */
data class FusionStats(
    val totalFusions: Int,
    val voiceAnalysisCount: Int,
    val faceAnalysisCount: Int,
    val textAnalysisCount: Int,
    val averageConfidence: Float,
    val lastFusionTime: LocalDateTime?
)