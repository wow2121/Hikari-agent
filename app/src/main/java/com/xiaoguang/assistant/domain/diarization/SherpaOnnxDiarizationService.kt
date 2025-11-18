package com.xiaoguang.assistant.domain.diarization

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 ONNX Runtime 的说话人分离服务实现
 * Phase 2: Speaker Diarization
 *
 * ⚠️ 注意：这是一个简化的框架实现
 * TODO: 需要集成实际的 sherpa-onnx 或 pyannote 模型
 *
 * 当前实现策略：
 * 1. 使用模型加载器加载 ONNX 模型
 * 2. 预处理音频（PCM → 特征提取）
 * 3. 调用分割模型获取语音活动片段
 * 4. 调用嵌入模型提取每个片段的说话人特征
 * 5. 聚类相似特征，分配说话人标签
 */
@Singleton
class SherpaOnnxDiarizationService @Inject constructor(
    private val modelLoader: DiarizationModelLoader
) : SpeakerDiarizationService {

    private var initialized = false

    override suspend fun initialize() {
        if (initialized) {
            Timber.w("[SherpaOnnxDiarizationService] 已经初始化，跳过")
            return
        }

        val success = modelLoader.loadModels()
        if (success) {
            initialized = true
            Timber.i("[SherpaOnnxDiarizationService] 初始化成功")
        } else {
            Timber.e("[SherpaOnnxDiarizationService] 初始化失败")
            throw IllegalStateException("Failed to initialize speaker diarization service")
        }
    }

    override suspend fun process(
        audioData: ByteArray,
        sampleRate: Int
    ): DiarizationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        ensureInitialized()

        try {
            val result = processAudioWithDiarization(audioData, sampleRate)
            val processingTime = System.currentTimeMillis() - startTime

            Timber.d("[SherpaOnnxDiarizationService] 处理完成: ${result.segments.size} 个片段, ${processingTime}ms")

            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            handleProcessingError(e, audioData, sampleRate, startTime)
        }
    }

    /**
     * 确保服务已初始化
     */
    private suspend fun ensureInitialized() {
        if (!initialized) {
            Timber.w("[SherpaOnnxDiarizationService] 未初始化，尝试自动初始化...")
            initialize()
        }
    }

    /**
     * 执行完整的说话人分离流程
     */
    private suspend fun processAudioWithDiarization(
        audioData: ByteArray,
        sampleRate: Int
    ): DiarizationResult {
        // 1. 音频预处理
        val preprocessed = preprocessAudio(audioData, sampleRate)

        // 2. 语音活动检测和分割
        val segments = performSegmentation(preprocessed)
        if (segments.isEmpty()) {
            Timber.w("[SherpaOnnxDiarizationService] 未检测到语音片段")
            return DiarizationResult(emptyList(), 0)
        }

        // 3. 提取说话人嵌入
        val embeddingsWithSegments = extractSegmentEmbeddings(preprocessed, segments)

        // 4. 聚类并分配标签
        val labeledSegments = clusterAndLabel(embeddingsWithSegments)

        return DiarizationResult(
            segments = labeledSegments,
            processingTimeMs = 0 // 将在外层计算
        )
    }

    /**
     * 为所有语音片段提取说话人嵌入
     */
    private suspend fun extractSegmentEmbeddings(
        preprocessed: PreprocessedAudio,
        segments: List<TemporarySegment>
    ): List<Pair<TemporarySegment, FloatArray>> {
        return segments.map { segment ->
            val embedding = extractSpeakerEmbedding(preprocessed, segment)
            segment to embedding
        }
    }

    /**
     * 处理说话人分离过程中的错误
     */
    private fun handleProcessingError(
        e: Exception,
        audioData: ByteArray,
        sampleRate: Int,
        startTime: Long
    ): DiarizationResult {
        Timber.e(e, "[SherpaOnnxDiarizationService] 处理失败")

        // 降级：返回单说话人结果
        val duration = (audioData.size / 2) * 1000L / sampleRate  // PCM 16-bit
        val processingTime = System.currentTimeMillis() - startTime

        return DiarizationResult(
            segments = listOf(
                SpeakerSegment(
                    label = "speaker_0",
                    startMs = 0,
                    endMs = duration,
                    confidence = 0.5f
                )
            ),
            processingTimeMs = processingTime
        )
    }

    override fun isInitialized(): Boolean = initialized

    override fun release() {
        modelLoader.release()
        initialized = false
        Timber.i("[SherpaOnnxDiarizationService] 资源已释放")
    }

    // ================== 私有辅助方法 ==================

    /**
     * 音频预处理：PCM 16-bit → Float32 归一化
     */
    private fun preprocessAudio(audioData: ByteArray, sampleRate: Int): PreprocessedAudio {
        // PCM 16-bit → Float32 归一化到 [-1.0, 1.0]
        val samples = FloatArray(audioData.size / 2)
        for (i in samples.indices) {
            val idx = i * 2
            val sample = ((audioData[idx + 1].toInt() shl 8) or (audioData[idx].toInt() and 0xFF)).toShort()
            samples[i] = sample / 32768.0f
        }

        Timber.d("[SherpaOnnxDiarizationService] 音频预处理完成: ${samples.size} 样本, ${sampleRate}Hz")
        return PreprocessedAudio(samples, sampleRate)
    }

    /**
     * 将音频分块为模型输入格式
     * 参考 Python 代码中的 as_strided 实现
     */
    private fun createAudioChunks(audio: FloatArray, windowSize: Int, windowShift: Int): Array<FloatArray> {
        val numSamples = audio.size

        // 如果音频太短，返回单个填充后的块
        if (numSamples < windowSize) {
            val paddedChunk = FloatArray(windowSize)
            audio.copyInto(paddedChunk, 0, 0, numSamples)
            return arrayOf(paddedChunk)
        }

        // 计算可以生成的块数
        val numChunks = (numSamples - windowSize) / windowShift + 1
        val chunks = Array(numChunks) { FloatArray(windowSize) }

        // 生成重叠的音频块
        for (i in 0 until numChunks) {
            val startIdx = i * windowShift
            audio.copyInto(chunks[i], 0, startIdx, startIdx + windowSize)
        }

        // 处理剩余部分（如果有）
        val remainingSamples = numSamples - (numChunks * windowShift)
        if (remainingSamples > 0 && remainingSamples + numChunks * windowShift < numSamples) {
            // 有剩余，需要最后一个填充块
            val lastChunk = FloatArray(windowSize)
            val lastStartIdx = numChunks * windowShift
            val lastLength = numSamples - lastStartIdx
            audio.copyInto(lastChunk, 0, lastStartIdx, numSamples)
            // 剩余部分填充 0
            return chunks + lastChunk
        }

        return chunks
    }

    /**
     * 语音分割：使用分割模型检测语音活动区域
     *
     * 模型输入：(batch_size, 1, num_samples)
     * 模型输出：(batch_size, num_frames, num_classes)
     */
    private fun performSegmentation(preprocessed: PreprocessedAudio): List<TemporarySegment> {
        val session = modelLoader.getSegmentationSession()
            ?: throw IllegalStateException("Segmentation model not loaded")

        try {
            val ortEnv = ai.onnxruntime.OrtEnvironment.getEnvironment()

            // 从模型元数据读取参数
            val metadata = session.metadata.customMetadata
            val windowSize = metadata["window_size"]?.toIntOrNull() ?: 80000  // 默认 5秒 @ 16kHz
            val receptiveFieldSize = metadata["receptive_field_size"]?.toIntOrNull() ?: 640
            val receptiveFieldShift = metadata["receptive_field_shift"]?.toIntOrNull() ?: 160
            val numClasses = metadata["num_classes"]?.toIntOrNull() ?: 7

            val windowShift = (windowSize * 0.1f).toInt()  // 10% 重叠

            Timber.d("[Segmentation] windowSize=$windowSize, shift=$windowShift, receptiveField=$receptiveFieldSize/$receptiveFieldShift")

            // 创建音频块
            val chunks = createAudioChunks(preprocessed.samples, windowSize, windowShift)
            Timber.d("[Segmentation] 创建了 ${chunks.size} 个音频块")

            // 批量推理
            val batchSize = 8
            val allOutputs = mutableListOf<Array<Array<FloatArray>>>()

            for (batchStart in chunks.indices step batchSize) {
                val batchEnd = minOf(batchStart + batchSize, chunks.size)
                val batchChunks = chunks.sliceArray(batchStart until batchEnd)

                // 构建输入张量: (batch, 1, num_samples)
                val inputShape = longArrayOf(batchChunks.size.toLong(), 1L, windowSize.toLong())
                val inputData = FloatArray(batchChunks.size * windowSize)

                for (i in batchChunks.indices) {
                    batchChunks[i].copyInto(inputData, i * windowSize)
                }

                val inputBuffer = FloatBuffer.wrap(inputData)
                val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)

                // 推理
                val outputs = session.run(mapOf(session.inputNames.first() to inputTensor))
                val outputTensor = outputs[0].value as Array<Array<FloatArray>>
                allOutputs.add(outputTensor)

                inputTensor.close()
                outputs.close()
            }

            // 解析输出，检测语音片段
            val segments = parseSegmentationOutput(
                allOutputs,
                numChunks = chunks.size,
                windowSize = windowSize,
                windowShift = windowShift,
                receptiveFieldSize = receptiveFieldSize,
                receptiveFieldShift = receptiveFieldShift,
                sampleRate = preprocessed.sampleRate
            )

            Timber.i("[Segmentation] 检测到 ${segments.size} 个语音片段")
            return segments

        } catch (e: Exception) {
            Timber.e(e, "[Segmentation] 推理失败，降级为单片段")
            // 降级：整段音频作为一个片段
            val durationMs = (preprocessed.samples.size * 1000L) / preprocessed.sampleRate
            return listOf(TemporarySegment(0, durationMs, 0))
        }
    }

    /**
     * 解析分割模型输出，提取语音活动片段
     *
     * ⚠️ 重要改进：检测并标记重叠语音片段
     */
    private fun parseSegmentationOutput(
        outputs: List<Array<Array<FloatArray>>>,
        numChunks: Int,
        windowSize: Int,
        windowShift: Int,
        receptiveFieldSize: Int,
        receptiveFieldShift: Int,
        sampleRate: Int
    ): List<TemporarySegment> {
        val segments = mutableListOf<TemporarySegment>()

        if (outputs.isEmpty()) return segments

        val firstBatch = outputs[0]
        // firstBatch: (batch_size, num_frames, num_classes)

        for (chunkIdx in firstBatch.indices) {
            val frames = firstBatch[chunkIdx]  // (num_frames, num_classes)

            var startFrame: Int? = null
            val threshold = 0.5f
            var hasOverlap = false

            for (frameIdx in frames.indices) {
                val frameScores = frames[frameIdx]

                // ✅ 检测重叠：统计激活的类别数量
                // PyAnnote 模型的 powerset 输出：
                // - 类别 0: 静音
                // - 类别 1-N: 单个说话人
                // - 类别 N+: 多人重叠组合
                val activeClasses = frameScores.count { it > threshold }
                val isOverlapping = activeClasses > 1

                val maxScore = frameScores.maxOrNull() ?: 0f
                val isActive = maxScore > threshold

                if (isActive && startFrame == null) {
                    startFrame = frameIdx
                    hasOverlap = isOverlapping
                } else if (isActive && startFrame != null) {
                    // 继续累积，更新重叠状态
                    hasOverlap = hasOverlap || isOverlapping
                } else if (!isActive && startFrame != null) {
                    // 片段结束
                    val startMs = (chunkIdx * windowShift + startFrame * receptiveFieldShift) * 1000L / sampleRate
                    val endMs = (chunkIdx * windowShift + frameIdx * receptiveFieldShift) * 1000L / sampleRate

                    segments.add(TemporarySegment(
                        startMs = startMs,
                        endMs = endMs,
                        chunkIdx = chunkIdx,
                        isOverlapping = hasOverlap
                    ))

                    startFrame = null
                    hasOverlap = false
                }
            }

            // 如果到结尾还在活动中
            if (startFrame != null) {
                val startMs = (chunkIdx * windowShift + startFrame * receptiveFieldShift) * 1000L / sampleRate
                val endMs = (chunkIdx * windowShift + frames.size * receptiveFieldShift) * 1000L / sampleRate

                segments.add(TemporarySegment(
                    startMs = startMs,
                    endMs = endMs,
                    chunkIdx = chunkIdx,
                    isOverlapping = hasOverlap
                ))
            }
        }

        // 统计
        val overlappingCount = segments.count { it.isOverlapping }
        if (overlappingCount > 0) {
            Timber.w("[Segmentation] 检测到 $overlappingCount/${segments.size} 个重叠片段")
        }

        return segments
    }

    /**
     * 提取说话人嵌入向量
     *
     * 模型输入：(batch_size, num_samples)
     * 模型输出：(batch_size, embedding_dim)
     */
    private fun extractSpeakerEmbedding(
        preprocessed: PreprocessedAudio,
        segment: TemporarySegment
    ): FloatArray {
        val session = modelLoader.getEmbeddingSession()
            ?: throw IllegalStateException("Embedding model not loaded")

        try {
            val ortEnv = ai.onnxruntime.OrtEnvironment.getEnvironment()

            // 提取片段对应的音频数据
            val startSample = ((segment.startMs * preprocessed.sampleRate) / 1000).toInt()
            val endSample = ((segment.endMs * preprocessed.sampleRate) / 1000).toInt()
                .coerceAtMost(preprocessed.samples.size)

            if (startSample >= endSample || startSample >= preprocessed.samples.size) {
                Timber.w("[Embedding] 无效片段: $startSample-$endSample")
                return FloatArray(256)  // 返回零向量
            }

            val segmentAudio = preprocessed.samples.copyOfRange(startSample, endSample)

            // 模型可能需要固定长度，如果太短则填充，太长则截断
            val targetLength = 16000 * 3  // 3秒 @ 16kHz
            val inputAudio = when {
                segmentAudio.size < targetLength -> {
                    // 填充
                    FloatArray(targetLength).also {
                        segmentAudio.copyInto(it)
                    }
                }
                segmentAudio.size > targetLength -> {
                    // 截断（取中间部分）
                    val offset = (segmentAudio.size - targetLength) / 2
                    segmentAudio.copyOfRange(offset, offset + targetLength)
                }
                else -> segmentAudio
            }

            // 构建输入张量: (1, num_samples)
            val inputShape = longArrayOf(1L, inputAudio.size.toLong())
            val inputBuffer = FloatBuffer.wrap(inputAudio)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)

            // 推理
            val outputs = session.run(mapOf(session.inputNames.first() to inputTensor))
            val embedding = when (val outputValue = outputs[0].value) {
                is Array<*> -> {
                    // 输出是 (1, embedding_dim)
                    @Suppress("UNCHECKED_CAST")
                    (outputValue as Array<FloatArray>)[0]
                }
                is FloatArray -> outputValue
                else -> {
                    Timber.w("[Embedding] 未知输出格式: ${outputValue?.javaClass}")
                    FloatArray(256)
                }
            }

            inputTensor.close()
            outputs.close()

            Timber.v("[Embedding] 提取嵌入: ${embedding.size}维")
            return embedding

        } catch (e: Exception) {
            Timber.e(e, "[Embedding] 提取失败，返回零向量")
            return FloatArray(256)
        }
    }

    /**
     * 聚类并分配说话人标签
     * 使用余弦相似度 + 阈值聚类
     *
     * ⚠️ 策略：排除重叠片段进行聚类，然后用聚类结果推断重叠片段的标签
     */
    private fun clusterAndLabel(
        embeddingsWithSegments: List<Pair<TemporarySegment, FloatArray>>
    ): List<SpeakerSegment> {
        if (embeddingsWithSegments.isEmpty()) return emptyList()

        // ✅ 步骤1：分离清晰片段和重叠片段
        val clearSegments = embeddingsWithSegments.filter { !it.first.isOverlapping }
        val overlappingSegments = embeddingsWithSegments.filter { it.first.isOverlapping }

        Timber.d("[Clustering] 清晰片段: ${clearSegments.size}, 重叠片段: ${overlappingSegments.size}")

        // ✅ 步骤2：只用清晰片段进行聚类
        val clusterLabels = if (clearSegments.isNotEmpty()) {
            val clearEmbeddings = clearSegments.map { it.second }
            val similarityThreshold = 0.75f
            performAgglomerativeClustering(clearEmbeddings, similarityThreshold)
        } else {
            // 如果没有清晰片段，降级处理所有片段
            Timber.w("[Clustering] 没有清晰片段，使用全部片段聚类")
            val allEmbeddings = embeddingsWithSegments.map { it.second }
            performAgglomerativeClustering(allEmbeddings, 0.75f)
        }

        val numSpeakers = clusterLabels.distinct().size
        Timber.d("[Clustering] 聚类完成: $numSpeakers 个说话人")

        // ✅ 步骤3：为清晰片段分配标签
        val results = mutableListOf<SpeakerSegment>()

        clearSegments.forEachIndexed { idx, (segment, _) ->
            results.add(SpeakerSegment(
                label = "speaker_${clusterLabels[idx]}",
                startMs = segment.startMs,
                endMs = segment.endMs,
                confidence = 0.85f
            ))
        }

        // ✅ 步骤4：为重叠片段分配标签（使用最近清晰片段的标签，或标记为 unknown）
        overlappingSegments.forEach { (segment, embedding) ->
            val label = if (clearSegments.isNotEmpty()) {
                // 找最相似的清晰片段
                val similarities = clearSegments.mapIndexed { idx, (_, clearEmb) ->
                    idx to cosineSimilarity(embedding, clearEmb)
                }
                val bestMatch = similarities.maxByOrNull { it.second }
                if (bestMatch != null && bestMatch.second > 0.6f) {
                    "speaker_${clusterLabels[bestMatch.first]}"
                } else {
                    "speaker_overlap"  // 无法确定
                }
            } else {
                "speaker_0"
            }

            results.add(SpeakerSegment(
                label = label,
                startMs = segment.startMs,
                endMs = segment.endMs,
                confidence = 0.5f  // 重叠片段置信度较低
            ))
        }

        return results.sortedBy { it.startMs }
    }

    /**
     * 凝聚层次聚类
     *
     * @param embeddings 嵌入向量列表
     * @param threshold 余弦相似度阈值（0-1）
     * @return 每个嵌入对应的簇标签
     */
    private fun performAgglomerativeClustering(
        embeddings: List<FloatArray>,
        threshold: Float
    ): IntArray {
        val n = embeddings.size
        val labels = IntArray(n) { it }  // 初始每个样本自成一簇

        if (n <= 1) return labels

        // 计算所有对之间的余弦相似度
        val similarities = Array(n) { i ->
            FloatArray(n) { j ->
                if (i == j) 1.0f
                else cosineSimilarity(embeddings[i], embeddings[j])
            }
        }

        // 贪心合并：不断合并最相似的簇，直到没有高于阈值的对
        var changed = true
        while (changed) {
            changed = false
            var maxSim = threshold
            var mergeI = -1
            var mergeJ = -1

            // 找到最相似的两个不同簇
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (labels[i] != labels[j] && similarities[i][j] > maxSim) {
                        maxSim = similarities[i][j]
                        mergeI = i
                        mergeJ = j
                    }
                }
            }

            // 如果找到相似对，合并簇
            if (mergeI != -1 && mergeJ != -1) {
                val oldLabel = labels[mergeJ]
                val newLabel = labels[mergeI]
                for (k in labels.indices) {
                    if (labels[k] == oldLabel) {
                        labels[k] = newLabel
                    }
                }
                changed = true
            }
        }

        // 重新映射标签为连续的 0, 1, 2, ...
        val uniqueLabels = labels.distinct().sorted()
        val labelMap = uniqueLabels.withIndex().associate { it.value to it.index }
        return IntArray(n) { labelMap[labels[it]]!! }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = kotlin.math.sqrt(normA * normB)
        return if (denominator > 1e-8) {
            (dotProduct / denominator).toFloat()
        } else {
            0f
        }
    }

    /**
     * 临时片段数据（用于中间处理）
     */
    private data class TemporarySegment(
        val startMs: Long,
        val endMs: Long,
        val chunkIdx: Int = 0,
        val isOverlapping: Boolean = false  // ✅ 标记是否有多人重叠
    )

    /**
     * 预处理后的音频数据
     */
    private data class PreprocessedAudio(
        val samples: FloatArray,
        val sampleRate: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PreprocessedAudio
            if (!samples.contentEquals(other.samples)) return false
            if (sampleRate != other.sampleRate) return false
            return true
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + sampleRate
            return result
        }
    }
}
