package com.xiaoguang.assistant.core.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统集成使用示例
 *
 * 展示如何在应用启动时进行系统健康检查,确保所有子系统完美配合
 *
 * 使用场景:
 * 1. 应用启动时 - MainActivity.onCreate()
 * 2. 配置更改后 - 验证新配置是否生效
 * 3. 定期健康检查 - 后台定时任务
 * 4. 调试诊断 - 开发者选项中手动触发
 */
@Singleton
class SystemIntegrationExample @Inject constructor(
    private val verifier: SystemIntegrationVerifier
) {

    /**
     * 示例1: 应用启动时的完整健康检查
     *
     * 在MainActivity或Application类中调用:
     * ```kotlin
     * class MainActivity : AppCompatActivity() {
     *     @Inject lateinit var integrationExample: SystemIntegrationExample
     *
     *     override fun onCreate(savedInstanceState: Bundle?) {
     *         super.onCreate(savedInstanceState)
     *         lifecycleScope.launch {
     *             integrationExample.performStartupHealthCheck()
     *         }
     *     }
     * }
     * ```
     */
    suspend fun performStartupHealthCheck(): Boolean {
        Timber.i("========== 系统启动健康检查 ==========")

        val result = verifier.performHealthCheck()

        // 打印详细报告
        Timber.i(result.getReport())

        // 如果健康检查失败,打印失败组件
        if (!result.healthy) {
            val failedComponents = result.getFailedComponents()
            Timber.e("❌ 以下组件检查失败:")
            failedComponents.forEach { component ->
                val status = result.components[component]
                Timber.e("  - $component: ${status?.message}")
                status?.error?.let { e ->
                    Timber.e("    错误详情: ${e.message}", e)
                }
            }

            // 可以选择弹出对话框通知用户
            // showSystemHealthDialog(failedComponents)
        } else {
            Timber.i("✅ 所有系统组件运行正常")
        }

        return result.healthy
    }

    /**
     * 示例2: 获取性能报告
     *
     * 在设置页面或开发者选项中显示:
     * ```kotlin
     * fun showPerformanceReport() {
     *     val report = integrationExample.getPerformanceReport()
     *     // 显示在UI中或记录到日志
     * }
     * ```
     */
    fun getPerformanceReport(): PerformanceReport {
        val report = verifier.getPerformanceReport()

        Timber.i("========== 系统性能报告 ==========")

        // 向量搜索优化
        report.vectorSearchOptimization?.let { stats ->
            Timber.i("向量搜索优化:")
            Timber.i("  - 总向量数: ${stats["total_vectors"]}")
            Timber.i("  - 哈希表数: ${stats["hash_tables"]}")
            Timber.i("  - 总桶数: ${stats["buckets"]}")
            Timber.i("  - 平均桶大小: ${stats["avg_bucket_size"]}")
            Timber.i("  - 缓存命中率: ${stats["cache_hit_rate"]}")
        }

        // 缓存配置
        Timber.i("缓存配置:")
        Timber.i("  - 嵌入缓存大小: ${report.cacheConfig["embedding_cache_size"]}")
        Timber.i("  - 查询缓存大小: ${report.cacheConfig["query_cache_size"]}")
        Timber.i("  - 缓存启用: ${report.cacheConfig["enabled"]}")

        // 模型状态
        Timber.i("模型状态:")
        Timber.i("  - 声纹特征维度: ${report.modelStatus["voiceprint_dim"]}")
        Timber.i("  - 说话人分离已初始化: ${report.modelStatus["diarization_initialized"]}")
        Timber.i("  - 分词模式: ${report.modelStatus["segmentation_mode"]}")

        // 分词选项对比
        Timber.i("分词选项:")
        report.segmentationOptions.forEach { (name, config) ->
            Timber.i("  $name:")
            Timber.i("    - 准确率: ${config["accuracy"]}")
            Timber.i("    - 速度: ${config["speed"]}")
            Timber.i("    - 离线: ${config["offline"]}")
            Timber.i("    - 使用场景: ${config["use_case"]}")
        }

        return report
    }

    /**
     * 示例3: 检查特定子系统
     *
     * 当只需要验证某个特定功能时:
     * ```kotlin
     * fun checkNLPOnly() {
     *     lifecycleScope.launch {
     *         val result = integrationExample.performStartupHealthCheck()
     *         val nlpStatus = result.components["nlp_pipeline"]
     *         if (nlpStatus?.healthy == true) {
     *             // NLP系统正常
     *         }
     *     }
     * }
     * ```
     */
    suspend fun checkSpecificComponent(componentName: String): ComponentStatus? {
        val result = verifier.performHealthCheck()
        val status = result.components[componentName]

        status?.let {
            Timber.i("========== $componentName 检查结果 ==========")
            Timber.i("状态: ${if (it.healthy) "✅ 正常" else "❌ 异常"}")
            Timber.i("消息: ${it.message}")
            it.details?.let { details ->
                Timber.i("详情:")
                details.forEach { (key, value) ->
                    Timber.i("  - $key: $value")
                }
            }
        }

        return status
    }

    /**
     * 示例4: 轻量级快速检查
     *
     * 用于频繁检查但不希望执行完整测试的场景:
     */
    suspend fun quickHealthCheck(): Boolean {
        // 只检查关键组件是否可访问
        val result = verifier.performHealthCheck()

        // 快速判断:配置、NLP、知识系统是否健康
        val criticalComponents = listOf("configuration", "nlp_pipeline", "knowledge_system")
        val criticalHealthy = criticalComponents.all {
            result.components[it]?.healthy == true
        }

        if (criticalHealthy) {
            Timber.i("✅ 核心系统健康")
        } else {
            Timber.w("⚠️ 部分核心系统异常")
        }

        return criticalHealthy
    }

    /**
     * 示例5: 集成测试端到端流程
     *
     * 模拟完整的用户对话流程,验证各系统配合:
     * ```kotlin
     * fun testEndToEndFlow() {
     *     lifecycleScope.launch {
     *         integrationExample.testCompleteUserFlow()
     *     }
     * }
     * ```
     */
    suspend fun testCompleteUserFlow(): Boolean {
        Timber.i("========== 端到端流程测试 ==========")

        val result = verifier.performHealthCheck()

        // 验证数据流集成
        val dataFlowStatus = result.components["data_flow"]
        val flowWorking = dataFlowStatus?.healthy == true

        if (flowWorking) {
            Timber.i("✅ 完整数据流正常:")
            dataFlowStatus?.details?.let { details ->
                Timber.i("  流程: ${details["pipeline"]}")
                Timber.i("  分词数: ${details["words_count"]}")
                Timber.i("  关键词: ${details["keywords"]}")
                Timber.i("  检索Token数: ${details["context_tokens"]}")
                Timber.i("  社群数: ${details["communities"]}")
            }
        } else {
            Timber.e("❌ 数据流异常: ${dataFlowStatus?.message}")
        }

        return flowWorking
    }
}

