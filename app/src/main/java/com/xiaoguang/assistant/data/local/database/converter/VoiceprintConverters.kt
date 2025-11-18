package com.xiaoguang.assistant.data.local.database.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room类型转换器 - 声纹数据
 */
class VoiceprintConverters {

    private val gson = Gson()

    /**
     * FloatArray -> String (JSON)
     */
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String {
        return if (value == null) {
            "[]"
        } else {
            gson.toJson(value.toList())
        }
    }

    /**
     * String (JSON) -> FloatArray
     */
    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        return try {
            val type = object : TypeToken<List<Float>>() {}.type
            val list: List<Float> = gson.fromJson(value, type)
            list.toFloatArray()
        } catch (e: Exception) {
            floatArrayOf()
        }
    }
}
