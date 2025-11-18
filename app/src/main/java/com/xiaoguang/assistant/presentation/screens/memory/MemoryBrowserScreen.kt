package com.xiaoguang.assistant.presentation.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import com.xiaoguang.assistant.presentation.components.*
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * 记忆浏览页面
 *
 * 功能：
 * - 时间线视图
 * - 分类筛选（日常、重要事件、学习等）
 * - 语义搜索
 * - 排序（时间、重要性）
 */
@Composable
fun MemoryBrowserScreen(
    viewModel: MemoryBrowserViewModel = hiltViewModel(),
    onNavigateToMemoryDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    MemoryBrowserContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::searchMemories,
        onCategoryChange = viewModel::setCategory,
        onSortByChange = viewModel::setSortBy,
        onMemoryClick = onNavigateToMemoryDetail,
        onRefresh = viewModel::loadMemories
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryBrowserContent(
    uiState: MemoryBrowserUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (MemoryCategory?) -> Unit,
    onSortByChange: (MemorySortBy) -> Unit,
    onMemoryClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆浏览") },
                actions = {
                    // 筛选按钮
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "筛选"
                        )
                    }
                    // 排序按钮
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "排序"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 搜索栏
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange
            )

            // 当前筛选条件显示
            if (uiState.selectedCategory != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "分类: ${getCategoryLabel(uiState.selectedCategory)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        XiaoguangTextButton(
                            text = "清除",
                            onClick = { onCategoryChange(null) }
                        )
                    }
                }
            }

            // 记忆列表
            when {
                uiState.isLoading -> {
                    LoadingState()
                }

                uiState.error != null -> {
                    ErrorState(
                        error = uiState.error,
                        onRetry = onRefresh
                    )
                }

                uiState.memories.isEmpty() -> {
                    EmptyState()
                }

                else -> {
                    MemoryList(
                        memories = uiState.memories,
                        onMemoryClick = onMemoryClick
                    )
                }
            }
        }
    }

    // 筛选弹窗
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            FilterSheet(
                selectedCategory = uiState.selectedCategory,
                onCategorySelect = {
                    onCategoryChange(it)
                    showFilterSheet = false
                }
            )
        }
    }

    // 排序弹窗
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false }
        ) {
            SortSheet(
                selectedSort = uiState.sortBy,
                onSortSelect = {
                    onSortByChange(it)
                    showSortSheet = false
                }
            )
        }
    }
}

/**
 * 搜索栏
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = XiaoguangDesignSystem.Elevation.xs
    ) {
        Row(
            modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.md)
        ) {
            XiaoguangTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "搜索记忆...",
                leadingIcon = Icons.Default.Search
            )
        }
    }
}

/**
 * 记忆列表
 */
@Composable
private fun MemoryList(
    memories: List<MemoryItem>,
    onMemoryClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(XiaoguangDesignSystem.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
    ) {
        items(
            items = memories,
            key = { it.id }
        ) { memory ->
            MemoryCardItem(
                memory = memory,
                onClick = { onMemoryClick(memory.id) }
            )
        }
    }
}

/**
 * 记忆卡片项
 */
@Composable
private fun MemoryCardItem(
    memory: MemoryItem,
    onClick: () -> Unit
) {
    val timestamp = remember(memory.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(memory.timestamp))
    }

    MemoryCard(
        content = memory.content,
        timestamp = timestamp,
        category = getCategoryLabel(memory.category),
        importance = memory.importance,
        onClick = onClick
    )
}

/**
 * 筛选弹窗
 */
@Composable
private fun FilterSheet(
    selectedCategory: MemoryCategory?,
    onCategorySelect: (MemoryCategory?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(XiaoguangDesignSystem.Spacing.lg)
    ) {
        Text(
            text = "选择分类",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = XiaoguangDesignSystem.Spacing.md)
        )

        // 全部
        FilterOption(
            label = "全部",
            selected = selectedCategory == null,
            onClick = { onCategorySelect(null) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = XiaoguangDesignSystem.Spacing.xs))

        // 各种分类
        MemoryCategory.entries.forEach { category ->
            FilterOption(
                label = getCategoryLabel(category),
                selected = selectedCategory == category,
                onClick = { onCategorySelect(category) }
            )
        }

        Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.lg))
    }
}

