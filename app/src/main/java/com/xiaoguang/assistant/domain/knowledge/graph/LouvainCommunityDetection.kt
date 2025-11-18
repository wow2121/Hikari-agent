package com.xiaoguang.assistant.domain.knowledge.graph

import timber.log.Timber
import kotlin.math.max

/**
 * Louvain社群检测算法
 *
 * 算法原理：
 * 1. 模块度优化 - 通过最大化模块度Q来发现社群
 * 2. 分层聚合 - 迭代地合并节点和社群
 * 3. 贪心策略 - 每次选择最优的社群移动
 *
 * 优点：
 * - 快速：O(n log n)复杂度
 * - 分层结构：可发现多层次社群
 * - 无需预设社群数量
 *
 * 模块度Q定义：
 * Q = 1/(2m) * Σ[Aij - kikj/(2m)] * δ(ci, cj)
 * 其中：
 * - m: 边总数
 * - Aij: 节点i和j之间的边权重
 * - ki: 节点i的度数
 * - ci: 节点i所属社群
 * - δ: 如果ci=cj则为1，否则为0
 */
class LouvainCommunityDetection {

    /**
     * 图节点
     */
    data class Node(
        val id: String,
        var community: Int = -1,
        var degree: Double = 0.0
    )

    /**
     * 图边
     */
    data class Edge(
        val from: String,
        val to: String,
        val weight: Double
    )

    /**
     * 社群信息
     */
    data class Community(
        val id: Int,
        val members: MutableSet<String> = mutableSetOf(),
        var internalWeight: Double = 0.0,
        var totalWeight: Double = 0.0
    )

    /**
     * 执行Louvain算法
     *
     * @param nodes 节点列表
     * @param edges 边列表
     * @param minModularityGain 最小模块度增益阈值（默认0.0001）
     * @return 社群划分结果 Map<CommunityId, List<NodeId>>
     */
    fun detectCommunities(
        nodes: List<Node>,
        edges: List<Edge>,
        minModularityGain: Double = 0.0001
    ): Map<Int, List<String>> {
        if (nodes.isEmpty()) {
            Timber.w("[Louvain] 节点列表为空")
            return emptyMap()
        }

        Timber.d("[Louvain] 开始社群检测: ${nodes.size} 个节点, ${edges.size} 条边")

        // 1. 初始化：每个节点是一个社群
        val nodeMap = nodes.associateBy { it.id }.toMutableMap()
        val communities = initializeCommunities(nodeMap, edges)

        // 计算总边权重
        val totalWeight = edges.sumOf { it.weight }

        // 2. 迭代优化
        var iteration = 0
        var improved = true
        var currentModularity = calculateModularity(nodeMap, edges, totalWeight)

        while (improved && iteration < 100) {
            improved = false
            iteration++

            // 遍历所有节点
            for (node in nodeMap.values) {
                val oldCommunity = node.community

                // 尝试将节点移动到邻居所在的社群
                val bestCommunity = findBestCommunity(
                    node = node,
                    nodeMap = nodeMap,
                    edges = edges,
                    communities = communities,
                    totalWeight = totalWeight
                )

                // 如果找到更好的社群，移动节点
                if (bestCommunity != oldCommunity) {
                    moveNodeToCommunity(
                        node = node,
                        newCommunity = bestCommunity,
                        oldCommunity = oldCommunity,
                        communities = communities,
                        edges = edges
                    )
                    improved = true
                }
            }

            // 计算新的模块度
            val newModularity = calculateModularity(nodeMap, edges, totalWeight)
            val modularityGain = newModularity - currentModularity

            Timber.d("[Louvain] 迭代 $iteration: 模块度 = $newModularity (增益: $modularityGain)")

            if (modularityGain < minModularityGain) {
                Timber.d("[Louvain] 模块度增益过小，停止迭代")
                break
            }

            currentModularity = newModularity
        }

        // 3. 聚合结果
        val result = communities.values
            .filter { it.members.isNotEmpty() }
            .associate { it.id to it.members.toList() }

        Timber.i("[Louvain] 检测完成: 发现 ${result.size} 个社群, 最终模块度 = $currentModularity")

        return result
    }

    /**
     * 初始化社群：每个节点单独一个社群
     */
    private fun initializeCommunities(
        nodeMap: MutableMap<String, Node>,
        edges: List<Edge>
    ): MutableMap<Int, Community> {
        val communities = mutableMapOf<Int, Community>()

        // 初始化节点度数
        edges.forEach { edge ->
            nodeMap[edge.from]?.let { it.degree += edge.weight }
            nodeMap[edge.to]?.let { it.degree += edge.weight }
        }

        // 为每个节点分配独立社群
        nodeMap.values.forEachIndexed { index, node ->
            node.community = index
            communities[index] = Community(
                id = index,
                members = mutableSetOf(node.id),
                totalWeight = node.degree
            )
        }

        return communities
    }

    /**
     * 为节点寻找最佳社群
     * 通过计算模块度增益ΔQ选择最优社群
     */
    private fun findBestCommunity(
        node: Node,
        nodeMap: Map<String, Node>,
        edges: List<Edge>,
        communities: Map<Int, Community>,
        totalWeight: Double
    ): Int {
        val currentCommunity = node.community
        var bestCommunity = currentCommunity
        var maxGain = 0.0

        // 获取节点的邻居社群
        val neighborCommunities = getNeighborCommunities(node, nodeMap, edges)

        // 尝试每个邻居社群
        for ((communityId, connectionWeight) in neighborCommunities) {
            if (communityId == currentCommunity) continue

            val community = communities[communityId] ?: continue

            // 计算移动到该社群的模块度增益
            val gain = calculateModularityGain(
                node = node,
                targetCommunity = community,
                connectionWeight = connectionWeight,
                totalWeight = totalWeight
            )

            if (gain > maxGain) {
                maxGain = gain
                bestCommunity = communityId
            }
        }

        return bestCommunity
    }

