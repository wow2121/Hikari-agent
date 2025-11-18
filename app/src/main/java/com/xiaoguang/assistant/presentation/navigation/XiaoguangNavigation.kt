package com.xiaoguang.assistant.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 小光导航结构
 *
 * 定义了应用的完整导航结构：
 * - 底部导航栏（5个主要Tab）
 * - 抽屉菜单（辅助功能）
 */

/**
 * 底部导航栏页面
 *
 * 5个主要页面：
 * 1. 小光 - Xiaoguang Center（主页，显示小光状态、情绪、心流）
 * 2. 对话 - Conversation（对话界面）
 * 3. 人物 - People（人物管理，声纹+社交系统）
 * 4. 日程 - Schedule（日程和提醒）
 * 5. 我的 - Profile（个人设置和配置）
 */
sealed class BottomNavScreen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    /** 小光中心 - 主页 */
    object XiaoguangCenter : BottomNavScreen(
        route = "xiaoguang_center",
        title = "小光",
        icon = Icons.Default.Face,
        selectedIcon = Icons.Default.Face
    )

    /** 对话 */
    object Conversation : BottomNavScreen(
        route = "conversation",
        title = "对话",
        icon = Icons.Default.Chat,
        selectedIcon = Icons.Default.Chat
    )

    /** 人物管理 */
    object People : BottomNavScreen(
        route = "people",
        title = "人物",
        icon = Icons.Default.People,
        selectedIcon = Icons.Default.People
    )

    /** 日程 */
    object Schedule : BottomNavScreen(
        route = "schedule",
        title = "日程",
        icon = Icons.Default.CalendarToday,
        selectedIcon = Icons.Default.CalendarToday
    )

    /** 我的 */
    object Profile : BottomNavScreen(
        route = "profile",
        title = "我的",
        icon = Icons.Default.Person,
        selectedIcon = Icons.Default.Person
    )

    companion object {
        /** 所有底部导航项 */
        val items = listOf(XiaoguangCenter, Conversation, People, Schedule, Profile)
    }
}

/**
 * 抽屉菜单页面
 *
 * 辅助功能页面，通过抽屉菜单访问：
 * - 记忆浏览
 * - 知识中心
 * - 心流状态
 * - 待办事项
 * - 环境监听
 * - 开发者选项
 */
sealed class DrawerScreen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    /** 记忆浏览 */
    object MemoryBrowser : DrawerScreen(
        route = "memory_browser",
        title = "记忆浏览",
        icon = Icons.Default.Psychology,
        description = "查看和管理我的记忆"
    )

    /** 知识中心 */
    object KnowledgeHub : DrawerScreen(
        route = "knowledge_hub",
        title = "知识中心",
        icon = Icons.Default.MenuBook,
        description = "世界书、角色书、知识图谱"
    )

    /** 心流状态 */
    object FlowStatus : DrawerScreen(
        route = "flow_status",
        title = "心流状态",
        icon = Icons.Default.Waves,
        description = "查看我的思维流程"
    )

    /** 待办事项 */
    object TodoList : DrawerScreen(
        route = "todo_list",
        title = "待办事项",
        icon = Icons.Default.CheckCircle,
        description = "任务和提醒列表"
    )

    /** 环境监听 */
    object EnvironmentMonitor : DrawerScreen(
        route = "environment_monitor",
        title = "环境监听",
        icon = Icons.Default.Hearing,
        description = "环境感知和声音监听"
    )

    /** 开发者选项 */
    object DeveloperOptions : DrawerScreen(
        route = "developer_options",
        title = "开发者选项",
        icon = Icons.Default.DeveloperMode,
        description = "调试和高级设置"
    )

    companion object {
        /** 所有抽屉菜单项 */
        val items = listOf(
            MemoryBrowser,
            KnowledgeHub,
            FlowStatus,
            TodoList,
            EnvironmentMonitor
        )

        /** 开发者选项（单独列出） */
        val developerItems = listOf(DeveloperOptions)
    }
}

/**
 * 详情页面
 *
 * 需要导航参数的页面
 */
