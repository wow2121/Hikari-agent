package com.xiaoguang.assistant.domain.multimodal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * MultimodalFusionEngine 单元测试
 *
 * 测试覆盖：
 * 1. 语音情感分析
 * 2. 面部表情识别
 * 3. 文本情感分析
 * 4. 多模态融合
 */
class MultimodalFusionEngineTest {

    private lateinit var engine: MultimodalFusionEngine

    @Before
    fun setup() {
        engine = MultimodalFusionEngine()
    }

    // ========== 语音分析测试 ==========

    @Test
    fun `test analyzeVoice with positive emotion`() = runTest {
        val voiceData = VoiceData(
            pitch = 200f,      // 高音调
            volume = 60f,      // 适中音量
            speed = 3.5f,      // 正常语速
            jitter = 0.1f,
            signalQuality = 0.9f
        )

        val result = engine.analyzeVoice(voiceData)

        assertEquals(Modality.VOICE, result.modality)
        assertTrue(result.valence > 0f)  // 正面情感
        assertTrue(result.arousal >= 0f && result.arousal <= 1f)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `test analyzeVoice with negative emotion`() = runTest {
        val voiceData = VoiceData(
            pitch = 100f,      // 低音调
            volume = 35f,      // 低音量
            speed = 1.5f,      // 慢语速
            jitter = 0.3f,
            signalQuality = 0.8f
        )

        val result = engine.analyzeVoice(voiceData)

        assertTrue(result.valence <= 0f)  // 负面或中性情感
        assertTrue(result.arousal < 0.5f)  // 低唤醒度
    }

    @Test
    fun `test analyzeVoice with high arousal`() = runTest {
        val voiceData = VoiceData(
            pitch = 180f,
            volume = 85f,      // 高音量
            speed = 5.5f,      // 快语速
            jitter = 0.4f,     // 高颤抖度
            signalQuality = 0.85f
        )

        val result = engine.analyzeVoice(voiceData)

        assertTrue(result.arousal > 0.6f)  // 高唤醒度
    }

    // ========== 面部分析测试 ==========

    @Test
    fun `test analyzeFace detects happy expression`() = runTest {
        val faceData = FaceData(
            mouthOpen = 0.3f,
            eyebrowRaise = 0.2f,
            eyeSquint = 0.1f,
            mouthCornerUp = 0.8f,    // 嘴角上扬
            mouthCornerDown = 0f,
            detectionQuality = 0.95f
        )

        val result = engine.analyzeFace(faceData)

        assertEquals(Modality.FACE, result.modality)
        assertTrue(result.valence > 0.5f)  // 正面情感
        assertTrue(result.confidence > 0.7f)
    }

    @Test
    fun `test analyzeFace detects sad expression`() = runTest {
        val faceData = FaceData(
            mouthOpen = 0.1f,
            eyebrowRaise = 0.3f,
            eyeSquint = 0.2f,
            mouthCornerUp = 0f,
            mouthCornerDown = 0.7f,  // 嘴角下垂
            detectionQuality = 0.9f
        )

        val result = engine.analyzeFace(faceData)

        assertTrue(result.valence < 0f)  // 负面情感
        assertTrue(result.arousal < 0.5f)  // 低唤醒度
    }

    @Test
    fun `test analyzeFace detects angry expression`() = runTest {
        val faceData = FaceData(
            mouthOpen = 0.2f,
            eyebrowRaise = 0.1f,
            eyeSquint = 0.7f,        // 眼睛眯缝
            mouthCornerUp = 0f,
            mouthCornerDown = 0.4f,
            detectionQuality = 0.85f
        )

        val result = engine.analyzeFace(faceData)

        assertTrue(result.valence < 0f)  // 负面情感
        assertTrue(result.arousal > 0.6f)  // 高唤醒度
    }

    @Test
    fun `test analyzeFace detects surprised expression`() = runTest {
        val faceData = FaceData(
            mouthOpen = 0.6f,        // 嘴巴张开
            eyebrowRaise = 0.8f,     // 眉毛上扬
            eyeSquint = 0.1f,
            mouthCornerUp = 0.2f,
            mouthCornerDown = 0.1f,
            detectionQuality = 0.9f
        )

        val result = engine.analyzeFace(faceData)

        assertTrue(result.arousal > 0.7f)  // 高唤醒度
    }

    // ========== 文本分析测试 ==========

    @Test
    fun `test analyzeText with positive sentiment`() = runTest {
        val textData = TextData("我今天很开心！太棒了！")

        val result = engine.analyzeText(textData)

        assertEquals(Modality.TEXT, result.modality)
        assertTrue(result.valence > 0f)  // 正面情感
        assertTrue(result.arousal > 0.3f)  // 有一定唤醒度
    }

    @Test
    fun `test analyzeText with negative sentiment`() = runTest {
        val textData = TextData("我很难过，太不好了")

        val result = engine.analyzeText(textData)

        assertTrue(result.valence < 0f)  // 负面情感
    }

    @Test
    fun `test analyzeText with excited sentiment`() = runTest {
        val textData = TextData("太激动了！！！哇！！")

        val result = engine.analyzeText(textData)

        assertTrue(result.arousal > 0.5f)  // 高唤醒度
    }

    @Test
    fun `test analyzeText with neutral sentiment`() = runTest {
        val textData = TextData("今天天气还可以")

        val result = engine.analyzeText(textData)

        assertTrue(result.valence >= -0.3f && result.valence <= 0.3f)  // 中性
    }

    // ========== 多模态融合测试 ==========

    @Test
    fun `test fuseMultimodal with all modalities`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 200f,
                volume = 60f,
                speed = 3.5f,
                jitter = 0.1f,
                signalQuality = 0.9f
            ),
            faceData = FaceData(
                mouthOpen = 0.3f,
                mouthCornerUp = 0.8f,
                detectionQuality = 0.9f
            ),
            textData = TextData("我很开心！")
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
        val fusedEmotion = result.getOrNull()!!
        assertEquals(3, fusedEmotion.modalityCount)
        assertTrue(fusedEmotion.valence > 0f)  // 所有模态都是正面，融合应该是正面
        assertTrue(fusedEmotion.confidence > 0.5f)
    }

    @Test
    fun `test fuseMultimodal with voice only`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 200f,
                volume = 60f,
                speed = 3.5f,
                signalQuality = 0.9f
            )
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
        val fusedEmotion = result.getOrNull()!!
        assertEquals(1, fusedEmotion.modalityCount)
        assertEquals(1, fusedEmotion.modalityBreakdown.size)
        assertEquals(Modality.VOICE, fusedEmotion.modalityBreakdown[0].modality)
    }

    @Test
    fun `test fuseMultimodal with conflicting modalities`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 200f,  // 高音调（正面）
                volume = 60f,
                speed = 3.5f,
                signalQuality = 0.9f
            ),
            faceData = FaceData(
                mouthCornerDown = 0.8f,  // 嘴角下垂（负面）
                detectionQuality = 0.9f
            )
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
        val fusedEmotion = result.getOrNull()!!
        // 冲突的模态应该导致较低的置信度
        assertTrue(fusedEmotion.confidence < 0.8f)
    }

    @Test
    fun `test fuseMultimodal fails with no modality data`() = runTest {
        val input = MultimodalInput()

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isFailure)
    }

    @Test
    fun `test fuseMultimodal with consistent modalities has high confidence`() = runTest {
        // 所有模态都显示相同的情感
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 200f,
                volume = 60f,
                speed = 3.5f,
                signalQuality = 0.9f
            ),
            faceData = FaceData(
                mouthCornerUp = 0.8f,
                detectionQuality = 0.9f
            ),
            textData = TextData("我很开心！")
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
        val fusedEmotion = result.getOrNull()!!
        // 一致的模态应该有更高的置信度
        assertTrue(fusedEmotion.confidence > 0.6f)
    }

    // ========== 配置测试 ==========

    @Test
    fun `test updateConfig changes weights`() {
        val newConfig = FusionConfig(
            voiceWeight = 0.5f,
            faceWeight = 0.3f,
            textWeight = 0.2f
        )

        engine.updateConfig(newConfig)
        val currentConfig = engine.getConfig()

        assertEquals(newConfig, currentConfig)
        assertEquals(0.5f, currentConfig.voiceWeight, 0.01f)
        assertEquals(0.3f, currentConfig.faceWeight, 0.01f)
    }

    // ========== 统计测试 ==========

    @Test
    fun `test stats are updated after fusion`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(pitch = 200f, volume = 60f, signalQuality = 0.9f)
        )

        val initialStats = engine.stats.value

        engine.fuseMultimodal(input)

        val updatedStats = engine.stats.value
        assertEquals(initialStats.totalFusions + 1, updatedStats.totalFusions)
        assertEquals(initialStats.voiceAnalysisCount + 1, updatedStats.voiceAnalysisCount)
        assertNotNull(updatedStats.lastFusionTime)
    }

    @Test
    fun `test fusionHistory is maintained`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(pitch = 200f, volume = 60f, signalQuality = 0.9f)
        )

        engine.fuseMultimodal(input)

        val history = engine.fusionHistory.value
        assertTrue(history.isNotEmpty())
        assertEquals(1, history.size)
    }

    // ========== 边界测试 ==========

    @Test
    fun `test handles extreme values gracefully`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 1000f,  // 极高音调
                volume = 150f,  // 极高音量
                speed = 20f,    // 极快语速
                signalQuality = 1f
            )
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
        val fusedEmotion = result.getOrNull()!!
        // 应该被限制在有效范围内
        assertTrue(fusedEmotion.valence >= -1f && fusedEmotion.valence <= 1f)
        assertTrue(fusedEmotion.arousal >= 0f && fusedEmotion.arousal <= 1f)
    }

    @Test
    fun `test handles missing optional fields`() = runTest {
        val input = MultimodalInput(
            voiceData = VoiceData(
                pitch = 200f
                // 其他字段为空
            )
        )

        val result = engine.fuseMultimodal(input)

        assertTrue(result.isSuccess)
    }
}