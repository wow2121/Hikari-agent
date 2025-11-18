package com.xiaoguang.assistant.presentation.knowledge.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.relationship.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Phase 6: 关系图谱可视化Screen
 * 使用Jetpack Compose绘制关系网络图
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipGraphScreen(
    onNavigateBack: () -> Unit,
    viewModel: RelationshipGraphViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val graphDepth by viewModel.graphDepth.collectAsState()
    val inferredRelations by viewModel.inferredRelations.collectAsState()
    val eventHistory by viewModel.eventHistory.collectAsState()
 
    var searchQuery by remember { mutableStateOf("") }
    var showInferencePanel by remember { mutableStateOf(false) }
    var showEventPanel by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关系图谱") },
                actions = {
                    // 推理按钮
                    IconButton(onClick = {
                        showInferencePanel = !showInferencePanel
                        if (showInferencePanel) {
                            viewModel.performInference(selectedPerson)
                        }
                    }) {
                        Badge(
                            containerColor = if (inferredRelations.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Transparent
                        ) {
                            if (inferredRelations.isNotEmpty()) {
                                Text(inferredRelations.size.toString())
                            }
                        }
                        Icon(Icons.Default.Psychology, "关系推理")
                    }

                    // 事件历史按钮
                    IconButton(onClick = {
                        showEventPanel = !showEventPanel
                        if (showEventPanel) {
                            viewModel.loadEventHistory(selectedPerson)
                        }
                    }) {
                        Badge(
                            containerColor = if (eventHistory.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            if (eventHistory.isNotEmpty()) {
                                Text(eventHistory.size.toString())
                            }
                        }
                        Icon(Icons.Default.History, "事件历史")
                    }

                    // 设置按钮
                    IconButton(onClick = { showSettingsPanel = !showSettingsPanel }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 搜索栏
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { viewModel.searchPeople(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                // 主内容
                when (val state = uiState) {
                    is RelationshipGraphUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is RelationshipGraphUiState.Success -> {
                        RelationshipGraphView(
                            graph = state.graph,
                            completeView = state.completeView,
                            depth = graphDepth,
                            onNodeClick = { personName ->
                                viewModel.loadRelationshipGraph(personName, graphDepth)
                            },
                            onDepthChange = { newDepth ->
                                viewModel.setGraphDepth(newDepth)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is RelationshipGraphUiState.SearchResults -> {
                        SearchResultsList(
                            results = state.results,
                            onPersonClick = { personName ->
                                viewModel.loadRelationshipGraph(personName, graphDepth)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is RelationshipGraphUiState.PathFound -> {
                        RelationshipPathView(
                            path = state.path,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is RelationshipGraphUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 推理面板
            if (showInferencePanel) {
                InferencePanel(
                    inferences = inferredRelations,
                    onDismiss = { showInferencePanel = false },
                    onApply = { threshold ->
                        viewModel.applyInferences(threshold)
                        showInferencePanel = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .width(400.dp)
                        .heightIn(max = 600.dp)
                )
            }

            // 事件历史面板
            if (showEventPanel) {
                EventHistoryPanel(
                    events = eventHistory,
                    onDismiss = { showEventPanel = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .width(400.dp)
                        .heightIn(max = 600.dp)
                )
            }

            // 设置面板
            if (showSettingsPanel) {
                SettingsPanel(
                    onDismiss = { showSettingsPanel = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .width(400.dp)
                )
            }
        }
    }
}

/**
 * 搜索栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索人物...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
        colors = TextFieldDefaults.textFieldColors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

/**
 * 关系图谱视图
 */
@Composable
fun RelationshipGraphView(
    graph: RelationshipGraph,
    completeView: CompleteRelationshipView,
    depth: Int,
    onNodeClick: (String) -> Unit,
    onDepthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 深度控制
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("图谱深度: $depth")
            Row {
                IconButton(onClick = { onDepthChange(depth - 1) }, enabled = depth > 1) {
                    Icon(Icons.Default.Remove, null)
                }
                IconButton(onClick = { onDepthChange(depth + 1) }, enabled = depth < 4) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }

        // 统计信息
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("节点", graph.totalNodes.toString())
                StatItem("连接", graph.totalEdges.toString())
                StatItem("中心", graph.centerPerson)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 图谱绘制
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRelationshipGraph(
                    graph = graph,
                    canvasSize = size,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}

/**
 * 绘制关系图谱
 */
fun DrawScope.drawRelationshipGraph(
    graph: RelationshipGraph,
    canvasSize: androidx.compose.ui.geometry.Size,
    onNodeClick: (String) -> Unit
) {
    if (graph.nodes.isEmpty()) return

    val centerX = canvasSize.width / 2
    val centerY = canvasSize.height / 2
    val radius = minOf(centerX, centerY) * 0.7f

    // 计算节点位置（圆形布局）
    val nodePositions = mutableMapOf<String, Offset>()

    graph.nodes.forEachIndexed { index, node ->
        val angle = 2 * PI * index / graph.nodes.size
        val x = centerX + radius * cos(angle).toFloat()
        val y = centerY + radius * sin(angle).toFloat()
        nodePositions[node.id] = Offset(x, y)
    }

    // 绘制边
    for (edge in graph.edges) {
        val fromPos = nodePositions[edge.from] ?: continue
        val toPos = nodePositions[edge.to] ?: continue

        val alpha = (edge.confidence * 255).toInt().coerceIn(50, 255)
        drawLine(
            color = Color(0xFF4CAF50).copy(alpha = alpha / 255f),
            start = fromPos,
            end = toPos,
            strokeWidth = 2f
        )
    }

    // 绘制节点
    for (node in graph.nodes) {
        val position = nodePositions[node.id] ?: continue

        val nodeColor = when (node.type) {
            "center" -> Color(0xFFFF5722)
            "direct" -> Color(0xFF2196F3)
            else -> Color(0xFF9E9E9E)
        }

        drawCircle(
            color = nodeColor,
            radius = 20f,
            center = position
        )

        // 简化：这里无法直接绘制文本，实际应用中需要使用Text composable
    }
}

/**
 * 统计项
 */
@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 搜索结果列表
 */
@Composable
fun SearchResultsList(
    results: List<PersonSearchResult>,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.padding(16.dp)) {
        items(results) { result ->
            PersonResultCard(
                result = result,
                onClick = { onPersonClick(result.name) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 人物结果卡片
 */
@Composable
fun PersonResultCard(
    result: PersonSearchResult,
    onClick: () -> Unit
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.name.take(1),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (result.bio != null) {
                    Text(
                        text = result.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${result.relationshipCount} 条关系",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 关系路径视图
 */
@Composable
fun RelationshipPathView(
    path: RelationshipPath,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "关系路径",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = path.toDescription())

            if (path.path.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("路径: ${path.path.joinToString(" → ")}")
            }
        }
    }
}

/**
 * 推理面板
 */
@Composable
fun InferencePanel(
    inferences: List<InferredRelation>,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var threshold by remember { mutableStateOf(0.5f) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "关系推理 (${inferences.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("置信度阈值: ${(threshold * 100).toInt()}%")
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 0f..1f
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(inferences.filter { it.confidence >= threshold }) { inference ->
                    InferenceCard(inference)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onApply(threshold) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("应用推理结果")
            }
        }
    }
}

/**
 * 推理卡片
 */
@Composable
fun InferenceCard(inference: InferredRelation) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${inference.personA} - ${inference.personB}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(inference.confidence * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = inference.inferredType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = inference.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 事件历史面板
 */
@Composable
fun EventHistoryPanel(
    events: List<EventRecord>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "事件历史 (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(events) { event ->
                    EventCard(event)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 事件卡片
 */
@Composable
fun EventCard(event: EventRecord) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.eventType,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 设置面板
 */
@Composable
fun SettingsPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Neo4j同步功能已在v2.2中移除\n现在使用纯Neo4j架构，无需同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 格式化时间戳
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        else -> "${diff / 86400000}天前"
    }
}
