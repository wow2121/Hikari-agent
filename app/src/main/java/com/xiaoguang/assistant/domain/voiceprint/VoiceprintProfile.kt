package com.xiaoguang.assistant.domain.voiceprint

/**
 * 声纹档案
 * 存储一个人的声纹特征和身份信息
 */
data class VoiceprintProfile(
    val voiceprintId: String,           // 声纹ID（UUID）
    val personId: String,                // 对应的人物ID（关联IdentityRegistry）
    val personName: String?,             // 人物名称（可能为空，陌生人）
    val displayName: String,             // 显示名称（陌生人用"陌生人_01"等临时代号）
    val isMaster: Boolean = false,       // 是否是主人
    val isStranger: Boolean = false,     // 是否是陌生人（未命名）
    val featureVector: FloatArray,       // 声纹特征向量
    val sampleCount: Int = 0,            // 采集的样本数量
    val confidence: Float = 0f,          // 声纹质量/置信度 (0.0-1.0)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()  // 额外元数据
) {
    /**
     * 获取有效的人物标识符（优先使用真名，否则使用显示名）
     */
    fun getEffectiveIdentifier(): String = personName ?: displayName

    /**
     * 是否已命名（不是陌生人）
     */
    fun isNamed(): Boolean = !personName.isNullOrBlank()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoiceprintProfile

        if (voiceprintId != other.voiceprintId) return false

        return true
    }

    override fun hashCode(): Int {
        return voiceprintId.hashCode()
    }
}

/**
 * 声纹识别结果
 */
data class VoiceprintIdentificationResult(
    val matched: Boolean,                // 是否匹配到已知声纹
    val profile: VoiceprintProfile?,     // 匹配到的声纹档案
    val similarity: Float = 0f,          // 相似度 (0.0-1.0)
    val confidence: Float = 0f,          // 识别置信度 (0.0-1.0)
    val speakerId: String? = null        // 说话人ID（未匹配时返回临时ID）
) {
    /**
     * 获取有效的说话人标识
     */
    fun getEffectiveSpeakerId(): String {
        return when {
            matched && profile != null -> profile.personId
            !speakerId.isNullOrBlank() -> speakerId
            else -> "unknown_speaker"
        }
    }

    /**
     * 获取显示名称
     */
    fun getDisplayName(): String {
        return when {
            matched && profile != null -> profile.getEffectiveIdentifier()
            !speakerId.isNullOrBlank() -> speakerId
            else -> "未知说话人"
        }
    }
}

/**
 * 声纹注册请求
 */
data class VoiceprintRegistrationRequest(
    val personId: String?,               // 人物ID（可选，新建时为null）
    val personName: String?,             // 人物名称（可选）
    val audioSamples: List<ByteArray>,   // 音频样本列表（PCM数据）
    val sampleRate: Int = 16000,         // 采样率
    val isMaster: Boolean = false,       // 是否注册为主人
    val metadata: Map<String, String> = emptyMap()
)
