package com.xiaoguang.assistant.presentation.knowledge.characterbook

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
import com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile
import com.xiaoguang.assistant.presentation.knowledge.KnowledgeViewModel

/**
 * Character Book管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterBookScreen(
    viewModel: KnowledgeViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val characters by viewModel.characterProfiles.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }
    var newCharacterName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Character Book") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, "导入")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "添加角色")
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
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索角色...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            // UI状态
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
                    // 角色列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            characters.filter {
                                searchQuery.isEmpty() ||
                                        it.basicInfo.name.contains(searchQuery, ignoreCase = true)
                            }
                        ) { profile ->
                            CharacterCard(
                                profile = profile,
                                onClick = { onNavigateToDetail(profile.basicInfo.characterId) },
                                onDelete = {
                                    viewModel.deleteCharacterProfile(profile.basicInfo.characterId)
                                },
                                onExport = { exportProfile ->
                                    val json = viewModel.exportCharacterProfileAsJson(exportProfile)
                                    if (json != null) {
                                        exportedJson = json
                                        showExportDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加角色对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加角色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("输入角色名称：")
                    OutlinedTextField(
                        value = newCharacterName,
                        onValueChange = { newCharacterName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("角色名称...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCharacterName.isNotBlank()) {
                            onNavigateToDetail(newCharacterName)
                            showAddDialog = false
                            newCharacterName = ""
                        }
                    },
                    enabled = newCharacterName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newCharacterName = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 导入对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入Character Book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("粘贴Character Book JSON数据：")
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
                    Text("Character Book JSON已生成（${exportedJson.length} 字符）：")
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
 * 角色卡片
 */
@Composable
fun CharacterCard(
    profile: CharacterProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: (CharacterProfile) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 姓名和昵称
                Text(
                    text = profile.basicInfo.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (profile.basicInfo.nickname != null) {
                    Text(
                        text = "昵称: ${profile.basicInfo.nickname}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 性格特征
                if (profile.personality.traits.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        profile.personality.getTopTraits(3).forEach { (trait, _) ->
                            AssistChip(
                                onClick = {},
                                label = { Text(trait, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 简介
                val description = profile.personality.description ?: ""
                Text(
                    text = description.take(60) +
                            if (description.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                IconButton(onClick = { onExport(profile) }) {
                    Icon(Icons.Default.FileDownload, "导出")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
