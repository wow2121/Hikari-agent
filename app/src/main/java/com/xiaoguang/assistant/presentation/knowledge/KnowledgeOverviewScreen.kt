package com.xiaoguang.assistant.presentation.knowledge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory
import com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch

/**
 * 知识系统总览界面
 * 显示World Book和Character Book的统计信息
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnowledgeOverviewScreen(
    viewModel: KnowledgeViewModel = hiltViewModel(),
    onNavigateToWorldBook: () -> Unit = {},
    onNavigateToCharacterBook: () -> Unit = {},
    onNavigateToRelationshipGraph: () -> Unit = {},
    onNavigateToVectorDatabase: () -> Unit = {},
    onNavigateToGraphDatabase: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val worldEntries by viewModel.worldEntries.collectAsStateWithLifecycle()
    val characterProfiles by viewModel.characterProfiles.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemStats by viewModel.systemStats.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    var relationshipCount by remember { mutableStateOf(0) }

    // 加载关系总数
    LaunchedEffect(characterProfiles) {
        relationshipCount = viewModel.getTotalRelationshipCount()
    }

    var showMigrationDialog by remember { mutableStateOf(false) }
    var showReinitDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识系统管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showReinitDialog = true }) {
                        Icon(Icons.Default.Refresh, "重新初始化")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 系统状态卡片
            SystemStatusCard(
                systemStats = systemStats,
                uiState = uiState,
                onMigrate = { showMigrationDialog = true }
            )

            // World Book统计
            WorldBookStatsCard(
                entries = worldEntries,
                onNavigate = onNavigateToWorldBook
            )

            // Character Book统计
            CharacterBookStatsCard(
                profiles = characterProfiles,
                onNavigate = onNavigateToCharacterBook
            )

            // 快速操作
            QuickActionsCard(
                onImportLorebook = { showImportDialog = true },
                onExportLorebook = {
                    coroutineScope.launch {
                        val json = viewModel.exportAsLorebook()
                        if (json != null) {
                            exportedJson = json
                            showExportDialog = true
                        }
                    }
                },
                onNavigateToWorldBook = onNavigateToWorldBook,
                onNavigateToCharacterBook = onNavigateToCharacterBook
            )

            // 关系图谱
            RelationshipGraphCard(
                relationshipCount = relationshipCount,
                onViewGraph = onNavigateToRelationshipGraph
            )

            // 向量数据库
            VectorDatabaseCard(
                chromaStatus = if (viewModel.isChromaAvailable()) "运行中" else "未连接",
                neo4jStatus = if (viewModel.isNeo4jAvailable()) "运行中" else "未连接",
                onManageVectorDB = onNavigateToVectorDatabase,
                onManageGraphDB = onNavigateToGraphDatabase
            )
        }
    }

    // 迁移确认对话框
    if (showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { showMigrationDialog = false },
            title = { Text("确认迁移") },
            text = { Text("这将把旧的社交关系数据迁移到Character Book系统。此操作不会删除原有数据。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.triggerMigration()
                        showMigrationDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMigrationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 重新初始化确认对话框
    if (showReinitDialog) {
        AlertDialog(
            onDismissRequest = { showReinitDialog = false },
            title = { Text("确认重新初始化") },
            text = { Text("这将重新初始化知识系统，包括创建小光的默认角色档案。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reinitializeSystem()
                        showReinitDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReinitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 导入对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入Lorebook") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("粘贴Lorebook JSON数据：")
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
                    Text("Lorebook JSON已生成（${exportedJson.length} 字符）：")
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

    // 设置对话框
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("知识系统设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "系统配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("World Book条目")
                        Text("${systemStats?.worldEntriesCount ?: 0} 个")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Character Book角色")
                        Text("${systemStats?.characterCount ?: 0} 个")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chroma向量数据库")
                        Text(if (viewModel.isChromaAvailable()) "✓ 运行中" else "✗ 未连接")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Neo4j图数据库")
                        Text(if (viewModel.isNeo4jAvailable()) "✓ 运行中" else "✗ 未连接")
                    }

                    Divider()

                    Text(
                        text = "快捷操作",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSettingsDialog = false
                                onNavigateToVectorDatabase()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("向量DB", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                showSettingsDialog = false
                                onNavigateToGraphDatabase()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("图DB", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 系统状态卡片
 */
