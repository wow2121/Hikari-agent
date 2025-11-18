package com.xiaoguang.assistant.domain.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaoguang.assistant.data.remote.dto.Tool
import com.xiaoguang.assistant.data.remote.dto.FunctionDefinition
import com.xiaoguang.assistant.domain.model.CalendarEvent
import com.xiaoguang.assistant.domain.model.TodoItem
import com.xiaoguang.assistant.domain.model.TodoPriority
import com.xiaoguang.assistant.domain.repository.CalendarRepository
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.xiaoguang.assistant.domain.repository.TodoRepository
import com.xiaoguang.assistant.domain.usecase.MemoryManagementUseCase
import com.xiaoguang.assistant.domain.usecase.SemanticSearchUseCase
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP服务器 - 为AI提供待办、日程和记忆管理工具
 */
@Singleton
class McpServer @Inject constructor(
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val semanticSearchUseCase: SemanticSearchUseCase,
    private val memoryManagementUseCase: MemoryManagementUseCase
) {
    private val tools = mutableMapOf<String, McpTool>()

    init {
        registerTools()
    }

    /**
     * 注册所有MCP工具
     */
    private fun registerTools() {
        // 系统工具（时间获取等）
        registerSystemTools()

        // 待办管理工具
        registerTodoTools()

        // 日程管理工具
        registerCalendarTools()

        // 记忆管理工具
        registerMemoryTools()
    }

    /**
     * 注册系统工具（时间相关）
     */
    private fun registerSystemTools() {
        // 获取当前时间
        tools["get_current_time"] = McpTool(
            name = "get_current_time",
            description = "获取当前的日期和时间，包括年月日、星期、时分秒。当用户询问现在几点、今天星期几、今天日期等问题时使用。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "format": {
                      "type": "string",
                      "enum": ["full", "date", "time", "datetime"],
                      "description": "返回格式: full-完整信息, date-仅日期, time-仅时间, datetime-日期+时间"
                    }
                  }
                }
            """).asJsonObject,
            handler = { args -> handleGetCurrentTime(args) }
        )

        // 计算时间差
        tools["calculate_time_diff"] = McpTool(
            name = "calculate_time_diff",
            description = "计算两个时间之间的差值，返回相差的天数、小时数等。用于回答'还有多久'、'过了多长时间'等问题。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "startTime": {"type": "string", "description": "开始时间，格式: yyyy-MM-dd HH:mm"},
                    "endTime": {"type": "string", "description": "结束时间，格式: yyyy-MM-dd HH:mm"}
                  },
                  "required": ["startTime", "endTime"]
                }
            """).asJsonObject,
            handler = { args -> handleCalculateTimeDiff(args) }
        )

        // 时区转换
        tools["convert_timezone"] = McpTool(
            name = "convert_timezone",
            description = "转换时区。用于回答不同时区的时间问题。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "time": {"type": "string", "description": "时间，格式: yyyy-MM-dd HH:mm"},
                    "fromZone": {"type": "string", "description": "源时区，如'Asia/Shanghai'"},
                    "toZone": {"type": "string", "description": "目标时区，如'America/New_York'"}
                  },
                  "required": ["time", "fromZone", "toZone"]
                }
            """).asJsonObject,
            handler = { args -> handleConvertTimezone(args) }
        )
    }

    /**
     * 注册待办管理工具
     */
    private fun registerTodoTools() {
        // 添加待办
        tools["add_todo"] = McpTool(
            name = "add_todo",
            description = "添加新的待办事项。当用户提到需要做某事、记录任务时使用。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "待办标题"},
                    "description": {"type": "string", "description": "详细描述"},
                    "dueDate": {"type": "string", "description": "截止日期,格式:yyyy-MM-dd HH:mm"},
                    "priority": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"], "description": "优先级"}
                  },
                  "required": ["title"]
                }
            """).asJsonObject,
            handler = { args -> handleAddTodo(args) }
        )

        // 删除待办
        tools["delete_todo"] = McpTool(
            name = "delete_todo",
            description = "删除指定的待办事项。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "todoId": {"type": "string", "description": "待办ID"}
                  },
                  "required": ["todoId"]
                }
            """).asJsonObject,
            handler = { args -> handleDeleteTodo(args) }
        )

        // 查看待办列表
        tools["list_todos"] = McpTool(
            name = "list_todos",
            description = "查看待办列表。可以查看全部、未完成或已完成的待办。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "filter": {"type": "string", "enum": ["all", "active", "completed"], "description": "筛选条件"}
                  }
                }
            """).asJsonObject,
            handler = { args -> handleListTodos(args) }
        )

        // 更新待办状态
        tools["update_todo_status"] = McpTool(
            name = "update_todo_status",
            description = "标记待办为完成或未完成。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "todoId": {"type": "string", "description": "待办ID"},
                    "completed": {"type": "boolean", "description": "是否完成"}
                  },
                  "required": ["todoId", "completed"]
                }
            """).asJsonObject,
            handler = { args -> handleUpdateTodoStatus(args) }
        )

        // 完整更新待办
        tools["update_todo"] = McpTool(
            name = "update_todo",
            description = "完整更新待办事项的信息,包括标题、描述、截止时间、优先级。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "todoId": {"type": "string", "description": "待办ID"},
                    "title": {"type": "string", "description": "新标题"},
                    "description": {"type": "string", "description": "新描述"},
                    "dueDate": {"type": "string", "description": "新截止日期,格式:yyyy-MM-dd HH:mm"},
                    "priority": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"], "description": "新优先级"}
                  },
                  "required": ["todoId"]
                }
            """).asJsonObject,
            handler = { args -> handleUpdateTodo(args) }
        )

        // 搜索待办
        tools["search_todos"] = McpTool(
            name = "search_todos",
            description = "按关键词搜索待办事项,支持搜索标题和描述。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词"}
                  },
                  "required": ["keyword"]
                }
            """).asJsonObject,
            handler = { args -> handleSearchTodos(args) }
        )

        // 获取即将到期的待办
        tools["get_upcoming_todos"] = McpTool(
            name = "get_upcoming_todos",
            description = "获取即将到期的待办事项(未来7天内)。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "days": {"type": "number", "description": "未来多少天内,默认7天"}
                  }
                }
            """).asJsonObject,
            handler = { args -> handleGetUpcomingTodos(args) }
        )

        // 获取已过期的待办
        tools["get_overdue_todos"] = McpTool(
            name = "get_overdue_todos",
            description = "获取已经过期但未完成的待办事项。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleGetOverdueTodos(args) }
        )

        // 按优先级获取待办
        tools["get_todos_by_priority"] = McpTool(
            name = "get_todos_by_priority",
            description = "按优先级筛选待办事项。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "priority": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"], "description": "优先级"}
                  },
                  "required": ["priority"]
                }
            """).asJsonObject,
            handler = { args -> handleGetTodosByPriority(args) }
        )

        // 获取待办统计信息
        tools["get_todo_statistics"] = McpTool(
            name = "get_todo_statistics",
            description = "获取待办事项的统计信息,包括总数、已完成数、各优先级数量等。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleGetTodoStatistics(args) }
        )
    }

    /**
     * 注册日程管理工具
     */
    private fun registerCalendarTools() {
        // 添加日程
        tools["add_calendar_event"] = McpTool(
            name = "add_calendar_event",
            description = "添加日程到日历。当用户提到会议、活动、约会等需要记录时间的事件时使用。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "日程标题"},
                    "description": {"type": "string", "description": "详细描述"},
                    "startTime": {"type": "string", "description": "开始时间,格式:yyyy-MM-dd HH:mm"},
                    "endTime": {"type": "string", "description": "结束时间,格式:yyyy-MM-dd HH:mm"},
                    "location": {"type": "string", "description": "地点"}
                  },
                  "required": ["title", "startTime"]
                }
            """).asJsonObject,
            handler = { args -> handleAddCalendarEvent(args) }
        )

        // 删除日程
        tools["delete_calendar_event"] = McpTool(
            name = "delete_calendar_event",
            description = "删除指定的日程。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "eventId": {"type": "number", "description": "日程ID"}
                  },
                  "required": ["eventId"]
                }
            """).asJsonObject,
            handler = { args -> handleDeleteCalendarEvent(args) }
        )

        // 查看今日日程
        tools["list_today_events"] = McpTool(
            name = "list_today_events",
            description = "查看今天的所有日程安排。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleListTodayEvents(args) }
        )

        // 查看本月日程
        tools["list_month_events"] = McpTool(
            name = "list_month_events",
            description = "查看指定月份的所有日程。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "year": {"type": "number", "description": "年份"},
                    "month": {"type": "number", "description": "月份(1-12)"}
                  }
                }
            """).asJsonObject,
            handler = { args -> handleListMonthEvents(args) }
        )

        // 更新日程
        tools["update_calendar_event"] = McpTool(
            name = "update_calendar_event",
            description = "完整更新日程信息,包括标题、时间、地点等。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "eventId": {"type": "number", "description": "日程ID"},
                    "title": {"type": "string", "description": "新标题"},
                    "description": {"type": "string", "description": "新描述"},
                    "startTime": {"type": "string", "description": "新开始时间,格式:yyyy-MM-dd HH:mm"},
                    "endTime": {"type": "string", "description": "新结束时间,格式:yyyy-MM-dd HH:mm"},
                    "location": {"type": "string", "description": "新地点"}
                  },
                  "required": ["eventId"]
                }
            """).asJsonObject,
            handler = { args -> handleUpdateCalendarEvent(args) }
        )

        // 搜索日程
        tools["search_calendar_events"] = McpTool(
            name = "search_calendar_events",
            description = "按关键词搜索日程,支持搜索标题、描述、地点。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词"}
                  },
                  "required": ["keyword"]
                }
            """).asJsonObject,
            handler = { args -> handleSearchCalendarEvents(args) }
        )

        // 按日期范围获取日程
        tools["get_events_by_date_range"] = McpTool(
            name = "get_events_by_date_range",
            description = "获取指定日期范围内的所有日程。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "startDate": {"type": "string", "description": "开始日期,格式:yyyy-MM-dd"},
                    "endDate": {"type": "string", "description": "结束日期,格式:yyyy-MM-dd"}
                  },
                  "required": ["startDate", "endDate"]
                }
            """).asJsonObject,
            handler = { args -> handleGetEventsByDateRange(args) }
        )

        // 检测时间冲突
        tools["get_conflicting_events"] = McpTool(
            name = "get_conflicting_events",
            description = "检测指定时间段内是否有冲突的日程安排。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {
                    "startTime": {"type": "string", "description": "开始时间,格式:yyyy-MM-dd HH:mm"},
                    "endTime": {"type": "string", "description": "结束时间,格式:yyyy-MM-dd HH:mm"}
                  },
                  "required": ["startTime", "endTime"]
                }
            """).asJsonObject,
            handler = { args -> handleGetConflictingEvents(args) }
        )

        // 获取日程统计
        tools["get_calendar_statistics"] = McpTool(
            name = "get_calendar_statistics",
            description = "获取日程的统计信息,包括总数、本周数量、本月数量等。",
            inputSchema = JsonParser.parseString("""
                {
                  "type": "object",
                  "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleGetCalendarStatistics(args) }
        )
    }

    /**
     * 获取所有可用工具
     */
    fun getAvailableTools(): List<McpTool> = tools.values.toList()

    /**
     * 获取SiliconFlow格式的工具列表 (用于官方Function Calling)
     */
    fun getSiliconFlowTools(): List<Tool> {
        val gson = Gson()
        return tools.values.map { mcpTool ->
            // 将JsonObject转换为Map
            val parametersMap: Map<String, Any> = gson.fromJson(
                mcpTool.inputSchema.toString(),
                Map::class.java
            ) as Map<String, Any>

            Tool(
                type = "function",
                function = FunctionDefinition(
                    name = mcpTool.name,
                    description = mcpTool.description,
                    parameters = parametersMap
                )
            )
        }
    }

    /**
     * 调用工具
     */
    suspend fun callTool(name: String, arguments: JsonObject): McpToolResult {
        val tool = tools[name] ?: return McpToolResult(
            success = false,
            content = "未找到工具: $name"
        )

        return try {
            tool.handler(arguments)
        } catch (e: Exception) {
            Timber.e(e, "调用工具失败: $name")
            McpToolResult(
                success = false,
                content = "工具调用失败: ${e.message}"
            )
        }
    }

    // ========== 待办工具处理器 ==========

    private suspend fun handleAddTodo(args: JsonObject): McpToolResult {
        val title = args.get("title").asString
        val description = args.get("description")?.asString ?: ""
        val dueDateStr = args.get("dueDate")?.asString
        val priorityStr = args.get("priority")?.asString ?: "MEDIUM"

        val dueDate = dueDateStr?.let { parseDateTime(it) }
        val priority = when (priorityStr.uppercase()) {
            "HIGH" -> TodoPriority.HIGH
            "LOW" -> TodoPriority.LOW
            else -> TodoPriority.MEDIUM
        }

        val todo = TodoItem(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            dueDate = dueDate ?: 0L,
            priority = priority,
            isCompleted = false,
            isAutoCreated = false,
            createdAt = System.currentTimeMillis()
        )

        todoRepository.addTodo(todo)

        return McpToolResult(
            success = true,
            content = "已添加待办: $title${dueDate?.let { "\n截止时间: ${formatDateTime(it)}" } ?: ""}",
            data = todo
        )
    }

    private suspend fun handleDeleteTodo(args: JsonObject): McpToolResult {
        val todoId = args.get("todoId").asString
        val todo = todoRepository.getTodoById(todoId)

        if (todo == null) {
            return McpToolResult(
                success = false,
                content = "未找到待办: $todoId"
            )
        }

        todoRepository.deleteTodo(todoId)

        return McpToolResult(
            success = true,
            content = "已删除待办: ${todo.title}"
        )
    }

    private suspend fun handleListTodos(args: JsonObject): McpToolResult {
        val filter = args.get("filter")?.asString ?: "all"

        val todos = when (filter) {
            "active" -> todoRepository.getIncompleteTodos()
            "completed" -> todoRepository.getAllTodos().filter { it.isCompleted }
            else -> todoRepository.getAllTodos()
        }

        val content = if (todos.isEmpty()) {
            "暂无待办事项"
        } else {
            "待办列表 (共${todos.size}项):\n\n" + todos.joinToString("\n") { todo ->
                val status = if (todo.isCompleted) "[✓]" else "[ ]"
                val priority = when (todo.priority) {
                    TodoPriority.HIGH -> "[高]"
                    TodoPriority.LOW -> "[低]"
                    else -> ""
                }
                val dueDate = todo.dueDate?.let { " - ${formatDateTime(it)}" } ?: ""
                "$status $priority ${todo.title}$dueDate"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = todos
        )
    }

    private suspend fun handleUpdateTodoStatus(args: JsonObject): McpToolResult {
        val todoId = args.get("todoId").asString
        val completed = args.get("completed").asBoolean

        val todo = todoRepository.getTodoById(todoId)
        if (todo == null) {
            return McpToolResult(
                success = false,
                content = "未找到待办: $todoId"
            )
        }

        todoRepository.markAsCompleted(todoId, completed)

        return McpToolResult(
            success = true,
            content = "已${if (completed) "完成" else "取消完成"}: ${todo.title}"
        )
    }

    // ========== 日程工具处理器 ==========

    private suspend fun handleAddCalendarEvent(args: JsonObject): McpToolResult {
        val title = args.get("title").asString
        val description = args.get("description")?.asString ?: ""
        val startTimeStr = args.get("startTime").asString
        val endTimeStr = args.get("endTime")?.asString
        val location = args.get("location")?.asString ?: ""

        val startTime = parseDateTime(startTimeStr)
            ?: return McpToolResult(false, "开始时间格式错误")

        val endTime = endTimeStr?.let { parseDateTime(it) } ?: (startTime + 3600000) // 默认1小时

        val event = CalendarEvent(
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            location = location
        )

        val eventId = calendarRepository.createEvent(event)

        if (eventId <= 0) {
            return McpToolResult(
                success = false,
                content = "添加日程失败,请检查日历权限"
            )
        }

        return McpToolResult(
            success = true,
            content = "已添加日程: $title\n时间: ${formatDateTime(startTime)}${if (location.isNotEmpty()) "\n地点: $location" else ""}",
            data = event.copy(id = eventId)
        )
    }

    private suspend fun handleDeleteCalendarEvent(args: JsonObject): McpToolResult {
        val eventId = args.get("eventId").asLong

        calendarRepository.deleteEvent(eventId)

        return McpToolResult(
            success = true,
            content = "已删除日程"
        )
    }

    private suspend fun handleListTodayEvents(args: JsonObject): McpToolResult {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        val startOfDay = today.timeInMillis

        today.set(Calendar.HOUR_OF_DAY, 23)
        today.set(Calendar.MINUTE, 59)
        today.set(Calendar.SECOND, 59)
        val endOfDay = today.timeInMillis

        val events = calendarRepository.getEventsBetween(startOfDay, endOfDay)

        val content = if (events.isEmpty()) {
            "今天暂无日程安排"
        } else {
            "今日日程 (共${events.size}项):\n\n" + events.sortedBy { it.startTime }.joinToString("\n") { event ->
                "${formatTime(event.startTime)} ${event.title}${if (event.location.isNotEmpty()) " @ ${event.location}" else ""}"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = events
        )
    }

    private suspend fun handleListMonthEvents(args: JsonObject): McpToolResult {
        val calendar = Calendar.getInstance()
        val year = args.get("year")?.asInt ?: calendar.get(Calendar.YEAR)
        val month = args.get("month")?.asInt ?: (calendar.get(Calendar.MONTH) + 1)

        calendar.set(year, month - 1, 1, 0, 0, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfMonth = calendar.timeInMillis

        val events = calendarRepository.getEventsBetween(startOfMonth, endOfMonth)

        val content = if (events.isEmpty()) {
            "${year}年${month}月暂无日程"
        } else {
            "${year}年${month}月日程 (共${events.size}项):\n\n" + events.sortedBy { it.startTime }.joinToString("\n") { event ->
                "${formatDate(event.startTime)} ${formatTime(event.startTime)} ${event.title}"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = events
        )
    }

    // ========== 新增待办工具处理器 ==========

    private suspend fun handleUpdateTodo(args: JsonObject): McpToolResult {
        val todoId = args.get("todoId").asString
        val title = args.get("title")?.asString
        val description = args.get("description")?.asString
        val dueDateStr = args.get("dueDate")?.asString
        val priorityStr = args.get("priority")?.asString

        val todo = todoRepository.getTodoById(todoId)
        if (todo == null) {
            return McpToolResult(
                success = false,
                content = "未找到待办: $todoId"
            )
        }

        // 解析优先级
        val priority = when (priorityStr?.lowercase()) {
            "high", "高" -> TodoPriority.HIGH
            "medium", "中" -> TodoPriority.MEDIUM
            "low", "低" -> TodoPriority.LOW
            else -> todo.priority
        }

        // 解析截止时间
        val dueDate = dueDateStr?.let { parseDateTime(it) } ?: todo.dueDate

        // 创建更新后的待办
        val updatedTodo = todo.copy(
            title = title ?: todo.title,
            description = description ?: todo.description,
            dueDate = dueDate,
            priority = priority
        )

        todoRepository.addTodo(updatedTodo)

        return McpToolResult(
            success = true,
            content = "已更新待办: ${updatedTodo.title}",
            data = updatedTodo
        )
    }

    private suspend fun handleSearchTodos(args: JsonObject): McpToolResult {
        val query = args.get("keyword").asString.lowercase()

        val allTodos = todoRepository.getAllTodos()
        val matchedTodos = allTodos.filter { todo ->
            todo.title.lowercase().contains(query) ||
            (todo.description?.lowercase()?.contains(query) == true)
        }

        val content = if (matchedTodos.isEmpty()) {
            "未找到包含 \"$query\" 的待办"
        } else {
            "找到 ${matchedTodos.size} 个相关待办:\n\n" + matchedTodos.joinToString("\n") { todo ->
                val status = if (todo.isCompleted) "[✓]" else "[ ]"
                val priority = when (todo.priority) {
                    TodoPriority.HIGH -> "[高]"
                    TodoPriority.LOW -> "[低]"
                    else -> ""
                }
                val dueDate = todo.dueDate?.let { " - ${formatDateTime(it)}" } ?: ""
                "$status $priority ${todo.title}$dueDate"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = matchedTodos
        )
    }

    private suspend fun handleGetUpcomingTodos(args: JsonObject): McpToolResult {
        val days = args.get("days")?.asInt ?: 7

        val now = System.currentTimeMillis()
        val futureTime = now + (days * 24 * 60 * 60 * 1000L)

        val allTodos = todoRepository.getIncompleteTodos()
        val upcomingTodos = allTodos.filter { todo ->
            val dueDate = todo.dueDate ?: return@filter false
            dueDate in now..futureTime
        }.sortedBy { it.dueDate }

        val content = if (upcomingTodos.isEmpty()) {
            "未来${days}天内没有待办事项"
        } else {
            "未来${days}天待办 (共${upcomingTodos.size}项):\n\n" + upcomingTodos.joinToString("\n") { todo ->
                val priority = when (todo.priority) {
                    TodoPriority.HIGH -> "[高]"
                    TodoPriority.LOW -> "[低]"
                    else -> ""
                }
                val dueDate = todo.dueDate?.let { formatDateTime(it) } ?: ""
                "$priority ${todo.title} - $dueDate"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = upcomingTodos
        )
    }

    private suspend fun handleGetOverdueTodos(args: JsonObject): McpToolResult {
        val now = System.currentTimeMillis()

        val allTodos = todoRepository.getIncompleteTodos()
        val overdueTodos = allTodos.filter { todo ->
            val dueDate = todo.dueDate ?: return@filter false
            dueDate < now
        }.sortedBy { it.dueDate }

        val content = if (overdueTodos.isEmpty()) {
            "没有逾期的待办"
        } else {
            "逾期待办 (共${overdueTodos.size}项):\n\n" + overdueTodos.joinToString("\n") { todo ->
                val priority = when (todo.priority) {
                    TodoPriority.HIGH -> "[高]"
                    TodoPriority.LOW -> "[低]"
                    else -> ""
                }
                val dueDate = todo.dueDate?.let { formatDateTime(it) } ?: ""
                val daysOverdue = ((now - (todo.dueDate ?: now)) / (24 * 60 * 60 * 1000)).toInt()
                "$priority ${todo.title} - $dueDate (逾期${daysOverdue}天)"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = overdueTodos
        )
    }

    private suspend fun handleGetTodosByPriority(args: JsonObject): McpToolResult {
        val priorityStr = args.get("priority").asString.lowercase()

        val priority = when (priorityStr) {
            "high", "高" -> TodoPriority.HIGH
            "medium", "中" -> TodoPriority.MEDIUM
            "low", "低" -> TodoPriority.LOW
            else -> return McpToolResult(
                success = false,
                content = "无效的优先级: $priorityStr"
            )
        }

        val allTodos = todoRepository.getAllTodos()
        val filteredTodos = allTodos.filter { it.priority == priority && !it.isCompleted }
            .sortedBy { it.dueDate }

        val priorityLabel = when (priority) {
            TodoPriority.HIGH -> "高优先级"
            TodoPriority.MEDIUM -> "中优先级"
            TodoPriority.LOW -> "低优先级"
        }

        val content = if (filteredTodos.isEmpty()) {
            "没有${priorityLabel}的待办"
        } else {
            "${priorityLabel}待办 (共${filteredTodos.size}项):\n\n" + filteredTodos.joinToString("\n") { todo ->
                val dueDate = todo.dueDate?.let { " - ${formatDateTime(it)}" } ?: ""
                "${todo.title}$dueDate"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = filteredTodos
        )
    }

    private suspend fun handleGetTodoStatistics(args: JsonObject): McpToolResult {
        val allTodos = todoRepository.getAllTodos()

        val total = allTodos.size
        val completed = allTodos.count { it.isCompleted }
        val active = allTodos.count { !it.isCompleted }
        val highPriority = allTodos.count { it.priority == TodoPriority.HIGH && !it.isCompleted }
        val autoCreated = allTodos.count { it.isAutoCreated }

        val now = System.currentTimeMillis()
        val overdue = allTodos.count { todo ->
            !todo.isCompleted && (todo.dueDate ?: Long.MAX_VALUE) < now
        }

        val content = """
            待办统计:
            - 总数: $total
            - 已完成: $completed
            - 进行中: $active
            - 高优先级: $highPriority
            - 逾期: $overdue
            - AI创建: $autoCreated
            - 完成率: ${if (total > 0) String.format("%.1f%%", completed * 100.0 / total) else "0%"}
        """.trimIndent()

        return McpToolResult(
            success = true,
            content = content,
            data = mapOf(
                "total" to total,
                "completed" to completed,
                "active" to active,
                "highPriority" to highPriority,
                "overdue" to overdue,
                "autoCreated" to autoCreated
            )
        )
    }

    // ========== 新增日程工具处理器 ==========

    private suspend fun handleUpdateCalendarEvent(args: JsonObject): McpToolResult {
        val eventId = args.get("eventId").asLong
        val title = args.get("title")?.asString
        val description = args.get("description")?.asString
        val startTimeStr = args.get("startTime")?.asString
        val endTimeStr = args.get("endTime")?.asString
        val location = args.get("location")?.asString

        // 获取原有事件
        val events = calendarRepository.getEventsBetween(0, Long.MAX_VALUE)
        val originalEvent = events.find { it.id == eventId }

        if (originalEvent == null) {
            return McpToolResult(
                success = false,
                content = "未找到日程: $eventId"
            )
        }

        // 解析时间
        val startTime = startTimeStr?.let { parseDateTime(it) } ?: originalEvent.startTime
        val endTime = endTimeStr?.let { parseDateTime(it) } ?: originalEvent.endTime

        // 创建更新后的事件
        val updatedEvent = CalendarEvent(
            id = eventId,
            title = title ?: originalEvent.title,
            description = description ?: originalEvent.description,
            startTime = startTime,
            endTime = endTime,
            location = location ?: originalEvent.location
        )

        // 先删除旧事件,再创建新事件
        calendarRepository.deleteEvent(eventId)
        val newEventId = calendarRepository.createEvent(updatedEvent)

        if (newEventId <= 0) {
            return McpToolResult(
                success = false,
                content = "更新日程失败,请检查日历权限"
            )
        }

        return McpToolResult(
            success = true,
            content = "已更新日程: ${updatedEvent.title}",
            data = updatedEvent
        )
    }

    private suspend fun handleSearchCalendarEvents(args: JsonObject): McpToolResult {
        val query = args.get("keyword").asString.lowercase()

        val allEvents = calendarRepository.getEventsBetween(0, Long.MAX_VALUE)
        val matchedEvents = allEvents.filter { event ->
            event.title.lowercase().contains(query) ||
            event.description.lowercase().contains(query) ||
            event.location.lowercase().contains(query)
        }.sortedBy { it.startTime }

        val content = if (matchedEvents.isEmpty()) {
            "未找到包含 \"$query\" 的日程"
        } else {
            "找到 ${matchedEvents.size} 个相关日程:\n\n" + matchedEvents.joinToString("\n") { event ->
                val time = "${formatDate(event.startTime)} ${formatTime(event.startTime)}"
                val location = if (event.location.isNotEmpty()) " @ ${event.location}" else ""
                "$time ${event.title}$location"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = matchedEvents
        )
    }

    private suspend fun handleGetEventsByDateRange(args: JsonObject): McpToolResult {
        val startDateStr = args.get("startDate").asString
        val endDateStr = args.get("endDate").asString

        val startTime = parseDateTime(startDateStr)
            ?: return McpToolResult(false, "开始日期格式错误: $startDateStr")

        val endTime = parseDateTime(endDateStr)
            ?: return McpToolResult(false, "结束日期格式错误: $endDateStr")

        val events = calendarRepository.getEventsBetween(startTime, endTime)
            .sortedBy { it.startTime }

        val content = if (events.isEmpty()) {
            "在 ${formatDate(startTime)} 到 ${formatDate(endTime)} 期间没有日程"
        } else {
            "在 ${formatDate(startTime)} 到 ${formatDate(endTime)} 的日程 (共${events.size}项):\n\n" +
            events.joinToString("\n") { event ->
                val time = "${formatDate(event.startTime)} ${formatTime(event.startTime)}"
                val location = if (event.location.isNotEmpty()) " @ ${event.location}" else ""
                "$time ${event.title}$location"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = events
        )
    }

    private suspend fun handleGetConflictingEvents(args: JsonObject): McpToolResult {
        val startTimeStr = args.get("startTime").asString
        val endTimeStr = args.get("endTime").asString

        val startTime = parseDateTime(startTimeStr)
            ?: return McpToolResult(false, "开始时间格式错误")

        val endTime = parseDateTime(endTimeStr)
            ?: return McpToolResult(false, "结束时间格式错误")

        // 获取所有事件
        val allEvents = calendarRepository.getEventsBetween(0, Long.MAX_VALUE)

        // 查找时间冲突的事件
        val conflictingEvents = allEvents.filter { event ->
            // 检查时间段是否重叠
            !(event.endTime <= startTime || event.startTime >= endTime)
        }.sortedBy { it.startTime }

        val content = if (conflictingEvents.isEmpty()) {
            "在 ${formatDateTime(startTime)} 到 ${formatDateTime(endTime)} 期间没有时间冲突"
        } else {
            "发现 ${conflictingEvents.size} 个时间冲突的日程:\n\n" +
            conflictingEvents.joinToString("\n") { event ->
                val time = "${formatDateTime(event.startTime)} - ${formatTime(event.endTime)}"
                val location = if (event.location.isNotEmpty()) " @ ${event.location}" else ""
                "$time ${event.title}$location"
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = conflictingEvents
        )
    }

    private suspend fun handleGetCalendarStatistics(args: JsonObject): McpToolResult {
        val now = System.currentTimeMillis()

        // 获取今天的日程
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfToday = calendar.timeInMillis

        val todayEvents = calendarRepository.getEventsBetween(startOfToday, endOfToday)

        // 获取本周的日程
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfWeek = calendar.timeInMillis

        val weekEvents = calendarRepository.getEventsBetween(startOfWeek, endOfWeek)

        // 获取本月的日程
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfMonth = calendar.timeInMillis

        val monthEvents = calendarRepository.getEventsBetween(startOfMonth, endOfMonth)

        // 统计已过期的日程
        val pastEvents = calendarRepository.getEventsBetween(0, now).filter { it.endTime < now }

        val content = """
            日程统计:
            - 今日日程: ${todayEvents.size}
            - 本周日程: ${weekEvents.size}
            - 本月日程: ${monthEvents.size}
            - 已过期: ${pastEvents.size}
        """.trimIndent()

        return McpToolResult(
            success = true,
            content = content,
            data = mapOf(
                "today" to todayEvents.size,
                "thisWeek" to weekEvents.size,
                "thisMonth" to monthEvents.size,
                "past" to pastEvents.size
            )
        )
    }

    // ========== 辅助方法 ==========

    private fun parseDateTime(dateTimeStr: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            format.parse(dateTimeStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        return format.format(Date(timestamp))
    }

    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("MM-dd", Locale.CHINA)
        return format.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.CHINA)
        return format.format(Date(timestamp))
    }

    // ========== 记忆管理工具 ==========

    /**
     * 注册记忆管理工具
     */
    private fun registerMemoryTools() {
        // 搜索记忆
        tools["search_memory"] = McpTool(
            name = "search_memory",
            description = "搜索相关的记忆和过往对话。当用户询问之前聊过的内容或需要回忆某些信息时使用。",
            inputSchema = JsonParser.parseString("""
                {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "搜索查询"},
                        "type": {"type": "string", "enum": ["all", "conversation", "memory"], "description": "搜索类型"}
                    },
                    "required": ["query"]
                }
            """).asJsonObject,
            handler = { args -> handleSearchMemory(args) }
        )

        // 获取记忆健康报告
        tools["get_memory_health"] = McpTool(
            name = "get_memory_health",
            description = "获取记忆系统的健康状态报告。",
            inputSchema = JsonParser.parseString("""
                {
                    "type": "object",
                    "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleGetMemoryHealth(args) }
        )

        // 获取记忆统计
        tools["get_memory_stats"] = McpTool(
            name = "get_memory_stats",
            description = "获取记忆系统的详细统计信息。",
            inputSchema = JsonParser.parseString("""
                {
                    "type": "object",
                    "properties": {}
                }
            """).asJsonObject,
            handler = { args -> handleGetMemoryStats(args) }
        )
    }

    // ========== 记忆工具处理器 ==========

    private suspend fun handleSearchMemory(args: JsonObject): McpToolResult {
        val query = args.get("query").asString
        val type = args.get("type")?.asString ?: "all"

        return try {
            val result = when (type) {
                "conversation" -> {
                    val searchResult = semanticSearchUseCase.searchConversations(query, topK = 5)
                    if (searchResult.isFailure) {
                        return McpToolResult(false, "搜索对话失败: ${searchResult.exceptionOrNull()?.message}")
                    }

                    val conversations = searchResult.getOrNull()!!
                    if (conversations.isEmpty()) {
                        "没有找到相关的对话历史"
                    } else {
                        "找到 ${conversations.size} 条相关对话:\n\n" +
                        conversations.take(3).joinToString("\n\n") { result ->
                            val entity = result.entity
                            val role = "用户" // 默认角色
                            val date = formatDate(entity.timestamp)
                            "$role ($date): ${entity.content.take(200)}"
                        }
                    }
                }

                "memory" -> {
                    val searchResult = semanticSearchUseCase.searchMemoryFacts(query, topK = 5)
                    if (searchResult.isFailure) {
                        return McpToolResult(false, "搜索记忆失败: ${searchResult.exceptionOrNull()?.message}")
                    }

                    val memories = searchResult.getOrNull()!!
                    if (memories.isEmpty()) {
                        "没有找到相关的记忆"
                    } else {
                        "找到 ${memories.size} 条相关记忆:\n\n" +
                        memories.take(3).joinToString("\n") { result ->
                            val fact = result.entity
                            "- [${fact.category}] ${fact.content}"
                        }
                    }
                }

                else -> { // "all"
                    val hybridResult = semanticSearchUseCase.hybridSearch(query, 3, 3)
                    if (hybridResult.isFailure) {
                        return McpToolResult(false, "搜索失败: ${hybridResult.exceptionOrNull()?.message}")
                    }

                    val hybrid = hybridResult.getOrNull()!!
                    val contextPrompt = semanticSearchUseCase.buildContextPrompt(hybrid, 1000)

                    if (contextPrompt.isEmpty()) {
                        "没有找到相关的记忆或对话"
                    } else {
                        contextPrompt
                    }
                }
            }

            McpToolResult(
                success = true,
                content = result
            )

        } catch (e: Exception) {
            Timber.e(e, "搜索记忆失败")
            McpToolResult(
                success = false,
                content = "搜索失败: ${e.message}"
            )
        }
    }

    private suspend fun handleGetMemoryHealth(args: JsonObject): McpToolResult {
        return try {
            val reportResult = memoryManagementUseCase.getMemoryHealthReport()

            if (reportResult.isFailure) {
                return McpToolResult(false, "获取报告失败: ${reportResult.exceptionOrNull()?.message}")
            }

            val report = reportResult.getOrNull()!!

            val content = """
                记忆健康报告:

                整体评分: ${report.healthScore}/100 (${report.healthStatus})

                记忆统计:
                - 活跃记忆: ${report.totalActive} 条
                - 已遗忘: ${report.totalForgotten} 条
                - 强记忆: ${report.strongMemories} 条
                - 弱记忆: ${report.weakMemories} 条
                - 最近访问: ${report.recentlyAccessed} 条
                - 陈旧记忆: ${report.staleMemories} 条

                质量指标:
                - 平均重要性: ${String.format("%.2f", report.averageImportance)}
                - 平均强化次数: ${String.format("%.1f", report.averageReinforcementCount)}

                分类分布:
                ${report.categoryDistribution.entries.joinToString("\n") { "- ${it.key}: ${it.value} 条" }}
            """.trimIndent()

            McpToolResult(
                success = true,
                content = content,
                data = report
            )

        } catch (e: Exception) {
            Timber.e(e, "获取记忆健康报告失败")
            McpToolResult(
                success = false,
                content = "获取报告失败: ${e.message}"
            )
        }
    }

    private suspend fun handleGetMemoryStats(args: JsonObject): McpToolResult {
        return try {
            val memoryStats = embeddingRepository.getMemoryFactStats()
            val conversationStats = embeddingRepository.getConversationEmbeddingStats()

            val content = """
                记忆系统统计:

                对话Embeddings:
                - 活跃: ${conversationStats.activeCount} 条
                - 已归档: ${conversationStats.archivedCount} 条
                - 平均重要性: ${String.format("%.2f", conversationStats.averageImportance)}

                记忆事实:
                - 活跃: ${memoryStats.activeCount} 条
                - 已遗忘: ${memoryStats.forgottenCount} 条
                - 平均重要性: ${String.format("%.2f", memoryStats.averageImportance)}
                - 平均强化次数: ${String.format("%.1f", memoryStats.averageReinforcementCount)}

                分类统计:
                ${memoryStats.categoryStats.entries.joinToString("\n") { "- ${it.key}: ${it.value} 条" }}
            """.trimIndent()

            McpToolResult(
                success = true,
                content = content,
                data = mapOf(
                    "conversation" to conversationStats,
                    "memory" to memoryStats
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "获取统计信息失败")
            McpToolResult(
                success = false,
                content = "获取统计失败: ${e.message}"
            )
        }
    }

    // ========== 系统工具处理器（时间相关）==========

    private fun handleGetCurrentTime(args: JsonObject): McpToolResult {
        val format = args.get("format")?.asString ?: "full"

        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)

        val dayOfWeek = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "星期日"
            Calendar.MONDAY -> "星期一"
            Calendar.TUESDAY -> "星期二"
            Calendar.WEDNESDAY -> "星期三"
            Calendar.THURSDAY -> "星期四"
            Calendar.FRIDAY -> "星期五"
            Calendar.SATURDAY -> "星期六"
            else -> ""
        }

        val content = when (format) {
            "date" -> "${year}年${month}月${day}日 $dayOfWeek"
            "time" -> String.format("%02d:%02d:%02d", hour, minute, second)
            "datetime" -> "${year}年${month}月${day}日 $dayOfWeek ${String.format("%02d:%02d", hour, minute)}"
            else -> """
现在是${year}年${month}月${day}日 $dayOfWeek ${String.format("%02d:%02d:%02d", hour, minute, second)}
            """.trimIndent()
        }

        return McpToolResult(
            success = true,
            content = content,
            data = mapOf(
                "year" to year,
                "month" to month,
                "day" to day,
                "dayOfWeek" to dayOfWeek,
                "hour" to hour,
                "minute" to minute,
                "second" to second,
                "timestamp" to now.timeInMillis
            )
        )
    }

    private fun handleCalculateTimeDiff(args: JsonObject): McpToolResult {
        val startTimeStr = args.get("startTime").asString
        val endTimeStr = args.get("endTime").asString

        val startTime = parseDateTime(startTimeStr)
            ?: return McpToolResult(false, "开始时间格式错误: $startTimeStr")

        val endTime = parseDateTime(endTimeStr)
            ?: return McpToolResult(false, "结束时间格式错误: $endTimeStr")

        val diffMs = endTime - startTime
        val diffSeconds = diffMs / 1000
        val diffMinutes = diffSeconds / 60
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24

        val days = diffDays.toInt()
        val hours = (diffHours % 24).toInt()
        val minutes = (diffMinutes % 60).toInt()

        val content = buildString {
            append("时间差: ")
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")

            if (days == 0 && hours == 0 && minutes == 0) {
                append("不到1分钟")
            }
        }

        return McpToolResult(
            success = true,
            content = content,
            data = mapOf(
                "days" to days,
                "hours" to hours,
                "minutes" to minutes,
                "totalSeconds" to diffSeconds,
                "totalMinutes" to diffMinutes,
                "totalHours" to diffHours,
                "totalDays" to diffDays
            )
        )
    }

    private fun handleConvertTimezone(args: JsonObject): McpToolResult {
        val timeStr = args.get("time").asString
        val fromZone = args.get("fromZone").asString
        val toZone = args.get("toZone").asString

        return try {
            val sourceTime = parseDateTime(timeStr)
                ?: return McpToolResult(false, "时间格式错误: $timeStr")

            // 创建源时区的Calendar
            val sourceCalendar = Calendar.getInstance(TimeZone.getTimeZone(fromZone))
            sourceCalendar.timeInMillis = sourceTime

            // 创建目标时区的Calendar
            val targetCalendar = Calendar.getInstance(TimeZone.getTimeZone(toZone))
            targetCalendar.timeInMillis = sourceTime

            val targetTimeStr = formatDateTime(targetCalendar.timeInMillis)

            val content = """
                时区转换:
                ${fromZone}: ${formatDateTime(sourceTime)}
                ${toZone}: ${targetTimeStr}
            """.trimIndent()

            McpToolResult(
                success = true,
                content = content,
                data = mapOf(
                    "sourceTime" to formatDateTime(sourceTime),
                    "targetTime" to targetTimeStr,
                    "fromZone" to fromZone,
                    "toZone" to toZone
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "时区转换失败")
            McpToolResult(
                success = false,
                content = "时区转换失败: ${e.message}"
            )
        }
    }
}
