package com.xiaoguang.assistant.data.remote.api

import com.xiaoguang.assistant.data.remote.dto.AudioTranscriptionResponse
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.data.remote.dto.ChatResponse
import com.xiaoguang.assistant.data.remote.dto.EmbeddingRequest
import com.xiaoguang.assistant.data.remote.dto.EmbeddingResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface SiliconFlowAPI {

    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @POST("v1/chat/completions")
    @Streaming
    @Headers("Content-Type: application/json")
    fun chatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Call<ResponseBody>

    /**
     * 语音转文本 API
     * 使用 TeleSpeechASR 或 SenseVoiceSmall 模型
     */
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun audioTranscription(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): Response<AudioTranscriptionResponse>

    /**
     * 文本向量化 API
     * 支持的模型:
     * - Qwen/Qwen3-Embedding-0.6B (1024维)
     * - Qwen/Qwen3-Embedding-4B (2560维)
     * - Qwen/Qwen3-Embedding-8B (4096维)
     */
    @POST("v1/embeddings")
    @Headers("Content-Type: application/json")
    suspend fun createEmbeddings(
        @Header("Authorization") authorization: String,
        @Body request: EmbeddingRequest
    ): Response<EmbeddingResponse>

    companion object {
        const val BASE_URL = "https://api.siliconflow.cn/"

        // 推荐的Embedding模型
        const val EMBEDDING_MODEL_0_6B = "Qwen/Qwen3-Embedding-0.6B"  // 1024维
        const val EMBEDDING_MODEL_4B = "Qwen/Qwen3-Embedding-4B"      // 2560维
        const val EMBEDDING_MODEL_8B = "Qwen/Qwen3-Embedding-8B"      // 4096维
    }
}