@Composable
fun SystemStatusCard(
    systemStats: com.xiaoguang.assistant.domain.knowledge.SystemStats?,
    uiState: KnowledgeUiState,
    onMigrate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (uiState) {
                is KnowledgeUiState.Loading -> MaterialTheme.colorScheme.surfaceVariant
                is KnowledgeUiState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "系统状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Divider()

            // 状态信息
            StatusRow(
                label = "系统状态",
                value = when (uiState) {
                    is KnowledgeUiState.Idle -> "运行正常"
                    is KnowledgeUiState.Loading -> "加载中..."
                    is KnowledgeUiState.Success -> "操作成功"
                    is KnowledgeUiState.Error -> "错误: ${uiState.message}"
                    is KnowledgeUiState.MigrationNeeded -> "需要迁移: ${uiState.message}"
                },
                isError = uiState is KnowledgeUiState.Error
            )

            if (systemStats != null) {
                StatusRow(
                    label = "初始化状态",
                    value = if (systemStats.isInitialized) "已初始化" else "未初始化"
                )

                StatusRow(
                    label = "World Book条目",
                    value = "${systemStats.worldEnabledCount} / ${systemStats.worldEntriesCount}"
                )

                StatusRow(
                    label = "角色数量",
                    value = systemStats.characterCount.toString()
                )
            } else {
                Text(
                    text = "正在加载系统统计...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * World Book统计卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldBookStatsCard(
    entries: List<com.xiaoguang.assistant.domain.knowledge.models.WorldEntry>,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Book, contentDescription = null)
                    Text(
                        text = "World Book",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onNavigate) {
                    Text("管理")
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Divider()

            // 统计数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "总条目",
                    value = entries.size.toString()
                )
                StatItem(
                    label = "已启用",
                    value = entries.count { it.enabled }.toString()
                )
                StatItem(
                    label = "已禁用",
                    value = entries.count { !it.enabled }.toString()
                )
            }

            // 分类统计
            Text(
                text = "分类分布",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorldEntryCategory.entries.forEach { category ->
                    val count = entries.count { it.category == category }
                    if (count > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${category.name}: $count") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Character Book统计卡片
 */
@Composable
fun CharacterBookStatsCard(
    profiles: List<com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile>,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null)
                    Text(
                        text = "Character Book",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onNavigate) {
                    Text("管理")
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Divider()

            // 统计数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "总角色",
                    value = profiles.size.toString()
                )
                StatItem(
                    label = "角色档案",
                    value = profiles.count { it.personality.traits.isNotEmpty() }.toString()
                )
                StatItem(
                    label = "已建档",
                    value = profiles.count { it.personality.description != null }.toString()
                )
            }
        }
    }
}

/**
 * 快速操作卡片
 */
@Composable
fun QuickActionsCard(
    onImportLorebook: () -> Unit,
    onExportLorebook: () -> Unit,
    onNavigateToWorldBook: () -> Unit,
    onNavigateToCharacterBook: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "快速操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            // 导入导出
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportLorebook,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入Lorebook")
                }

                OutlinedButton(
                    onClick = onExportLorebook,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出Lorebook")
                }
            }

            // 管理入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToWorldBook,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Book, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("World Book")
                }

                FilledTonalButton(
                    onClick = onNavigateToCharacterBook,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.People, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Character Book")
                }
            }
        }
    }
}

/**
 * 状态行组件
 */
@Composable
fun StatusRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 统计项组件
 */
@Composable
fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 关系图谱卡片
 */
@Composable
fun RelationshipGraphCard(
    relationshipCount: Int,
    onViewGraph: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "关系图谱",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider()

            // 统计信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "关系数量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$relationshipCount 条",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // 查看按钮
            FilledTonalButton(
                onClick = onViewGraph,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("查看关系图谱")
            }
        }
    }
}

/**
 * 向量数据库卡片
 */
@Composable
fun VectorDatabaseCard(
    chromaStatus: String,
    neo4jStatus: String,
    onManageVectorDB: () -> Unit,
    onManageGraphDB: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "向量与图数据库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider()

            // Chroma向量数据库
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chroma向量数据库",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "语义搜索和相似度匹配",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (chromaStatus == "运行中") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = chromaStatus,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chromaStatus == "运行中") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Neo4j图数据库
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Neo4j图数据库",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "关系图谱和知识推理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (neo4jStatus == "运行中") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = neo4jStatus,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (neo4jStatus == "运行中") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 管理按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onManageVectorDB,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Chroma", style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = onManageGraphDB,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Neo4j", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

