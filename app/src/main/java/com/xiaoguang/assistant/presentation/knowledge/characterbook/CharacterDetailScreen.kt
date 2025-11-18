package com.xiaoguang.assistant.presentation.knowledge.characterbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory
import com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory
import com.xiaoguang.assistant.presentation.knowledge.KnowledgeViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * 角色详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    characterId: String,
    viewModel: KnowledgeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val characterProfiles by viewModel.characterProfiles.collectAsStateWithLifecycle()
    val profile = characterProfiles.find { it.basicInfo.characterId == characterId }

    var memories by remember { mutableStateOf<List<CharacterMemory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(profile?.basicInfo?.name ?: "") }
    var editDescription by remember { mutableStateOf(profile?.basicInfo?.bio ?: "") }

    // 加载记忆
    LaunchedEffect(characterId) {
        viewModel.getCharacterMemories(characterId).collectLatest {
            memories = it
        }
    }

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text("角色不存在")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.basicInfo.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, "编辑")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMemoryDialog = true }) {
                Icon(Icons.Default.Add, "添加记忆")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基本信息
            item {
                ProfileSection(profile = profile)
            }

            // 记忆类别筛选
            item {
                MemoryCategoryFilter(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }

            // 记忆列表
            items(
                memories.filter { selectedCategory == null || it.category == selectedCategory }
                    .sortedByDescending { it.importance }
            ) { memory ->
                MemoryCard(memory = memory)
            }
        }
    }

    // 添加记忆对话框
    if (showAddMemoryDialog) {
        AddMemoryDialog(
            characterId = characterId,
            onDismiss = { showAddMemoryDialog = false },
            onConfirm = { memory ->
                viewModel.addCharacterMemory(memory)
                showAddMemoryDialog = false
            }
        )
    }

    // 编辑对话框
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑角色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("角色名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("角色描述") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                    Text(
                        text = "角色ID：${profile.basicInfo.characterId}（不可修改）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedProfile = profile.copy(
                            basicInfo = profile.basicInfo.copy(
                                name = editName.ifBlank { profile.basicInfo.name },
                                bio = editDescription.ifBlank { null }
                            )
                        )
                        viewModel.updateCharacterProfile(updatedProfile)
                        showEditDialog = false
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editName = profile.basicInfo.name
                    editDescription = profile.basicInfo.bio ?: ""
                }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 角色档案部分
 */
@Composable
fun ProfileSection(profile: com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "性格特征",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 特征标签
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profile.personality.traits.entries.forEach { (trait, strength) ->
                    AssistChip(
                        onClick = {},
                        label = { Text("$trait (${(strength * 100).toInt()}%)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 性格描述
            val description = profile.personality.description
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 说话风格
            Text(
                text = "说话风格：${profile.personality.speechStyle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 记忆类别筛选
 */
@Composable
fun MemoryCategoryFilter(
    selectedCategory: MemoryCategory?,
    onCategorySelected: (MemoryCategory?) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = if (selectedCategory == null) 0 else selectedCategory.ordinal + 1,
        edgePadding = 0.dp
    ) {
        Tab(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            text = { Text("全部") }
        )

        MemoryCategory.entries.forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                text = { Text(category.name) }
            )
        }
    }
}

/**
 * 记忆卡片
 */
@Composable
fun MemoryCard(memory: CharacterMemory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(memory.category.name) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "重要性: ${String.format("%.1f", memory.importance)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "访问: ${memory.accessCount}次",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium
            )

            if (memory.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    memory.tags.take(5).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 添加记忆对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryDialog(
    characterId: String,
    onDismiss: () -> Unit,
    onConfirm: (CharacterMemory) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MemoryCategory.EPISODIC) }
    var importance by remember { mutableStateOf("0.5") }
    var tags by remember { mutableStateOf("") }

    var showCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记忆") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 类别选择
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it }
                ) {
                    OutlinedTextField(
                        value = category.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类别") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                // 内容
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("记忆内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 8
                )

                // 重要性
                OutlinedTextField(
                    value = importance,
                    onValueChange = { importance = it },
                    label = { Text("重要性 (0.0-1.0)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 标签
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val memory = CharacterMemory(
                        memoryId = "mem_${System.currentTimeMillis()}",
                        characterId = characterId,
                        category = category,
                        content = content,
                        importance = importance.toFloatOrNull() ?: 0.5f,
                        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        createdAt = System.currentTimeMillis(),
                        lastAccessed = System.currentTimeMillis()
                    )
                    onConfirm(memory)
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
