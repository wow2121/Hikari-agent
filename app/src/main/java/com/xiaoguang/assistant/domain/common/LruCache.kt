package com.xiaoguang.assistant.domain.common

import timber.log.Timber
import java.util.LinkedHashMap

/**
 * LRU（Least Recently Used）缓存实现
 * 基于LinkedHashMap实现，线程安全
 *
 * 特性：
 * - 固定大小，超出后自动淘汰最久未使用的条目
 * - 线程安全
 * - 支持TTL（Time To Live）过期机制
 * - 统计命中率
 */
class LruCache<K, V>(
    private val maxSize: Int,
    private val ttlMs: Long? = null,  // 条目存活时间（毫秒），null表示永不过期
    private val name: String = "UnnamedCache"
) {
    private val cache = object : LinkedHashMap<K, CacheEntry<V>>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheEntry<V>>?): Boolean {
            val shouldRemove = size > maxSize
            if (shouldRemove && eldest != null) {
                Timber.v("[$name] 淘汰最久未使用条目: ${eldest.key}")
            }
            return shouldRemove
        }
    }

    // 统计信息
    private var hitCount = 0L
    private var missCount = 0L
    private var putCount = 0L
    private var evictionCount = 0L

    /**
     * 获取缓存值
     */
    @Synchronized
    fun get(key: K): V? {
        val entry = cache[key]

        if (entry == null) {
            missCount++
            return null
        }

        // 检查是否过期
        if (ttlMs != null && System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            missCount++
            evictionCount++
            Timber.v("[$name] 条目已过期: $key")
            return null
        }

        hitCount++
        return entry.value
    }

    /**
     * 存入缓存
     */
    @Synchronized
    fun put(key: K, value: V) {
        val entry = CacheEntry(value, System.currentTimeMillis())
        cache[key] = entry
        putCount++
    }

    /**
     * 移除缓存
     */
    @Synchronized
    fun remove(key: K): V? {
        val entry = cache.remove(key)
        return entry?.value
    }

    /**
     * 清空缓存
     */
    @Synchronized
    fun clear() {
        cache.clear()
        Timber.d("[$name] 缓存已清空")
    }

    /**
     * 获取缓存大小
     */
    @Synchronized
    fun size(): Int = cache.size

    /**
     * 检查是否包含key
     */
    @Synchronized
    fun containsKey(key: K): Boolean {
        val entry = cache[key] ?: return false

        // 检查是否过期
        if (ttlMs != null && System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return false
        }

        return true
    }

    /**
     * 获取统计信息
     */
    @Synchronized
    fun getStats(): CacheStats {
        return CacheStats(
            name = name,
            size = cache.size,
            maxSize = maxSize,
            hitCount = hitCount,
            missCount = missCount,
            putCount = putCount,
            evictionCount = evictionCount,
            hitRate = if (hitCount + missCount > 0) {
                hitCount.toDouble() / (hitCount + missCount)
            } else {
                0.0
            }
        )
    }

    /**
     * 打印统计信息
     */
    fun logStats() {
        val stats = getStats()
        Timber.i(
            "[$name] 缓存统计: 大小=${stats.size}/${stats.maxSize}, " +
                    "命中率=${String.format("%.2f%%", stats.hitRate * 100)}, " +
                    "命中=${stats.hitCount}, 未命中=${stats.missCount}, " +
                    "写入=${stats.putCount}, 淘汰=${stats.evictionCount}"
        )
    }

    /**
     * 清理过期条目
     */
    @Synchronized
    fun cleanupExpired(): Int {
        if (ttlMs == null) return 0

        var removed = 0
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > ttlMs) {
                iterator.remove()
                removed++
            }
        }

        if (removed > 0) {
            evictionCount += removed
            Timber.d("[$name] 清理了${removed}个过期条目")
        }

        return removed
    }

    /**
     * 缓存条目（包含时间戳）
     */
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long
    )

    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val name: String,
        val size: Int,
        val maxSize: Int,
        val hitCount: Long,
        val missCount: Long,
        val putCount: Long,
        val evictionCount: Long,
        val hitRate: Double
    )
}

/**
 * 带加载器的LRU缓存
 * 自动加载缺失的值
 */
class LoadingLruCache<K, V>(
    maxSize: Int,
    ttlMs: Long? = null,
    name: String = "LoadingCache",
    private val loader: suspend (K) -> V?
) {
    private val cache = LruCache<K, V>(maxSize, ttlMs, name)

    /**
     * 获取值，如果不存在则自动加载
     */
    suspend fun get(key: K): V? {
        val cached = cache.get(key)
        if (cached != null) {
            return cached
        }

        // 加载
        val loaded = loader(key) ?: return null
        cache.put(key, loaded)
        return loaded
    }

    /**
     * 显式放入缓存
     */
    fun put(key: K, value: V) {
        cache.put(key, value)
    }

    /**
     * 使指定key失效
     */
    fun invalidate(key: K) {
        cache.remove(key)
    }

    /**
     * 清空缓存
     */
    fun invalidateAll() {
        cache.clear()
    }

    /**
     * 获取统计信息
     */
    fun getStats() = cache.getStats()

    /**
     * 打印统计信息
     */
    fun logStats() = cache.logStats()
}
