package com.xiaoguang.assistant.data.local.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/**
 * Room数据库类型转换器
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        if (value == null) return null
        return try {
            val type = object : TypeToken<List<Float>>() {}.type
            val result: List<Float>? = gson.fromJson(value, type)
            result
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to parse FloatList from: ${value.take(100)}")
            null  // 返回null而不是崩溃
        } catch (e: ClassCastException) {
            Timber.e(e, "ClassCastException when parsing FloatList from: ${value.take(100)}")
            null
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val result: List<String>? = gson.fromJson(value, type)
            result
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to parse StringList from: ${value.take(100)}")
            null
        } catch (e: ClassCastException) {
            Timber.e(e, "ClassCastException when parsing StringList from: ${value.take(100)}")
            null
        }
    }

    @TypeConverter
    fun fromMap(value: Map<String, String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toMap(value: String?): Map<String, String>? {
        if (value == null) return null
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val result: Map<String, String>? = gson.fromJson(value, type)
            result
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to parse Map from: ${value.take(100)}")
            null
        } catch (e: ClassCastException) {
            Timber.e(e, "ClassCastException when parsing Map from: ${value.take(100)}")
            null
        }
    }
}