/**
 * 使用建议:
 *
 * 1. 应用启动时:
 *    - 调用 performStartupHealthCheck() 确保系统可用
 *    - 如果失败,显示友好的错误信息给用户
 *
 * 2. 开发调试时:
 *    - 使用 getPerformanceReport() 查看系统性能指标
 *    - 使用 checkSpecificComponent() 定位问题组件
 *
 * 3. 生产环境:
 *    - 使用 quickHealthCheck() 做轻量级定期检查
 *    - 记录失败的健康检查到分析服务
 *
 * 4. 集成测试:
 *    - 使用 testCompleteUserFlow() 验证端到端流程
 *    - 确保所有系统完美配合
 */

/**
 * 完整集成示例 - 在Application中初始化
 *
 * ```kotlin
 * @HiltAndroidApp
 * class MyApplication : Application() {
 *     @Inject lateinit var integrationExample: SystemIntegrationExample
 *
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         // 启动时执行健康检查
 *         GlobalScope.launch(Dispatchers.IO) {
 *             val healthy = integrationExample.performStartupHealthCheck()
 *
 *             if (!healthy) {
 *                 // 记录到分析服务
 *                 Analytics.logEvent("system_health_check_failed")
 *             }
 *
 *             // 获取性能报告
 *             integrationExample.getPerformanceReport()
 *         }
 *     }
 * }
 * ```
 */
