package com.xiaoguang.assistant.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiaoguang.assistant.presentation.design.XiaoguangAnimations
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.state.XiaoguangCoreState
import com.xiaoguang.assistant.presentation.components.XiaoguangStatusBar
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import kotlinx.coroutines.launch

/**
 * 小光主导航Host
 *
 * 包含：
 * - Scaffold 框架
 * - 底部导航栏
 * - 抽屉菜单
 * - NavHost 路由
 * - 状态栏
 *
 * @param coreState 核心状态
 * @param navController 导航控制器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiaoguangNavHost(
    coreState: XiaoguangCoreState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 当前路由
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 判断是否显示底部导航栏（只在主页面显示）
    val showBottomBar = currentDestination?.route in BottomNavScreen.items.map { it.route }

    // 判断是否显示顶部状态栏
    val showTopBar = showBottomBar

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            XiaoguangDrawerContent(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                coreState = coreState
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (showTopBar) {
                    XiaoguangTopBar(
                        coreState = coreState,
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    XiaoguangBottomBar(
                        currentDestination = currentDestination,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = BottomNavScreen.XiaoguangCenter.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                // ===== 底部导航页面 =====
                // 小光中心 - 淡入淡出动画
                composable(
                    route = BottomNavScreen.XiaoguangCenter.route,
                    enterTransition = { XiaoguangAnimations.Transitions.fadeInOut().first },
                    exitTransition = { XiaoguangAnimations.Transitions.fadeInOut().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.center.XiaoguangCenterScreen(
                        onNavigateToConversation = {
                            navController.navigate(BottomNavScreen.Conversation.route)
                        }
                    )
                }

                // 对话页面 - 从右侧滑入
                composable(
                    route = BottomNavScreen.Conversation.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.conversation.NewConversationScreen()
                }

                // 人物页面 - 淡入淡出动画
                composable(
                    route = BottomNavScreen.People.route,
                    enterTransition = { XiaoguangAnimations.Transitions.fadeInOut().first },
                    exitTransition = { XiaoguangAnimations.Transitions.fadeInOut().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.people.PeopleScreen(
                        onNavigateToPersonDetail = { personId ->
                            navController.navigate(DetailScreen.PersonDetail.createRoute(personId))
                        },
                        onNavigateToNewFriendRegistration = {
                            // TODO: 导航到新朋友注册页面
                        }
                    )
                }

                // 日程页面 - 淡入淡出动画
                composable(
                    route = BottomNavScreen.Schedule.route,
                    enterTransition = { XiaoguangAnimations.Transitions.fadeInOut().first },
                    exitTransition = { XiaoguangAnimations.Transitions.fadeInOut().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.schedule.ScheduleScreen()
                }

                // 我的页面 - 淡入淡出动画
                composable(
                    route = BottomNavScreen.Profile.route,
                    enterTransition = { XiaoguangAnimations.Transitions.fadeInOut().first },
                    exitTransition = { XiaoguangAnimations.Transitions.fadeInOut().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.profile.ProfileScreen(
                        coreState = coreState,
                        onNavigateToSettings = {
                            // TODO: 导航到设置页面
                        },
                        onNavigateToDeveloperOptions = {
                            navController.navigate(DrawerScreen.DeveloperOptions.route)
                        }
                    )
                }

                // ===== 抽屉菜单页面 =====
                // 记忆浏览 - 从底部滑入
                composable(
                    route = DrawerScreen.MemoryBrowser.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.memory.MemoryBrowserScreen(
                        onNavigateToMemoryDetail = { memoryId ->
                            navController.navigate(DetailScreen.MemoryDetail.createRoute(memoryId))
                        }
                    )
                }

                // 知识中心 - 从底部滑入
                composable(
                    route = DrawerScreen.KnowledgeHub.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.knowledge.KnowledgeHubScreen(
                        onNavigateToWorldBook = {
                            navController.navigate(KnowledgeScreen.WorldBook.route)
                        },
                        onNavigateToCharacterBook = {
                            navController.navigate(KnowledgeScreen.CharacterBook.route)
                        },
                        onNavigateToVectorDatabase = {
                            navController.navigate(KnowledgeScreen.VectorDatabase.route)
                        },
                        onNavigateToGraphDatabase = {
                            navController.navigate(KnowledgeScreen.GraphDatabase.route)
                        }
                    )
                }

                // 世界书页面
                composable(
                    route = KnowledgeScreen.WorldBook.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.knowledge.worldbook.WorldBookScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 角色书页面
                composable(
                    route = KnowledgeScreen.CharacterBook.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.knowledge.characterbook.CharacterBookScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 向量数据库页面
                composable(
                    route = KnowledgeScreen.VectorDatabase.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.knowledge.vector.VectorDatabaseScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 图数据库页面
                composable(
                    route = KnowledgeScreen.GraphDatabase.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.knowledge.graph.GraphDatabaseScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 心流状态 - 从底部滑入
                composable(
                    route = DrawerScreen.FlowStatus.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.flow.FlowStatusScreen()
                }

                // 待办事项 - 从底部滑入
                composable(
                    route = DrawerScreen.TodoList.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.todo.TodoListScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 环境监听 - 从底部滑入
                composable(
                    route = DrawerScreen.EnvironmentMonitor.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromBottom().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.environment.EnvironmentMonitorScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 开发者选项 - 从右侧滑入（特殊入口）
                composable(
                    route = DrawerScreen.DeveloperOptions.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.screens.developer.DeveloperOptionsScreen(
                        onNavigateToFlowDebug = {
                            navController.navigate(DeveloperScreen.FlowDebug.route)
                        },
                        onNavigateToFlowSettings = {
                            navController.navigate(DeveloperScreen.FlowSettings.route)
                        },
                        onNavigateToNetworkDebug = {
                            navController.navigate(DeveloperScreen.NetworkDebug.route)
                        },
                        onNavigateToDataManagement = {
                            navController.navigate(DeveloperScreen.DataManagement.route)
                        },
                        onNavigateToLogViewer = {
                            navController.navigate(DeveloperScreen.LogViewer.route)
                        }
                    )
                }

                // 心流调试页面
                composable(
                    route = DeveloperScreen.FlowDebug.route,
                    enterTransition = { XiaoguangAnimations.Transitions.slideInFromRight().first },
                    exitTransition = { XiaoguangAnimations.Transitions.slideInFromRight().second }
                ) {
                    com.xiaoguang.assistant.presentation.ui.debug.FlowDebugScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 心流设置页面
                composable(DeveloperScreen.FlowSettings.route) {
                    com.xiaoguang.assistant.presentation.ui.debug.FlowSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 网络调试页面
                composable(DeveloperScreen.NetworkDebug.route) {
                    com.xiaoguang.assistant.presentation.ui.debug.NetworkDebugScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 数据管理页面
                composable(DeveloperScreen.DataManagement.route) {
                    com.xiaoguang.assistant.presentation.ui.debug.DataManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 日志查看器页面
                composable(DeveloperScreen.LogViewer.route) {
                    com.xiaoguang.assistant.presentation.ui.debug.LogViewerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ===== 详情页面 =====
                // 人物详情页面
                composable(
                    route = DetailScreen.PersonDetail.route,
                    arguments = listOf(
                        navArgument(DetailScreen.PersonDetail.PERSON_ID_ARG) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val personId = backStackEntry.arguments?.getString(DetailScreen.PersonDetail.PERSON_ID_ARG) ?: ""
                    com.xiaoguang.assistant.presentation.screens.people.PersonDetailScreen(
                        personId = personId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // 记忆详情页面
                composable(
                    route = DetailScreen.MemoryDetail.route,
                    arguments = listOf(
                        navArgument(DetailScreen.MemoryDetail.MEMORY_ID_ARG) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val memoryId = backStackEntry.arguments?.getString(DetailScreen.MemoryDetail.MEMORY_ID_ARG) ?: ""
                    com.xiaoguang.assistant.presentation.screens.memory.MemoryDetailScreen(
                        memoryId = memoryId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * 顶部栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XiaoguangTopBar(
    coreState: XiaoguangCoreState,
    onMenuClick: () -> Unit
) {
    Column {
        TopAppBar(
            title = { Text("小光 AI 助手") },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "菜单"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // 状态栏
        XiaoguangStatusBar(
            emotion = coreState.emotion.currentEmotion,
            flowImpulse = coreState.flow.impulse,
            speakerName = coreState.speaker.currentSpeakerName,
            isListening = coreState.speaker.isListening
        )

        HorizontalDivider()
    }
}

/**
 * 底部导航栏
 */
