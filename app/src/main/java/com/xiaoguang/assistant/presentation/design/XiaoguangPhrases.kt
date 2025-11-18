package com.xiaoguang.assistant.presentation.design

/**
 * 小光文案库
 *
 * 包含所有UI文本，体现小光温暖、可爱、有点迷糊但贴心的性格特征。
 * 所有文案都使用第一人称，拉近与用户的距离。
 */
object XiaoguangPhrases {

    /**
     * 声纹识别相关文案
     */
    object VoiceRecognition {
        // 识别状态
        const val LISTENING = "在听呢..."
        const val RECOGNIZING = "让我想想这是谁的声音..."
        const val IDENTIFIED = "我认出来了！"
        const val STRANGER_DETECTED = "诶，好像有新朋友？"
        const val FAILED = "唔...我好像没听清楚"

        // 新朋友注册
        const val NEW_FRIEND_GREETING = "你好呀！我是小光~"
        const val ASK_NAME = "能告诉我你叫什么名字吗？"
        const val INFERRING_NAME = "我试试能不能听出来..."
        const val NAME_INFERRED = "你是%s对吧？"  // %s = 推断的名字
        const val CONFIRM_NAME = "确认一下，我该怎么称呼你呢？"
        const val RECORDING_VOICE = "我要记住你的声音啦~"
        const val VOICE_RECORDED = "好啦！下次我就能认出你了！"

        // 熟人识别
        const val RECOGNIZED = "是%s！"  // %s = 人名
        const val WELCOME_BACK = "欢迎回来，%s~"  // %s = 人名
        const val LONG_TIME_NO_SEE = "好久不见了，%s！"  // %s = 人名

        // 错误提示（温暖语气）
        const val MIC_PERMISSION_NEEDED = "我需要麦克风权限才能认出你的声音哦..."
        const val RECORDING_FAILED = "哎呀，录音好像出了点问题..."
        const val VOICE_TOO_SHORT = "刚才说得太短啦，能再说一遍吗？"
        const val VOICE_TOO_QUIET = "声音有点小呢，能大声一点吗？"
        const val TOO_NOISY = "周围好像有点吵，能换个安静的地方吗？"
    }

    /**
     * 情绪状态文案
     */
    object Emotion {
        // 情绪描述
        const val HAPPY = "心情不错~"
        const val EXCITED = "好开心！"
        const val CALM = "很平静"
        const val TIRED = "有点困了..."
        const val CURIOUS = "好奇中..."
        const val CONFUSED = "有点迷糊..."
        const val SURPRISED = "诶！？"
        const val SAD = "有点难过..."
        const val ANXIOUS = "有点担心..."
        const val FRUSTRATED = "啊...有点烦..."

        // 情绪变化提示
        const val EMOTION_CHANGED = "我现在%s"  // %s = 情绪描述
        const val WHY_HAPPY = "因为%s，我很开心~"  // %s = 原因
        const val WHY_SAD = "因为%s，我有点难过..."  // %s = 原因
        const val WHY_CURIOUS = "%s，让我很好奇！"  // %s = 原因
    }

    /**
     * 心流系统文案
     */
    object Flow {
        // 状态描述
        const val THINKING = "我在想..."
        const val ANALYZING = "让我想想..."
        const val DECIDING = "该做什么好呢..."
        const val ACTING = "我来说点什么吧~"

        // 心流冲动
        const val WANT_TO_SPEAK = "我想说话！"
        const val SHOULD_STAY_QUIET = "还是先不说话吧..."
        const val STRONG_IMPULSE = "我真的很想说点什么！"
        const val WEAK_IMPULSE = "好像没什么特别想说的..."

        // 感知环境
        const val PERCEIVING = "感觉到..."
        const val NOTICED = "我注意到%s"  // %s = 感知到的事物
        const val ENVIRONMENT_CHANGED = "环境好像变了..."
    }

    /**
     * 对话相关文案
     */
    object Conversation {
        // 对话状态
        const val LISTENING_TO_YOU = "在听你说~"
        const val THINKING_RESPONSE = "让我想想怎么回答..."
        const val GENERATING_REPLY = "我在组织语言..."
        const val SPEAKING = "小光说："

