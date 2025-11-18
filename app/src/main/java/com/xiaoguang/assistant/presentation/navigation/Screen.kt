package com.xiaoguang.assistant.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Conversation : Screen("conversation", "对话", Icons.Default.Chat)
    object Todo : Screen("todo", "待办", Icons.Default.CheckCircle)
    object Calendar : Screen("calendar", "日程", Icons.Default.CalendarToday)
    object Memory : Screen("memory", "记忆", Icons.Default.Psychology)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
    object FlowSettings : Screen("flow_settings", "心流设置", Icons.Default.Psychology)
    object FlowDebug : Screen("flow_debug", "心流调试", Icons.Default.BugReport)

    // Knowledge System Routes
    object KnowledgeOverview : Screen("knowledge_overview", "知识系统", Icons.Default.MenuBook)
    object WorldBook : Screen("world_book", "世界书", Icons.Default.Public)
    object CharacterBook : Screen("character_book", "角色书", Icons.Default.People)
    object RelationshipGraph : Screen("relationship_graph", "关系图谱", Icons.Default.AccountTree)
    object VectorDatabase : Screen("vector_database", "向量数据库", Icons.Default.Dns)
    object GraphDatabase : Screen("graph_database", "图数据库", Icons.Default.Hub)

    /**
     * Character Detail Screen with character ID parameter
     * Route pattern: "character_detail/{characterId}"
     */
    object CharacterDetail : Screen("character_detail/{characterId}", "角色详情", Icons.Default.Person) {
        /**
         * Create route with character ID
         */
        fun createRoute(characterId: String): String {
            return "character_detail/$characterId"
        }

        /**
         * Extract character ID from route arguments
         */
        const val CHARACTER_ID_ARG = "characterId"
    }

    companion object {
        val items = listOf(Conversation, Todo, Calendar, Memory, Settings)

        /**
         * Knowledge system navigation items (accessible from settings or knowledge overview)
         */
        val knowledgeItems = listOf(KnowledgeOverview, WorldBook, CharacterBook)
    }
}