    /**
     * 获取节点的邻居社群及连接权重
     */
    private fun getNeighborCommunities(
        node: Node,
        nodeMap: Map<String, Node>,
        edges: List<Edge>
    ): Map<Int, Double> {
        val neighbors = mutableMapOf<Int, Double>()

        edges.forEach { edge ->
            when {
                edge.from == node.id -> {
                    nodeMap[edge.to]?.let { neighbor ->
                        neighbors[neighbor.community] =
                            neighbors.getOrDefault(neighbor.community, 0.0) + edge.weight
                    }
                }
                edge.to == node.id -> {
                    nodeMap[edge.from]?.let { neighbor ->
                        neighbors[neighbor.community] =
                            neighbors.getOrDefault(neighbor.community, 0.0) + edge.weight
                    }
                }
            }
        }

        return neighbors
    }

    /**
     * 计算模块度增益ΔQ
     *
     * ΔQ = [Σin + ki_in]/(2m) - [Σtot + ki]²/(2m)² - [Σin/(2m) - (Σtot/(2m))² - (ki/(2m))²]
     *
     * 简化为：
     * ΔQ = [ki_in - Σtot * ki / (2m)] / (2m)
     */
    private fun calculateModularityGain(
        node: Node,
        targetCommunity: Community,
        connectionWeight: Double,
        totalWeight: Double
    ): Double {
        val ki = node.degree
        val kiIn = connectionWeight
        val sigmaTot = targetCommunity.totalWeight

        return (kiIn - sigmaTot * ki / (2 * totalWeight)) / (2 * totalWeight)
    }

    /**
     * 将节点移动到新社群
     */
    private fun moveNodeToCommunity(
        node: Node,
        newCommunity: Int,
        oldCommunity: Int,
        communities: MutableMap<Int, Community>,
        edges: List<Edge>
    ) {
        // 从旧社群移除
        communities[oldCommunity]?.let { comm ->
            comm.members.remove(node.id)
            comm.totalWeight -= node.degree
        }

        // 加入新社群
        communities[newCommunity]?.let { comm ->
            comm.members.add(node.id)
            comm.totalWeight += node.degree
        }

        // 更新节点社群
        node.community = newCommunity
    }

    /**
     * 计算整体模块度Q
     *
     * Q = Σ[lin/(2m) - (dout/(2m))²]
     * 其中：
     * - lin: 社群内部边权重
     * - dout: 社群总度数
     */
    private fun calculateModularity(
        nodeMap: Map<String, Node>,
        edges: List<Edge>,
        totalWeight: Double
    ): Double {
        if (totalWeight == 0.0) return 0.0

        val communityStats = mutableMapOf<Int, Pair<Double, Double>>() // <internal, total>

        // 统计每个社群的内部边和总度数
        edges.forEach { edge ->
            val fromNode = nodeMap[edge.from]
            val toNode = nodeMap[edge.to]

            if (fromNode != null && toNode != null) {
                val comm = fromNode.community

                // 如果是社群内部边
                if (fromNode.community == toNode.community) {
                    val (internal, total) = communityStats.getOrDefault(comm, 0.0 to 0.0)
                    communityStats[comm] = (internal + edge.weight) to total
                }
            }
        }

        // 统计总度数
        nodeMap.values.forEach { node ->
            val (internal, total) = communityStats.getOrDefault(node.community, 0.0 to 0.0)
            communityStats[node.community] = internal to (total + node.degree)
        }

        // 计算模块度
        var modularity = 0.0
        communityStats.values.forEach { (internal, total) ->
            val lin = internal / (2 * totalWeight)
            val dout = total / (2 * totalWeight)
            modularity += lin - dout * dout
        }

        return modularity
    }

    /**
     * 合并小社群
     * 将成员数少于minSize的社群合并到相邻的最大社群
     */
    fun mergeSmalCommunities(
        communities: Map<Int, List<String>>,
        edges: List<Edge>,
        minSize: Int = 2
    ): Map<Int, List<String>> {
        val result = communities.toMutableMap()
        val smallCommunities = result.filter { it.value.size < minSize }

        if (smallCommunities.isEmpty()) {
            return result
        }

        Timber.d("[Louvain] 合并 ${smallCommunities.size} 个小社群（成员 < $minSize）")

        smallCommunities.forEach { (smallCommId, members) ->
            // 找到该社群最常连接的其他社群
            val neighborCommunities = mutableMapOf<Int, Int>()

            members.forEach { memberId ->
                edges.forEach { edge ->
                    when {
                        edge.from == memberId -> {
                            result.entries.find { memberId in it.value && it.key != smallCommId }?.let { entry ->
                                neighborCommunities[entry.key] = neighborCommunities.getOrDefault(entry.key, 0) + 1
                            }
                        }
                        edge.to == memberId -> {
                            result.entries.find { memberId in it.value && it.key != smallCommId }?.let { entry ->
                                neighborCommunities[entry.key] = neighborCommunities.getOrDefault(entry.key, 0) + 1
                            }
                        }
                    }
                }
            }

            // 合并到连接最多的社群
            val targetCommId = neighborCommunities.maxByOrNull { it.value }?.key
            if (targetCommId != null) {
                result[targetCommId] = result[targetCommId]!! + members
                result.remove(smallCommId)
            }
        }

        return result
    }
}
