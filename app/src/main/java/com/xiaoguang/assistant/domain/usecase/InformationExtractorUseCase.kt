package com.xiaoguang.assistant.domain.usecase

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.model.*
import com.xiaoguang.assistant.domain.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 智能信息提取UseCase
 * 使用DeepSeek-V3.2分析对话文本,提取任务、事件和截止日期
 */
class InformationExtractorUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val todoRepository: TodoRepository,
    private val timeParser: TimeParser,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "InfoExtractor"
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6f
    }

    /**
     * 从对话文本中提取信息
     * @param conversationText 对话文本
     * @param confidenceThreshold 置信度阈值,低于此值的任务不会自动创建
     */
    suspend fun extractFromConversation(
        conversationText: String,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // 构建提取提示词
            val systemPrompt = buildExtractionPrompt()
            val userMessage = "请分析以下对话,提取任务和事件:\n\n$conversationText"

            // 调用DeepSeek API
            val chatRequest = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userMessage)
                ),
                temperature = 0.3f  // 降低温度,提高稳定性
            )

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = chatRequest
            )

            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "API调用失败: ${response.code()}")
                return@withContext ExtractionResult(
                    tasks = emptyList(),
                    events = emptyList(),
                    processingTime = System.currentTimeMillis() - startTime,
                    rawResponse = ""
                )
            }

            val aiResponse = response.body()!!.choices.firstOrNull()?.message?.content ?: ""
            Log.d(TAG, "AI响应: $aiResponse")

            // 解析AI响应
            val (tasks, events) = parseAIResponse(aiResponse, conversationText)

            // 过滤低置信度任务
            val filteredTasks = tasks.filter { it.confidence >= confidenceThreshold }
            val filteredEvents = events.filter { it.confidence >= confidenceThreshold }

            Log.d(TAG, "提取到 ${filteredTasks.size} 个任务, ${filteredEvents.size} 个事件")

            // 自动创建待办事项
            filteredTasks.forEach { task ->
                createTodoFromExtractedTask(task)
            }

            ExtractionResult(
                tasks = filteredTasks,
                events = filteredEvents,
                processingTime = System.currentTimeMillis() - startTime,
                rawResponse = aiResponse
            )
        } catch (e: Exception) {
            Log.e(TAG, "信息提取失败", e)
            ExtractionResult(
                tasks = emptyList(),
                events = emptyList(),
                processingTime = System.currentTimeMillis() - startTime,
                rawResponse = ""
            )
        }
    }

    /**
     * 构建信息提取的系统提示词
     */
    private fun buildExtractionPrompt(): String {
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            .format(Date())

        return """你是一个专业的任务和日程信息提取助手。请从对话中提取以下信息:

**当前时间**: $currentDate

**提取目标**:
1. 任务(作业、待办事项)
2. 事件(会议、活动、日程)

**输出格式**:
请以JSON格式输出,严格遵循以下结构:

```json
{
  "tasks": [
    {
      "title": "任务标题",
      "description": "详细描述",
      "dueDate": "截止日期(YYYY-MM-DD HH:mm格式,如果没有明确时间则为空字符串)",
      "priority": "low/medium/high",
      "relatedPerson": "相关人物姓名",
      "confidence": 0.95
    }
  ],
  "events": [
    {
      "title": "事件标题",
      "description": "详细描述",
      "startTime": "开始时间(YYYY-MM-DD HH:mm格式)",
      "endTime": "结束时间(YYYY-MM-DD HH:mm格式)",
      "location": "地点",
      "participants": ["参与者1", "参与者2"],
      "confidence": 0.90
    }
  ]
}
```

**时间解析规则**:
- "明天" → 明天同时刻
- "下周五" → 下周五
- "这周五" → 本周五
- "下个月15号" → 下个月15号
- "后天下午3点" → 后天15:00
- 如果没有明确时间,dueDate为空字符串

**置信度评分** (0.0-1.0):
- 0.9-1.0: 非常明确的任务/事件(如"明天交数学作业")
- 0.7-0.9: 较明确(如"这周要完成报告")
- 0.5-0.7: 不太明确(如"可能需要准备一下")
- 0.0-0.5: 模糊或不确定

**优先级判断**:
- high: 明确提到"紧急"、"重要"、"必须"、"deadline"等
- medium: 一般任务
- low: 可选、不紧急的任务

**注意**:
- 如果对话中没有明确的任务或事件,返回空数组
- 不要编造信息,只提取明确提到的内容
- 相关人物指的是布置任务的人或事件组织者
- 只返回JSON,不要有其他文字"""
    }

    /**
     * 解析AI返回的JSON响应
     */
    private fun parseAIResponse(
        aiResponse: String,
        sourceText: String
    ): Pair<List<ExtractedTask>, List<ExtractedEvent>> {
        try {
            // 提取JSON部分
            val jsonStr = extractJsonFromResponse(aiResponse)
            val jsonObject = JSONObject(jsonStr)

            // 解析任务
            val tasks = mutableListOf<ExtractedTask>()
            val tasksArray = jsonObject.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val taskJson = tasksArray.getJSONObject(i)
                    val task = parseExtractedTask(taskJson, sourceText)
                    if (task != null) {
                        tasks.add(task)
                    }
                }
            }

            // 解析事件
            val events = mutableListOf<ExtractedEvent>()
            val eventsArray = jsonObject.optJSONArray("events")
            if (eventsArray != null) {
                for (i in 0 until eventsArray.length()) {
                    val eventJson = eventsArray.getJSONObject(i)
                    val event = parseExtractedEvent(eventJson, sourceText)
                    if (event != null) {
                        events.add(event)
                    }
                }
            }

            return Pair(tasks, events)
        } catch (e: Exception) {
            Log.e(TAG, "解析AI响应失败", e)
            return Pair(emptyList(), emptyList())
        }
    }

    /**
     * 从AI响应中提取JSON
     */
    private fun extractJsonFromResponse(response: String): String {
        // 尝试直接解析
        if (response.trim().startsWith("{")) {
            return response.trim()
        }

        // 提取markdown代码块中的JSON
        val codeBlockRegex = "```(?:json)?\\s*([\\s\\S]*?)```".toRegex()
        val match = codeBlockRegex.find(response)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // 查找第一个{到最后一个}
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return response.substring(firstBrace, lastBrace + 1)
        }

        return "{}"
    }

    /**
     * 解析单个提取的任务
     */
    private fun parseExtractedTask(json: JSONObject, sourceText: String): ExtractedTask? {
        try {
            val title = json.optString("title", "").trim()
            if (title.isEmpty()) return null

            val dueDateStr = json.optString("dueDate", "")
            val dueDate = if (dueDateStr.isNotEmpty()) {
                timeParser.parseDateTime(dueDateStr)
            } else {
                0L
            }

            val priorityStr = json.optString("priority", "medium").lowercase()
            val priority = when (priorityStr) {
                "high" -> TodoPriority.HIGH
                "low" -> TodoPriority.LOW
                else -> TodoPriority.MEDIUM
            }

            return ExtractedTask(
                title = title,
                description = json.optString("description", ""),
                dueDate = dueDate,
                priority = priority,
                relatedPersonId = json.optString("relatedPerson", ""),
                sourceText = sourceText,
                confidence = json.optDouble("confidence", 0.5).toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析任务失败", e)
            return null
        }
    }

    /**
     * 解析单个提取的事件
     */
    private fun parseExtractedEvent(json: JSONObject, sourceText: String): ExtractedEvent? {
        try {
            val title = json.optString("title", "").trim()
            if (title.isEmpty()) return null

            val startTimeStr = json.optString("startTime", "")
            if (startTimeStr.isEmpty()) return null

            val startTime = timeParser.parseDateTime(startTimeStr)
            if (startTime == 0L) return null

            val endTimeStr = json.optString("endTime", "")
            val endTime = if (endTimeStr.isNotEmpty()) {
                timeParser.parseDateTime(endTimeStr)
            } else {
                startTime + 3600000  // 默认1小时
            }

            val participantsArray = json.optJSONArray("participants")
            val participants = mutableListOf<String>()
            if (participantsArray != null) {
                for (i in 0 until participantsArray.length()) {
                    participants.add(participantsArray.getString(i))
                }
            }

            return ExtractedEvent(
                title = title,
                description = json.optString("description", ""),
                startTime = startTime,
                endTime = endTime,
                location = json.optString("location", ""),
                participants = participants,
                sourceText = sourceText,
                confidence = json.optDouble("confidence", 0.5).toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析事件失败", e)
            return null
        }
    }

    /**
     * 从提取的任务创建待办事项
     */
    private suspend fun createTodoFromExtractedTask(task: ExtractedTask) {
        try {
            val todoItem = TodoItem(
                id = UUID.randomUUID().toString(),
                title = task.title,
                description = task.description,
                dueDate = task.dueDate,
                isCompleted = false,
                priority = task.priority,
                relatedPersonId = task.relatedPersonId,
                sourceText = task.sourceText,
                confidence = task.confidence,
                isAutoCreated = true,
                addedToCalendar = false
            )

            todoRepository.addTodo(todoItem)
            Log.d(TAG, "自动创建待办: ${todoItem.title} (置信度: ${todoItem.confidence})")
        } catch (e: Exception) {
            Log.e(TAG, "创建待办失败", e)
        }
    }
}
