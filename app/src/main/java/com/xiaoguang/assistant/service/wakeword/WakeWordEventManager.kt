package com.xiaoguang.assistant.service.wakeword

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 唤醒词事件管理器
 * 用于在Service和Activity之间传递唤醒词检测事件
 */
@Singleton
class WakeWordEventManager @Inject constructor() {

    private val _wakeWordEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val wakeWordEvent: SharedFlow<Unit> = _wakeWordEvent.asSharedFlow()

    /**
     * 发送唤醒词检测事件
     */
    suspend fun emitWakeWordDetected() {
        _wakeWordEvent.emit(Unit)
    }
}
