package com.xiaoguang.assistant.domain.usecase

import com.google.gson.Gson
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.data.remote.dto.EventExtractionResponse
import com.xiaoguang.assistant.domain.model.EventIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class ExtractEventUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val gson: Gson = Gson()
) {

    suspend fun extractEventFromConversation(conversationText: String): EventIntent? = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = SYSTEM_PROMPT
                ),
                ChatMessage(
                    role = "user",
                    content = conversationText
                )
            )

            val request = ChatRequest(
                messages = messages,
                temperature = 0.3f, // Lower temperature for more consistent extraction
                maxTokens = 500
            )

            val apiKey = BuildConfig.SILICON_FLOW_API_KEY
            if (apiKey.isBlank()) {
                Timber.w("API Key not configured")
                return@withContext null
            }

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                Timber.d("Event extraction response: $content")

                content?.let { parseEventResponse(it) }
            } else {
                Timber.e("Event extraction failed: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting event")
            null
        }
    }

    private fun parseEventResponse(jsonString: String): EventIntent? {
        return try {
            // Try to extract JSON from the response
            val jsonStart = jsonString.indexOf("{")
            val jsonEnd = jsonString.lastIndexOf("}") + 1

            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonContent = jsonString.substring(jsonStart, jsonEnd)
                val extraction = gson.fromJson(jsonContent, EventExtractionResponse::class.java)

                if (extraction.hasEvent && extraction.confidence > 0.5f) {
                    EventIntent(
                        hasEvent = true,
                        title = extraction.title,
                        datetime = extraction.datetime,
                        durationMinutes = extraction.durationMinutes,
                        location = extraction.location,
                        description = extraction.description,
                        confidence = extraction.confidence,
                        participants = extraction.participants
                    )
                } else {
                    null
                }
            } else {
                Timber.w("No JSON found in response: $jsonString")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing event response: $jsonString")
            null
        }
    }

    companion object {
        private const val SYSTEM_PROMPT = """你是一个日程提取助手。从对话中提取日程相关信息。

重点关注以下类型的信息：
- 作业和任务（如"明天交数学作业"）
- 考试和测验（如"下周三有语文考试"）
- 会议和活动（如"周五下午2点开会"）
- 截止日期和提交时间

如果发现日程相关内容，返回JSON格式：
{
  "has_event": true,
  "title": "事件标题",
  "datetime": "具体时间（相对时间或绝对时间）",
  "duration_minutes": 60,
  "location": "地点（如果有）",
  "description": "详细描述",
  "confidence": 0.85,
  "participants": ["相关人员"]
}

如果没有日程信息，返回：
{
  "has_event": false,
  "confidence": 0.0
}

只返回JSON，不要添加其他说明文字。"""
    }
}