@Composable
private fun XiaoguangBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit
) {
    val emotionColors = LocalEmotionColors.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = XiaoguangDesignSystem.Elevation.sm
    ) {
        BottomNavScreen.items.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = selected,
                onClick = { onNavigate(screen.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = emotionColors.primary,
                    selectedTextColor = emotionColors.primary,
                    indicatorColor = emotionColors.background
                )
            )
        }
    }
}

/**
 * 抽屉菜单内容
 */
@Composable
private fun XiaoguangDrawerContent(
    onNavigate: (String) -> Unit,
    coreState: XiaoguangCoreState
) {
    val emotionColors = LocalEmotionColors.current

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        // 抽屉头部
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.lg)
        ) {
            Text(
                text = XiaoguangPhrases.Greeting.FIRST_TIME,
                style = MaterialTheme.typography.titleLarge,
                color = emotionColors.primary
            )
            Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xs))
            Text(
                text = coreState.emotion.currentEmotion.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // 主菜单项
        DrawerScreen.items.forEach { screen ->
            NavigationDrawerItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.title) },
                badge = {
                    Text(
                        text = screen.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                selected = false,
                onClick = { onNavigate(screen.route) },
                modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.sm)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = XiaoguangDesignSystem.Spacing.sm))

        // 开发者选项
        DrawerScreen.developerItems.forEach { screen ->
            NavigationDrawerItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.title) },
                selected = false,
                onClick = { onNavigate(screen.route) },
                modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.sm),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            )
        }
    }
}

/**
 * 占位符屏幕（用于还未实现的页面）
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "$title - 开发中...",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