sealed class DetailScreen(
    val route: String,
    val title: String
) {
    /** 人物详情 */
    object PersonDetail : DetailScreen(
        route = "person_detail/{personId}",
        title = "人物详情"
    ) {
        fun createRoute(personId: String) = "person_detail/$personId"
        const val PERSON_ID_ARG = "personId"
    }

    /** 记忆详情 */
    object MemoryDetail : DetailScreen(
        route = "memory_detail/{memoryId}",
        title = "记忆详情"
    ) {
        fun createRoute(memoryId: String) = "memory_detail/$memoryId"
        const val MEMORY_ID_ARG = "memoryId"
    }

    /** 角色详情 */
    object CharacterDetail : DetailScreen(
        route = "character_detail/{characterId}",
        title = "角色详情"
    ) {
        fun createRoute(characterId: String) = "character_detail/$characterId"
        const val CHARACTER_ID_ARG = "characterId"
    }

    /** 关系图谱详情 */
    object RelationshipGraph : DetailScreen(
        route = "relationship_graph/{personId}",
        title = "关系图谱"
    ) {
        fun createRoute(personId: String) = "relationship_graph/$personId"
        const val PERSON_ID_ARG = "personId"
    }
}

/**
 * 知识系统子页面
 *
 * 从知识中心访问的子页面
 */
sealed class KnowledgeScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    /** 世界书 */
    object WorldBook : KnowledgeScreen(
        route = "world_book",
        title = "世界书",
        icon = Icons.Default.Public
    )

    /** 角色书 */
    object CharacterBook : KnowledgeScreen(
        route = "character_book",
        title = "角色书",
        icon = Icons.Default.People
    )

    /** 向量数据库 */
    object VectorDatabase : KnowledgeScreen(
        route = "vector_database",
        title = "向量数据库",
        icon = Icons.Default.Dns
    )

    /** 图数据库 */
    object GraphDatabase : KnowledgeScreen(
        route = "graph_database",
        title = "图数据库",
        icon = Icons.Default.Hub
    )

    companion object {
        val items = listOf(WorldBook, CharacterBook, VectorDatabase, GraphDatabase)
    }
}

/**
 * 开发者选项子页面
 */
sealed class DeveloperScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    /** 心流调试 */
    object FlowDebug : DeveloperScreen(
        route = "flow_debug",
        title = "心流调试",
        icon = Icons.Default.BugReport
    )

    /** 心流设置 */
    object FlowSettings : DeveloperScreen(
        route = "flow_settings",
        title = "心流设置",
        icon = Icons.Default.Settings
    )

    /** 网络调试 */
    object NetworkDebug : DeveloperScreen(
        route = "network_debug",
        title = "网络调试",
        icon = Icons.Default.NetworkCheck
    )

    /** 数据管理 */
    object DataManagement : DeveloperScreen(
        route = "data_management",
        title = "数据管理",
        icon = Icons.Default.Storage
    )

    /** 日志查看 */
    object LogViewer : DeveloperScreen(
        route = "log_viewer",
        title = "日志查看",
        icon = Icons.Default.Article
    )

    companion object {
        val items = listOf(FlowDebug, FlowSettings, NetworkDebug, DataManagement, LogViewer)
    }
}

/**
 * 导航路由常量
 */
object NavRoutes {
    // 底部导航
    const val XIAOGUANG_CENTER = "xiaoguang_center"
    const val CONVERSATION = "conversation"
    const val PEOPLE = "people"
    const val SCHEDULE = "schedule"
    const val PROFILE = "profile"

    // 抽屉菜单
    const val MEMORY_BROWSER = "memory_browser"
    const val KNOWLEDGE_HUB = "knowledge_hub"
    const val FLOW_STATUS = "flow_status"
    const val TODO_LIST = "todo_list"
    const val ENVIRONMENT_MONITOR = "environment_monitor"
    const val DEVELOPER_OPTIONS = "developer_options"

    // 详情页
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val MEMORY_DETAIL = "memory_detail/{memoryId}"
    const val CHARACTER_DETAIL = "character_detail/{characterId}"
    const val RELATIONSHIP_GRAPH = "relationship_graph/{personId}"

    // 知识系统
    const val WORLD_BOOK = "world_book"
    const val CHARACTER_BOOK = "character_book"
    const val VECTOR_DATABASE = "vector_database"
    const val GRAPH_DATABASE = "graph_database"

    // 开发者选项
    const val FLOW_DEBUG = "flow_debug"
    const val FLOW_SETTINGS = "flow_settings"
    const val NETWORK_DEBUG = "network_debug"
    const val DATA_MANAGEMENT = "data_management"
    const val LOG_VIEWER = "log_viewer"
}