        // 对话操作
        const val START_CONVERSATION = "和我聊聊天吧~"
        const val CONTINUE_TALKING = "继续说呀~"
        const val END_CONVERSATION = "好的！"
        const val CLEAR_HISTORY = "要清空聊天记录吗？"

        // 错误提示
        const val NETWORK_ERROR = "网络好像断了...我暂时想不出答案..."
        const val API_ERROR = "我的大脑好像卡住了...要不等会儿再试试？"
        const val TIMEOUT = "想得太久了...我有点迷糊了..."
        const val EMPTY_INPUT = "你还没说话呢~"
    }

    /**
     * 记忆系统文案
     */
    object Memory {
        // 记忆操作
        const val REMEMBERING = "我记住了~"
        const val RECALLING = "让我想想..."
        const val FORGOT = "唔...我好像忘了..."
        const val MEMORY_SAVED = "记在心里啦！"

        // 记忆类型
        const val EPISODIC_MEMORY = "我记得那次..."
        const val SEMANTIC_MEMORY = "我知道..."
        const val PROCEDURAL_MEMORY = "这个我会做！"

        // 记忆搜索
        const val SEARCHING_MEMORY = "在记忆里找找..."
        const val FOUND_MEMORY = "想起来了！"
        const val NO_MEMORY = "好像没有相关的记忆..."
        const val MEMORY_CONFLICT = "咦，这个和我之前记得的不一样..."

        // 记忆重构
        const val RECONSTRUCTING = "让我重新理解一下..."
        const val UPDATED_MEMORY = "更新了我的理解~"
    }

    /**
     * 社交关系文案
     */
    object Social {
        // 人物管理
        const val ALL_FRIENDS = "我认识的人"
        const val MASTER = "我的主人"
        const val CLOSE_FRIEND = "亲密的朋友"
        const val FRIEND = "朋友"
        const val ACQUAINTANCE = "认识的人"
        const val STRANGER = "陌生人"

        // 关系描述
        const val RELATIONSHIP_WITH = "%s和%s的关系"  // %s1 = 人物1, %s2 = 人物2
        const val KNOWS = "%s认识%s"
        const val CLOSE_TO = "%s和%s很亲近"
        const val MET_RECENTLY = "最近见过"
        const val LONG_TIME_NO_SEE = "好久没见了"

        // 社交事件
        const val MET_NEW_PERSON = "认识了新朋友：%s"  // %s = 名字
        const val RELATIONSHIP_UPDATED = "更新了和%s的关系"  // %s = 名字
        const val FOUND_RELATIONSHIP = "发现了新的人际关系~"
    }

    /**
     * 日程与提醒文案
     */
    object Schedule {
        // 提醒类型
        const val REMINDER = "提醒你一下~"
        const val UPCOMING_EVENT = "马上要%s了哦"  // %s = 事件
        const val OVERDUE = "%s好像过期了..."  // %s = 事件
        const val COMPLETED = "完成了~"

        // 时间描述
        const val NOW = "现在"
        const val SOON = "马上"
        const val IN_MINUTES = "%d分钟后"  // %d = 分钟数
        const val IN_HOURS = "%d小时后"  // %d = 小时数
        const val TOMORROW = "明天"
        const val NEXT_WEEK = "下周"

        // 日程操作
        const val ADD_EVENT = "帮你记下来了！"
        const val REMOVE_EVENT = "删掉了~"
        const val UPDATE_EVENT = "更新好了！"
    }

    /**
     * 系统状态文案
     */
    object System {
        // 初始化
        const val INITIALIZING = "我正在醒来..."
        const val READY = "我准备好啦！"
        const val LOADING = "加载中..."

        // 权限请求（温暖语气）
        const val NEED_PERMISSION = "我需要%s权限才能帮你哦..."  // %s = 权限名称
        const val MICROPHONE_PERMISSION = "麦克风权限"
        const val NOTIFICATION_PERMISSION = "通知权限"
        const val STORAGE_PERMISSION = "存储权限"
        const val LOCATION_PERMISSION = "位置权限"

        // 错误提示（避免技术术语）
        const val ERROR_OCCURRED = "哎呀，出了点小问题..."
        const val TRY_AGAIN = "要不再试一次？"
        const val CONTACT_SUPPORT = "如果一直这样，可能需要找主人看看..."

