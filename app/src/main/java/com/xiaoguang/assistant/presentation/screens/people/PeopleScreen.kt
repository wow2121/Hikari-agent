package com.xiaoguang.assistant.presentation.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.components.*
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * 人物管理页面
 *
 * 整合声纹识别和社交系统，显示：
 * - 所有认识的人
 * - 人物关系
 * - 声纹信息
 * - 新朋友注册
 */
@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel = hiltViewModel(),
    onNavigateToPersonDetail: (String) -> Unit = {},
    onNavigateToNewFriendRegistration: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    PeopleContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::searchPeople,
        onFilterChange = viewModel::setFilter,
        onPersonClick = onNavigateToPersonDetail,
        onAddNewFriend = onNavigateToNewFriendRegistration,
        onRefresh = viewModel::loadPeople
    )
}

@Composable
private fun PeopleContent(
    uiState: PeopleUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (PeopleFilter) -> Unit,
    onPersonClick: (String) -> Unit,
    onAddNewFriend: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            XiaoguangFab(
                icon = Icons.Default.Add,
                onClick = onAddNewFriend,
                contentDescription = "添加新朋友"
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

            // 筛选Tabs
            FilterTabs(
                selectedFilter = uiState.selectedFilter,
                onFilterChange = onFilterChange
            )

            // 人物列表
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

                uiState.people.isEmpty() -> {
                    EmptyState()
                }

                else -> {
                    PeopleList(
                        people = filterPeople(uiState.people, uiState.selectedFilter, uiState.searchQuery),
                        onPersonClick = onPersonClick
                    )
                }
            }
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
                placeholder = "搜索人物...",
                leadingIcon = Icons.Default.Search
            )
        }
    }
}

/**
 * 筛选Tabs
 */
@Composable
private fun FilterTabs(
    selectedFilter: PeopleFilter,
    onFilterChange: (PeopleFilter) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedFilter.ordinal,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = XiaoguangDesignSystem.Spacing.sm
    ) {
        PeopleFilter.entries.forEach { filter ->
            Tab(
                selected = filter == selectedFilter,
                onClick = { onFilterChange(filter) },
                text = {
                    Text(
                        text = getFilterLabel(filter),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/**
 * 人物列表
 */
@Composable
private fun PeopleList(
    people: List<PersonInfo>,
    onPersonClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(XiaoguangDesignSystem.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
    ) {
        items(
            items = people,
            key = { it.personId }
        ) { person ->
            PersonCardItem(
                person = person,
                onClick = { onPersonClick(person.personId) }
            )
        }
    }
}

/**
 * 人物卡片项
 */
@Composable
private fun PersonCardItem(
    person: PersonInfo,
    onClick: () -> Unit
) {
    val lastSeenTime = remember(person.lastSeenTimestamp) {
        if (person.lastSeenTimestamp > 0) {
            formatTimestamp(person.lastSeenTimestamp)
        } else {
            "从未见过"
        }
    }

    PersonCard(
        name = person.displayName,
        relationship = person.relationship,
        lastSeenTime = lastSeenTime,
        isMaster = person.isMaster,
        onClick = onClick
    )
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
                text = XiaoguangPhrases.Common.LOADING,
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
                emotion = EmotionType.CURIOUS,
                size = XiaoguangDesignSystem.AvatarSize.xl
            )
            Text(
                text = XiaoguangPhrases.Common.EMPTY,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "还没有认识的人哦~",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 筛选人物列表
 */
private fun filterPeople(
    people: List<PersonInfo>,
    filter: PeopleFilter,
    searchQuery: String
): List<PersonInfo> {
    var filtered = people

    // 应用筛选条件
    filtered = when (filter) {
        PeopleFilter.ALL -> filtered
        PeopleFilter.MASTER -> filtered.filter { it.isMaster }
        PeopleFilter.FRIENDS -> filtered.filter { it.relationship == "朋友" }
        PeopleFilter.ACQUAINTANCES -> filtered.filter { it.relationship == "认识的人" }
        PeopleFilter.STRANGERS -> filtered.filter { it.relationship == null }
        PeopleFilter.WITH_VOICEPRINT -> filtered.filter { it.hasVoiceprint }
    }

    // 应用搜索查询
    if (searchQuery.isNotBlank()) {
        filtered = filtered.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.personName.contains(searchQuery, ignoreCase = true)
        }
    }

    return filtered
}

/**
 * 获取筛选标签
 */
private fun getFilterLabel(filter: PeopleFilter): String {
    return when (filter) {
        PeopleFilter.ALL -> "全部"
        PeopleFilter.MASTER -> XiaoguangPhrases.Social.MASTER
        PeopleFilter.FRIENDS -> "朋友"
        PeopleFilter.ACQUAINTANCES -> "认识的人"
        PeopleFilter.STRANGERS -> XiaoguangPhrases.Social.STRANGER
        PeopleFilter.WITH_VOICEPRINT -> "有声纹"
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> XiaoguangPhrases.Common.JUST_NOW
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 604800_000 -> "${diff / 86400_000}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun PeopleScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        val mockPeople = listOf(
            PersonInfo(
                personId = "1",
                personName = "主人",
                displayName = "主人",
                isMaster = true,
                relationship = "主人",
                lastSeenTimestamp = System.currentTimeMillis() - 3600_000,
                hasVoiceprint = true,
                voiceprintSampleCount = 10,
                intimacyScore = 1.0f
            ),
            PersonInfo(
                personId = "2",
                personName = "张三",
                displayName = "张三",
                isMaster = false,
                relationship = "朋友",
                lastSeenTimestamp = System.currentTimeMillis() - 86400_000,
                hasVoiceprint = true,
                voiceprintSampleCount = 5,
                intimacyScore = 0.7f
            ),
            PersonInfo(
                personId = "3",
                personName = "李四",
                displayName = "李四",
                isMaster = false,
                relationship = "认识的人",
                lastSeenTimestamp = System.currentTimeMillis() - 604800_000,
                hasVoiceprint = false,
                voiceprintSampleCount = 0,
                intimacyScore = 0.5f
            )
        )

        PeopleContent(
            uiState = PeopleUiState(
                people = mockPeople,
                isLoading = false
            ),
            onSearchQueryChange = {},
            onFilterChange = {},
            onPersonClick = {},
            onAddNewFriend = {},
            onRefresh = {}
        )
    }
}
