package com.xiaoguang.assistant.presentation.knowledge.worldbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoguang.assistant.domain.knowledge.models.WorldEntry
import com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory
import com.xiaoguang.assistant.presentation.knowledge.KnowledgeViewModel

/**
 * World Book管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookScreen(
    viewModel: KnowledgeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val worldEntries by viewModel.worldEntries.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<WorldEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("World Book 管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, "导入")
                    }
                    IconButton(onClick = {
                        val json = viewModel.exportWorldBookAsJson()
                        if (json != null) {
                            exportedJson = json
                            showExportDialog = true
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, "导出")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "添加条目")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isNotEmpty()) {
                        viewModel.searchWorldEntries(it)
                    } else {
                        viewModel.loadData()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索条目...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            // UI状态提示
            when (uiState) {
                is com.xiaoguang.assistant.presentation.knowledge.KnowledgeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is com.xiaoguang.assistant.presentation.knowledge.KnowledgeUiState.Error -> {
                    val error = (uiState as com.xiaoguang.assistant.presentation.knowledge.KnowledgeUiState.Error).message
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    // 条目列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(worldEntries) { entry ->
                            WorldEntryCard(
                                entry = entry,
                                onClick = { selectedEntry = entry },
                                onToggleEnabled = {
                                    viewModel.updateWorldEntry(entry.copy(enabled = !entry.enabled))
                                },
                                onDelete = {
                                    viewModel.deleteWorldEntry(entry.entryId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog || selectedEntry != null) {
        WorldEntryDialog(
            entry = selectedEntry,
            onDismiss = {
                showAddDialog = false
                selectedEntry = null
            },
            onConfirm = { entry ->
                if (selectedEntry != null) {
                    viewModel.updateWorldEntry(entry)
                } else {
                    viewModel.addWorldEntry(entry)
                }
                showAddDialog = false
                selectedEntry = null
            }
        )
    }

    // 导入对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入World Book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("粘贴World Book JSON数据：")
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("请粘贴JSON...") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importJson.isNotBlank()) {
                            viewModel.importFromLorebook(importJson)
                            showImportDialog = false
                            importJson = ""
                        }
                    },
                    enabled = importJson.isNotBlank()
                ) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importJson = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 导出对话框
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出成功") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("World Book JSON已生成（${exportedJson.length} 字符）：")
                    OutlinedTextField(
                        value = exportedJson,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        readOnly = true,
                        maxLines = 10
                    )
                    Text(
                        text = "提示：可以长按文本框复制内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportedJson = ""
                }) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * World Entry卡片
 */
@Composable
fun WorldEntryCard(
    entry: WorldEntry,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // 标题和分类
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(entry.category.name) }
                        )
                        Text(
                            text = "优先级: ${entry.priority}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 关键词
                    Text(
                        text = "关键词: ${entry.keys.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 内容预览
                    Text(
                        text = entry.content.take(100) + if (entry.content.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 操作按钮
                Column {
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * World Entry编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldEntryDialog(
    entry: WorldEntry?,
    onDismiss: () -> Unit,
    onConfirm: (WorldEntry) -> Unit
) {
    var entryId by remember { mutableStateOf(entry?.entryId ?: "entry_${System.currentTimeMillis()}") }
    var keys by remember { mutableStateOf(entry?.keys?.joinToString(", ") ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: WorldEntryCategory.SETTING) }
    var priority by remember { mutableStateOf(entry?.priority?.toString() ?: "100") }
    var enabled by remember { mutableStateOf(entry?.enabled ?: true) }

    var showCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "添加条目" else "编辑条目") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 关键词
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    label = { Text("关键词（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 分类
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it }
                ) {
                    OutlinedTextField(
                        value = category.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        WorldEntryCategory.entries.forEach { cat ->
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

                // 优先级
                OutlinedTextField(
                    value = priority,
                    onValueChange = { if (it.all { c -> c.isDigit() }) priority = it },
                    label = { Text("优先级") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 内容
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 8
                )

                // 启用状态
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                    Text("启用此条目")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val worldEntry = WorldEntry(
                        entryId = entryId,
                        keys = keys.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        content = content,
                        category = category,
                        priority = priority.toIntOrNull() ?: 100,
                        enabled = enabled
                    )
                    onConfirm(worldEntry)
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