        // 网络状态
        const val ONLINE = "网络连上了~"
        const val OFFLINE = "网络好像断了..."
        const val SLOW_NETWORK = "网络有点慢呢..."

        // 更新
        const val UPDATE_AVAILABLE = "有新版本啦~"
        const val UPDATING = "正在更新..."
        const val UPDATE_COMPLETE = "更新完成！"
    }

    /**
     * 设置相关文案
     */
    object Settings {
        // 分类
        const val APPEARANCE = "外观设置"
        const val BEHAVIOR = "行为设置"
        const val VOICE = "语音设置"
        const val NOTIFICATION = "通知设置"
        const val PRIVACY = "隐私设置"
        const val ABOUT = "关于小光"

        // 开关
        const val ENABLED = "开启"
        const val DISABLED = "关闭"
        const val DEFAULT = "默认"

        // 主题
        const val LIGHT_THEME = "明亮模式"
        const val DARK_THEME = "暗黑模式"
        const val FOLLOW_SYSTEM = "跟随系统"

        // 确认操作
        const val CONFIRM_RESET = "要重置所有设置吗？"
        const val CONFIRM_DELETE = "真的要删除吗？"
        const val CANNOT_UNDO = "这个操作不能撤销哦..."
    }

    /**
     * 通用操作文案
     */
    object Common {
        // 按钮
        const val OK = "好的"
        const val CANCEL = "算了"
        const val CONFIRM = "确认"
        const val DELETE = "删除"
        const val SAVE = "保存"
        const val EDIT = "编辑"
        const val BACK = "返回"
        const val NEXT = "下一步"
        const val DONE = "完成"
        const val RETRY = "重试"
        const val SKIP = "跳过"

        // 状态
        const val LOADING = "加载中..."
        const val EMPTY = "这里还什么都没有呢..."
        const val NO_RESULTS = "没找到相关的内容..."
        const val SUCCESS = "成功啦！"
        const val FAILED = "失败了..."

        // 时间
        const val JUST_NOW = "刚刚"
        const val MINUTES_AGO = "%d分钟前"  // %d = 分钟数
        const val HOURS_AGO = "%d小时前"  // %d = 小时数
        const val DAYS_AGO = "%d天前"  // %d = 天数
        const val WEEKS_AGO = "%d周前"  // %d = 周数

        // 提示
        const val TAP_TO_VIEW = "点击查看"
        const val SWIPE_TO_DELETE = "滑动删除"
        const val LONG_PRESS = "长按"
        const val PULL_TO_REFRESH = "下拉刷新"
    }

    /**
     * 欢迎和问候语
     */
    object Greeting {
        // 时段问候
        const val GOOD_MORNING = "早上好呀~"
        const val GOOD_AFTERNOON = "下午好~"
        const val GOOD_EVENING = "晚上好~"
        const val GOOD_NIGHT = "晚安，好好休息~"

        // 首次见面
        const val FIRST_TIME = "你好！我是小光，很高兴认识你~"
        const val INTRODUCE_SELF = "我是一个AI助手，会尽力帮助你、陪伴你~"

        // 回归问候
        const val WELCOME_BACK = "欢迎回来！"
        const val MISSED_YOU = "好久不见，想你了~"

        // 特殊时刻
        const val BIRTHDAY = "生日快乐！"
        const val CONGRATS = "恭喜恭喜~"
        const val CHEER_UP = "加油！"
    }

    /**
     * 开发者模式文案
     */
    object Developer {
        // 调试功能
        const val DEBUG_MODE = "开发者模式"
        const val FLOW_DEBUG = "心流调试"
        const val MEMORY_DEBUG = "记忆调试"
        const val VOICE_DEBUG = "语音调试"
        const val NETWORK_DEBUG = "网络调试"

        // 日志
        const val VIEW_LOGS = "查看日志"
        const val CLEAR_LOGS = "清空日志"
        const val EXPORT_LOGS = "导出日志"

        // 数据管理
        const val CLEAR_DATA = "清空数据"
        const val EXPORT_DATA = "导出数据"
        const val IMPORT_DATA = "导入数据"

        // 警告
        const val DEVELOPER_WARNING = "这些功能仅供开发者使用，请谨慎操作！"
        const val DATA_LOSS_WARNING = "此操作可能导致数据丢失！"
    }
}
