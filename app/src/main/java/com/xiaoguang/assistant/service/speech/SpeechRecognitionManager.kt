package com.xiaoguang.assistant.service.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.AudioModel
import com.xiaoguang.assistant.domain.model.RecognitionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.vosk.Recognizer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 混合语音识别管理器
 * 支持在线识别（Android SpeechRecognizer、SiliconFlow API）和离线识别（Vosk）
 */
@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val networkStateMonitor: NetworkStateMonitor,
    private val voskHelper: VoskHelper,
    private val siliconFlowAPI: SiliconFlowAPI
) {
    companion object {
        private const val TAG = "SpeechRecognition"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _recognitionResults = MutableSharedFlow<SpeechRecognitionResult>()
    val recognitionResults: SharedFlow<SpeechRecognitionResult> = _recognitionResults.asSharedFlow()

    private var androidRecognizer: SpeechRecognizer? = null
    private var voskRecognizer: Recognizer? = null
    private var isRecognizing = false

    private var currentMethod: RecognitionMethodUsed? = null

    /**
     * 开始语音识别
     */
    suspend fun startRecognition() {
        if (isRecognizing) {
            Log.w(TAG, "识别已在进行中")
            return
        }

        isRecognizing = true
        _recognitionResults.emit(SpeechRecognitionResult.Started)

        // 根据配置选择识别方法
        val recognitionMethod = appPreferences.recognitionMethod.first()
        val isNetworkAvailable = networkStateMonitor.isNetworkAvailable.value

        currentMethod = determineRecognitionMethod(recognitionMethod, isNetworkAvailable)

        Log.d(TAG, "开始语音识别，方法: $currentMethod")

        when (currentMethod) {
            RecognitionMethodUsed.ONLINE_ANDROID -> startAndroidRecognition()
            RecognitionMethodUsed.ONLINE_SILICON_FLOW -> {
                // SiliconFlow API需要音频文件，这里暂时回退到Android识别
                // 实际使用需要配合AudioCaptureService
                startAndroidRecognition()
            }
            RecognitionMethodUsed.OFFLINE_VOSK -> startVoskRecognition()
            null -> {
                _recognitionResults.emit(
                    SpeechRecognitionResult.Error("无法确定识别方法")
                )
                isRecognizing = false
            }
        }
    }

    /**
     * 停止语音识别
     */
    suspend fun stopRecognition() {
        if (!isRecognizing) return

        Log.d(TAG, "停止语音识别")

        when (currentMethod) {
            RecognitionMethodUsed.ONLINE_ANDROID,
            RecognitionMethodUsed.ONLINE_SILICON_FLOW -> {
                androidRecognizer?.stopListening()
                androidRecognizer?.destroy()
                androidRecognizer = null
            }
            RecognitionMethodUsed.OFFLINE_VOSK -> {
                voskRecognizer?.close()
                voskRecognizer = null
            }
            null -> {}
        }

        isRecognizing = false
        _recognitionResults.emit(SpeechRecognitionResult.Stopped)
    }

    /**
     * 使用音频文件进行识别（用于SiliconFlow API）
     */
    suspend fun recognizeAudioFile(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val modelBody = AudioModel.DEFAULT.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = siliconFlowAPI.audioTranscription(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                file = audioPart,
                model = modelBody
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.text)
            } else {
                Result.failure(Exception("API调用失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SiliconFlow API识别失败", e)
            Result.failure(e)
        }
    }

    /**
     * 处理Vosk识别的音频数据
     * @param audioData PCM 16位音频数据
     * @return 识别结果文本，如果是部分结果则返回null
     */
    fun processVoskAudio(audioData: ByteArray): String? {
        val recognizer = voskRecognizer ?: return null

        return try {
            if (recognizer.acceptWaveForm(audioData, audioData.size)) {
                // 最终结果
                val result = recognizer.result
                val jsonResult = JSONObject(result)
                jsonResult.optString("text", "")
            } else {
                // 部分结果
                val partialResult = recognizer.partialResult
                val jsonResult = JSONObject(partialResult)
                val partialText = jsonResult.optString("partial", "")

                if (partialText.isNotEmpty()) {
                    scope.launch {
                        _recognitionResults.emit(
                            SpeechRecognitionResult.Partial(partialText)
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk音频处理错误", e)
            null
        }
    }

    /**
     * 确定使用的识别方法
     */
    private fun determineRecognitionMethod(
        preference: RecognitionMethod,
        networkAvailable: Boolean
    ): RecognitionMethodUsed? {
        return when (preference) {
            RecognitionMethod.ONLINE_FIRST -> {
                if (networkAvailable) {
                    RecognitionMethodUsed.ONLINE_ANDROID
                } else if (voskHelper.isModelDownloaded()) {
                    RecognitionMethodUsed.OFFLINE_VOSK
                } else {
                    null
                }
            }
            RecognitionMethod.OFFLINE_FIRST -> {
                if (voskHelper.isModelDownloaded()) {
                    RecognitionMethodUsed.OFFLINE_VOSK
                } else if (networkAvailable) {
                    RecognitionMethodUsed.ONLINE_ANDROID
                } else {
                    null
                }
            }
            RecognitionMethod.HYBRID -> {
                // 混合模式：WiFi用在线，移动数据或无网络用离线
                if (networkStateMonitor.isWifiConnected.value) {
                    RecognitionMethodUsed.ONLINE_ANDROID
                } else if (voskHelper.isModelDownloaded()) {
                    RecognitionMethodUsed.OFFLINE_VOSK
                } else if (networkAvailable) {
                    RecognitionMethodUsed.ONLINE_ANDROID
                } else {
                    null
                }
            }
        }
    }

    /**
     * 启动Android在线识别
     */
    private suspend fun startAndroidRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionResults.emit(
                SpeechRecognitionResult.Error("设备不支持语音识别")
            )
            isRecognizing = false
            return
        }

        androidRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "准备接收语音")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "开始说话")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // 接收到音频缓冲
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "说话结束")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "无匹配结果"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                        else -> "未知错误: $error"
                    }

                    Log.e(TAG, "Android识别错误: $errorMessage")
                    scope.launch {
                        _recognitionResults.emit(
                            SpeechRecognitionResult.Error(errorMessage)
                        )
                        isRecognizing = false
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        val confidence = confidences?.getOrNull(0) ?: 1.0f

                        Log.d(TAG, "识别结果: $text (置信度: $confidence)")
                        scope.launch {
                            _recognitionResults.emit(
                                SpeechRecognitionResult.Final(
                                    text = text,
                                    confidence = confidence,
                                    method = RecognitionMethodUsed.ONLINE_ANDROID
                                )
                            )
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        scope.launch {
                            _recognitionResults.emit(
                                SpeechRecognitionResult.Partial(matches[0])
                            )
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // 其他事件
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        androidRecognizer?.startListening(intent)
    }

    /**
     * 启动Vosk离线识别
     */
    private suspend fun startVoskRecognition() {
        // 加载模型
        val modelResult = voskHelper.loadModel()
        if (modelResult.isFailure) {
            _recognitionResults.emit(
                SpeechRecognitionResult.Error(
                    "Vosk模型加载失败: ${modelResult.exceptionOrNull()?.message}",
                    modelResult.exceptionOrNull() as? Exception
                )
            )
            isRecognizing = false
            return
        }

        // 创建识别器
        voskRecognizer = voskHelper.createRecognizer()
        if (voskRecognizer == null) {
            _recognitionResults.emit(
                SpeechRecognitionResult.Error("创建Vosk识别器失败")
            )
            isRecognizing = false
            return
        }

        Log.d(TAG, "Vosk离线识别已启动，等待音频数据...")
        // 实际音频数据需要由AudioCaptureService提供
        // 这里只是初始化识别器
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        androidRecognizer?.destroy()
        androidRecognizer = null
        voskRecognizer?.close()
        voskRecognizer = null
        voskHelper.cleanup()
        networkStateMonitor.cleanup()
    }
}
