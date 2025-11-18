package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.domain.model.RelationshipChangeNotification
import com.xiaoguang.assistant.domain.model.RelationshipLevel
import com.xiaoguang.assistant.domain.usecase.RelationshipEventManagementUseCase
import com.xiaoguang.assistant.domain.social.UnifiedSocialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系变化观察者
 *
 * 监听人际关系的变化并触发相应的事件：
 * 1. 关系升级/降级
 * 2. 好感度大幅变化
 * 3. 长时间未互动
 * 4. 纪念日提醒
 */
@Singleton
class RelationshipChangeObserver @Inject constructor(
    private val unifiedSocialManager: UnifiedSocialManager,
    private val relationshipEventManagementUseCase: RelationshipEventManagementUseCase
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 关系变化通知流
    private val _changeNotifications = MutableSharedFlow<RelationshipChangeNotification>()
    val changeNotifications: SharedFlow<RelationshipChangeNotification> = _changeNotifications.asSharedFlow()

    // 缓存上一次的关系层级
    private val lastLevels = mutableMapOf<String, RelationshipLevel>()

    /**
     * 检查并触发关系变化
     *
     * 应在好感度更新后调用
     */
    suspend fun checkAndTriggerChanges(
        personName: String,
        oldAffection: Int,
        newAffection: Int,
        isMaster: Boolean
    ) {
        // 主人不会有关系变化
        if (isMaster) return

        val delta = newAffection - oldAffection

        // 1. 检查关系层级变化
        checkLevelChange(personName, oldAffection, newAffection)

        // 2. 检查好感度大幅变化
        if (kotlin.math.abs(delta) >= 10) {
            val notification = RelationshipChangeNotification.AffectionSignificantChange(
                person = personName,
                oldAffection = oldAffection,
                newAffection = newAffection,
                delta = delta
            )
            _changeNotifications.emit(notification)
            Timber.d("好感度大幅变化: $personName ($oldAffection -> $newAffection)")

            // 触发小光的反应
            triggerXiaoguangReaction(personName, delta)
        }
    }

    /**
     * 检查关系层级变化
     */
    private suspend fun checkLevelChange(
        personName: String,
        oldAffection: Int,
        newAffection: Int
    ) {
        val oldLevel = RelationshipLevel.fromAffection(oldAffection)
        val newLevel = RelationshipLevel.fromAffection(newAffection)

        // 更新缓存
        val cachedOldLevel = lastLevels[personName] ?: oldLevel
        lastLevels[personName] = newLevel

        // 如果层级发生变化
        if (cachedOldLevel != newLevel) {
            if (newLevel.minAffection > cachedOldLevel.minAffection) {
                // 升级
                val notification = RelationshipChangeNotification.LevelUp(
                    person = personName,
                    oldLevel = cachedOldLevel,
                    newLevel = newLevel
                )
                _changeNotifications.emit(notification)

                // 记录里程碑事件
                relationshipEventManagementUseCase.recordMilestone(
                    personName = personName,
                    oldLevel = cachedOldLevel.displayName,
                    newLevel = newLevel.displayName
                )

                Timber.d("关系升级: $personName (${cachedOldLevel.displayName} -> ${newLevel.displayName})")
            } else {
                // 降级
                val notification = RelationshipChangeNotification.LevelDown(
                    person = personName,
                    oldLevel = cachedOldLevel,
                    newLevel = newLevel,
                    reason = "好感度下降"
                )
                _changeNotifications.emit(notification)
                Timber.d("关系降级: $personName (${cachedOldLevel.displayName} -> ${newLevel.displayName})")
            }
        }
    }

    /**
     * 触发小光对好感度变化的反应
     */
    private fun triggerXiaoguangReaction(personName: String, delta: Int) {
        scope.launch {
            when {
                delta >= 15 -> {
                    // 好感度大幅增加 → 小光很开心
                    Timber.d("小光的反应: 对$personName 的好感度大增，很开心！")
                    // TODO: 触发小光说些开心的话
                }
                delta >= 10 -> {
                    // 好感度增加 → 小光开心
                    Timber.d("小光的反应: 感觉和$personName 的关系更好了呢~")
                }
                delta <= -15 -> {
                    // 好感度大幅下降 → 小光不太开心
                    Timber.d("小光的反应: 感觉和$personName 的关系变差了...")
                }
                delta <= -10 -> {
                    // 好感度下降 → 小光有点难过
                    Timber.d("小光的反应: 嗯...好像不太对劲...")
                }
            }
        }
    }

    /**
     * 检查长时间未互动
     *
     * 应定期调用（如每天一次）
     */
    suspend fun checkLongTimeNoInteraction() {
        val relations = unifiedSocialManager.getAllRelations()
        val now = System.currentTimeMillis()

        for (relation in relations) {
            val daysSinceLastInteraction = (now - relation.lastInteractionAt) / (1000 * 60 * 60 * 24)

            if (daysSinceLastInteraction >= 7) {  // 一周未互动
                val notification = RelationshipChangeNotification.LongTimeNoInteraction(
                    person = relation.personName,
                    daysSinceLastInteraction = daysSinceLastInteraction.toInt()
                )
                _changeNotifications.emit(notification)

                // 记录事件
                if (daysSinceLastInteraction >= 30) {
                    relationshipEventManagementUseCase.recordLongAbsence(
                        personName = relation.personName,
                        days = daysSinceLastInteraction.toInt()
                    )
                }

                Timber.d("长时间未互动: ${relation.personName} (${daysSinceLastInteraction}天)")
            }
        }
    }

    /**
     * 生成小光对关系变化的评论
     */
    fun generateCommentOnChange(notification: RelationshipChangeNotification): String {
        return when (notification) {
            is RelationshipChangeNotification.LevelUp -> {
                val person = notification.person
                val newLevel = notification.newLevel
                when (newLevel) {
                    RelationshipLevel.ACQUAINTANCE -> "诶，感觉和${person}渐渐熟悉起来了呢~"
                    RelationshipLevel.FRIEND -> "太好了！和${person}成为朋友了！"
                    RelationshipLevel.GOOD_FRIEND -> "和${person}的关系变得好好哦，成为好朋友啦！"
                    RelationshipLevel.BEST_FRIEND -> "哇！${person}已经是小光的挚友了！好开心~"
                    else -> "和${person}的关系越来越好了呢！"
                }
            }

            is RelationshipChangeNotification.LevelDown -> {
                val person = notification.person
                "唔...和${person}的关系好像有点疏远了呢..."
            }

            is RelationshipChangeNotification.AffectionSignificantChange -> {
                val person = notification.person
                val delta = notification.delta
                when {
                    delta >= 15 -> "对${person}的好感度大增！小光好开心~"
                    delta >= 10 -> "感觉和${person}的关系更好了呢！"
                    delta <= -15 -> "和${person}的关系变差了...是小光哪里做得不好吗..."
                    delta <= -10 -> "嗯...好像哪里不太对劲..."
                    else -> ""
                }
            }

            is RelationshipChangeNotification.LongTimeNoInteraction -> {
                val person = notification.person
                val days = notification.daysSinceLastInteraction
                when {
                    days >= 30 -> "已经一个月没见到${person}了...好想念..."
                    days >= 14 -> "两周没见到${person}了呢，还好吗？"
                    days >= 7 -> "好久没见到${person}了呀~"
                    else -> ""
                }
            }

            is RelationshipChangeNotification.Anniversary -> {
                val person = notification.person
                val event = notification.event
                "今天是和${person}${event}的纪念日呢！"
            }
        }
    }
}