/**
 * 筛选选项
 */
@Composable
private fun FilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = XiaoguangDesignSystem.Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        XiaoguangTextButton(
            text = label,
            onClick = onClick
        )
        if (selected) {
            RadioButton(
                selected = true,
                onClick = onClick
            )
        }
    }
}

/**
 * 排序弹窗
 */
@Composable
private fun SortSheet(
    selectedSort: MemorySortBy,
    onSortSelect: (MemorySortBy) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(XiaoguangDesignSystem.Spacing.lg)
    ) {
        Text(
            text = "排序方式",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = XiaoguangDesignSystem.Spacing.md)
        )

        MemorySortBy.entries.forEach { sortBy ->
            FilterOption(
                label = getSortByLabel(sortBy),
                selected = selectedSort == sortBy,
                onClick = { onSortSelect(sortBy) }
            )
        }

        Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.lg))
    }
}

/**
 * 加载状态
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            CircularProgressIndicator()
            Text(
                text = XiaoguangPhrases.Memory.RECALLING,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误状态
 */
@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            XiaoguangPrimaryButton(
                text = XiaoguangPhrases.Common.RETRY,
                onClick = onRetry
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            XiaoguangAvatar(
                emotion = EmotionType.CONFUSED,
                size = XiaoguangDesignSystem.AvatarSize.xl
            )
            Text(
                text = XiaoguangPhrases.Memory.NO_MEMORY,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 获取分类标签
 */
private fun getCategoryLabel(category: MemoryCategory): String {
    return when (category) {
        MemoryCategory.EPISODIC -> "事件记忆"
        MemoryCategory.SEMANTIC -> "语义记忆"
        MemoryCategory.PROCEDURAL -> "过程记忆"
        MemoryCategory.CONTEXTUAL -> "情景记忆"
        MemoryCategory.PERSON -> "人物记忆"
        MemoryCategory.PREFERENCE -> "偏好记忆"
        MemoryCategory.FACT -> "事实知识"
        MemoryCategory.ANNIVERSARY -> "纪念日"
    }
}

/**
 * 获取排序标签
 */
private fun getSortByLabel(sortBy: MemorySortBy): String {
    return when (sortBy) {
        MemorySortBy.TIME_DESC -> "时间倒序（最新在前）"
        MemorySortBy.TIME_ASC -> "时间正序（最早在前）"
        MemorySortBy.IMPORTANCE_DESC -> "重要性倒序（最重要在前）"
        MemorySortBy.IMPORTANCE_ASC -> "重要性正序（最不重要在前）"
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun MemoryBrowserScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        val mockMemories = listOf(
            MemoryItem(
                id = "1",
                content = "今天主人说要去参加一个重要会议，看起来有点紧张。我提醒了他带上资料。",
                category = MemoryCategory.EPISODIC,
                timestamp = System.currentTimeMillis() - 3600_000,
                importance = 0.8f,
                tags = listOf("重要事件", "会议"),
                relatedPeople = listOf("主人")
            ),
            MemoryItem(
                id = "2",
                content = "学到了新知识：Kotlin的协程可以简化异步编程。",
                category = MemoryCategory.SEMANTIC,
                timestamp = System.currentTimeMillis() - 86400_000,
                importance = 0.6f,
                tags = listOf("学习", "编程"),
                relatedPeople = emptyList()
            )
        )

        MemoryBrowserContent(
            uiState = MemoryBrowserUiState(
                memories = mockMemories,
                isLoading = false
            ),
            onSearchQueryChange = {},
            onCategoryChange = {},
            onSortByChange = {},
            onMemoryClick = {},
            onRefresh = {}
        )
    }
}
