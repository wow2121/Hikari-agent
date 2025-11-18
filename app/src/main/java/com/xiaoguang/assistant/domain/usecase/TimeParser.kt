package com.xiaoguang.assistant.domain.usecase

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时间表达式解析器
 * 支持中文自然语言时间表达
 */
@Singleton
class TimeParser @Inject constructor() {
    companion object {
        private const val TAG = "TimeParser"

        // 支持的日期格式
        private val DATE_FORMATS = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日",
            "MM月dd日 HH:mm",
            "MM月dd日"
        )
    }

    /**
     * 解析时间表达式
     * @param timeExpression 时间表达式(如"明天下午3点"、"2024-11-10 15:00"等)
     * @return Unix timestamp (毫秒),解析失败返回0
     */
    fun parseDateTime(timeExpression: String): Long {
        if (timeExpression.isBlank()) return 0L

        val normalized = timeExpression.trim()

        // 尝试标准格式解析
        val standardResult = parseStandardFormat(normalized)
        if (standardResult != 0L) return standardResult

        // 尝试中文自然语言解析
        return parseChineseExpression(normalized)
    }

    /**
     * 解析标准日期格式
     */
    private fun parseStandardFormat(dateStr: String): Long {
        for (format in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(format, Locale.CHINA)
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // 继续尝试其他格式
            }
        }
        return 0L
    }

    /**
     * 解析中文时间表达式
     */
    private fun parseChineseExpression(expression: String): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.clone() as Calendar

        try {
            when {
                // 今天
                expression.contains("今天") || expression.contains("今日") -> {
                    // 保持当前日期
                }

                // 明天
                expression.contains("明天") || expression.contains("明日") -> {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                // 后天
                expression.contains("后天") -> {
                    calendar.add(Calendar.DAY_OF_MONTH, 2)
                }

                // 大后天
                expression.contains("大后天") -> {
                    calendar.add(Calendar.DAY_OF_MONTH, 3)
                }

                // 昨天
                expression.contains("昨天") || expression.contains("昨日") -> {
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                }

                // 本周/这周 + 星期X
                expression.contains("本周") || expression.contains("这周") -> {
                    val dayOfWeek = extractDayOfWeek(expression)
                    if (dayOfWeek != -1) {
                        setToThisWeekDay(calendar, dayOfWeek)
                    }
                }

                // 下周 + 星期X
                expression.contains("下周") -> {
                    val dayOfWeek = extractDayOfWeek(expression)
                    if (dayOfWeek != -1) {
                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                        setToThisWeekDay(calendar, dayOfWeek)
                    }
                }

                // 下下周
                expression.contains("下下周") -> {
                    val dayOfWeek = extractDayOfWeek(expression)
                    if (dayOfWeek != -1) {
                        calendar.add(Calendar.WEEK_OF_YEAR, 2)
                        setToThisWeekDay(calendar, dayOfWeek)
                    }
                }

                // 本月/这个月
                expression.contains("本月") || expression.contains("这个月") -> {
                    val day = extractDay(expression)
                    if (day != -1) {
                        calendar.set(Calendar.DAY_OF_MONTH, day)
                    }
                }

                // 下个月
                expression.contains("下个月") || expression.contains("下月") -> {
                    calendar.add(Calendar.MONTH, 1)
                    val day = extractDay(expression)
                    if (day != -1) {
                        calendar.set(Calendar.DAY_OF_MONTH, day)
                    }
                }

                // 今年/明年
                expression.contains("今年") -> {
                    val month = extractMonth(expression)
                    val day = extractDay(expression)
                    if (month != -1) calendar.set(Calendar.MONTH, month - 1)
                    if (day != -1) calendar.set(Calendar.DAY_OF_MONTH, day)
                }

                expression.contains("明年") -> {
                    calendar.add(Calendar.YEAR, 1)
                    val month = extractMonth(expression)
                    val day = extractDay(expression)
                    if (month != -1) calendar.set(Calendar.MONTH, month - 1)
                    if (day != -1) calendar.set(Calendar.DAY_OF_MONTH, day)
                }
            }

            // 提取时间(小时和分钟)
            val time = extractTime(expression)
            if (time != null) {
                calendar.set(Calendar.HOUR_OF_DAY, time.first)
                calendar.set(Calendar.MINUTE, time.second)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            } else {
                // 没有指定具体时间,使用默认时间
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
            }

            return calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "解析中文时间表达式失败: $expression", e)
            return 0L
        }
    }

    /**
     * 提取星期几
     * @return 1-7 (周一到周日), -1表示未找到
     */
    private fun extractDayOfWeek(expression: String): Int {
        return when {
            expression.contains("周一") || expression.contains("星期一") -> Calendar.MONDAY
            expression.contains("周二") || expression.contains("星期二") -> Calendar.TUESDAY
            expression.contains("周三") || expression.contains("星期三") -> Calendar.WEDNESDAY
            expression.contains("周四") || expression.contains("星期四") -> Calendar.THURSDAY
            expression.contains("周五") || expression.contains("星期五") -> Calendar.FRIDAY
            expression.contains("周六") || expression.contains("星期六") -> Calendar.SATURDAY
            expression.contains("周日") || expression.contains("周天") ||
            expression.contains("星期日") || expression.contains("星期天") -> Calendar.SUNDAY
            else -> -1
        }
    }

    /**
     * 设置到本周的某一天
     */
    private fun setToThisWeekDay(calendar: Calendar, targetDayOfWeek: Int) {
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = targetDayOfWeek - currentDayOfWeek

        // Calendar.SUNDAY = 1, 需要特殊处理
        if (targetDayOfWeek == Calendar.SUNDAY && currentDayOfWeek != Calendar.SUNDAY) {
            daysToAdd += 7
        }

        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)
    }

    /**
     * 提取日期中的"号"
     */
    private fun extractDay(expression: String): Int {
        val dayRegex = "(\\d{1,2})号".toRegex()
        val match = dayRegex.find(expression)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /**
     * 提取月份
     */
    private fun extractMonth(expression: String): Int {
        val monthRegex = "(\\d{1,2})月".toRegex()
        val match = monthRegex.find(expression)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /**
     * 提取时间(小时和分钟)
     * @return Pair<小时, 分钟> 或 null
     */
    private fun extractTime(expression: String): Pair<Int, Int>? {
        // 匹配 "15:30" 或 "15点30分" 或 "下午3点" 等
        val patterns = listOf(
            "(\\d{1,2}):(\\d{2})".toRegex(),  // 15:30
            "(\\d{1,2})点(\\d{1,2})分?".toRegex(),  // 15点30分 或 15点30
            "(\\d{1,2})点".toRegex()  // 15点
        )

        for (pattern in patterns) {
            val match = pattern.find(expression)
            if (match != null) {
                val hour = match.groupValues[1].toIntOrNull() ?: continue
                val minute = if (match.groupValues.size > 2) {
                    match.groupValues[2].toIntOrNull() ?: 0
                } else {
                    0
                }

                // 处理上午/下午
                val adjustedHour = when {
                    expression.contains("下午") || expression.contains("晚上") -> {
                        if (hour < 12) hour + 12 else hour
                    }
                    expression.contains("上午") || expression.contains("早上") -> {
                        if (hour == 12) 0 else hour
                    }
                    expression.contains("中午") -> 12
                    else -> hour
                }

                return Pair(adjustedHour, minute)
            }
        }

        // 处理纯文字时间
        return when {
            expression.contains("早上") || expression.contains("早晨") -> Pair(8, 0)
            expression.contains("上午") -> Pair(10, 0)
            expression.contains("中午") -> Pair(12, 0)
            expression.contains("下午") -> Pair(15, 0)
            expression.contains("傍晚") -> Pair(18, 0)
            expression.contains("晚上") -> Pair(20, 0)
            expression.contains("深夜") || expression.contains("半夜") -> Pair(23, 0)
            else -> null
        }
    }

    /**
     * 格式化时间戳为可读字符串
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "无截止日期"

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        return sdf.format(Date(timestamp))
    }

    /**
     * 计算时间距离现在的相对描述
     */
    fun getRelativeTimeDescription(timestamp: Long): String {
        if (timestamp == 0L) return "无截止日期"

        val now = System.currentTimeMillis()
        val diff = timestamp - now

        return when {
            diff < 0 -> "已过期"
            diff < 3600000 -> "1小时内"
            diff < 86400000 -> "今天"
            diff < 172800000 -> "明天"
            diff < 604800000 -> "本周内"
            diff < 2592000000 -> "本月内"
            else -> formatTimestamp(timestamp)
        }
    }
}
