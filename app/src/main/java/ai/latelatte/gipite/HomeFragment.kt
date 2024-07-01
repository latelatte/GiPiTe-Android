package ai.latelatte.gipite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var textOutput: TextView
    private lateinit var saveConversationButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var scrollView: ScrollView
    private var recognizedText: String = ""
    private var conversationHistory: MutableList<Map<String, String>> = mutableListOf()
    private var isConversationActive = false
    private var isRecognitionActive = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val SPEECH_REQUEST_CODE = 102

    // 初期設定の変数
    private var apiKey: String = ""
    private var gptModel: String = ""
    private var gptName: String = ""
    private var systemMessage: MutableMap<String, String> = mutableMapOf()

    // APIのURLを定数として宣言
    private val gptApiUrl = "https://api.openai.com/v1/chat/completions"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)
        statusLabel = view.findViewById(R.id.statusLabel)
        textOutput = view.findViewById(R.id.textOutput)
        saveConversationButton = view.findViewById(R.id.saveConversationButton)
        scrollView = view.findViewById(R.id.scrollView)

        textToSpeech = TextToSpeech(context, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        configureUI()
        loadSettings()

        startButton.setOnClickListener {
            startRecognition()
        }

        stopButton.setOnClickListener {
            stopRecognition()
        }

        saveConversationButton.setOnClickListener {
            promptForFileNameAndSave()
        }

        // 会話履歴がある場合に復元
        val conversationHistoryJson = activity?.intent?.getStringExtra("conversationHistory")
        val filePath = activity?.intent?.getStringExtra("fileURL")
        val restoreFlag = activity?.intent?.getBooleanExtra("restoreFlag", false) ?: false

        if (restoreFlag && conversationHistoryJson != null && filePath != null) {
            restoreConversation(conversationHistoryJson, filePath)
        } else {
            initializeConversationHistory()
        }

        return view
    }

    private fun configureUI() {
        startButton.text = "会話を始める！"
        stopButton.text = "会話を終える！"
        statusLabel.text = "準備完了"
    }

    private fun loadSettings() {
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        apiKey = prefs?.getString("apiKey", "") ?: ""
        gptModel = prefs?.getString("model", "") ?: ""
        systemMessage["role"] = "system"
        systemMessage["content"] = prefs?.getString("systemMessage", "") ?: ""
        gptName = prefs?.getString("gptName", gptModel) ?: gptModel

        Log.d("HomeFragment", "API Key: $apiKey")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.JAPANESE
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    activity?.runOnUiThread {
                        if (isConversationActive) {
                            startRecognition()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTS", "Error in synthesis")
                }
            })
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    private fun startRecognition() {
        if (!isConversationActive) {
            isConversationActive = true
            loadSettings()
            // 復元フラグをチェックして、初期化をスキップする
            val restoreFlag = activity?.intent?.getBooleanExtra("restoreFlag", false) ?: false
            if (!restoreFlag) {
                textOutput.text = "ここに会話が記録されます\n"
                initializeConversationHistory()
            }
        }

        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusLabel.text = "音声認識を開始しました…"

        startSpeechRecognition()
    }

    private fun initializeConversationHistory() {
        conversationHistory.clear()
        conversationHistory.add(systemMessage)
    }

    private fun startSpeechRecognition() {
        if (!isConversationActive) return

        isRecognitionActive = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognition", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognition", "Speech beginning")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognition", "Speech end")
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "Error: $error")
                if (isConversationActive) {
                    startSpeechRecognition()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    textOutput.append("\nあなた: $recognizedText\n")
                    scrollToBottom()
                    if (recognizedText.contains("またね")) {
                        endConversation()
                    } else {
                        sendToGPT(recognizedText)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun cancelRecognition() {
        if (isRecognitionActive) {
            speechRecognizer?.cancel()
            isRecognitionActive = false
            statusLabel.text = "音声認識をキャンセルしました。"
        }
    }

    private fun stopRecognition() {
        endConversation()
    }

    private fun endConversation() {
        isConversationActive = false
        isRecognitionActive = false
        statusLabel.text = "会話を終了しました。"
        startButton.isEnabled = true
        stopButton.isEnabled = false
        speechRecognizer?.stopListening()
        activity?.intent?.removeExtra("restoreFlag")
    }

    private fun sendToGPT(text: String) {
        val serverUrl = gptApiUrl
        conversationHistory.add(mapOf("role" to "user", "content" to text))
        val json = Gson().toJson(mapOf("model" to gptModel, "messages" to conversationHistory))
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val request = Request.Builder()
            .url(serverUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    statusLabel.text = "GPTリクエストエラー: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body
                responseBody?.string()?.let {
                    Log.d("HomeFragment", "API Response: $it")
                    val responseJSON = Gson().fromJson<Map<String, Any>>(
                        it,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val choices = responseJSON["choices"] as? List<Map<String, Any>>
                    val message = choices?.firstOrNull()?.get("message") as? Map<String, String>
                    val content = message?.get("content")
                    if (content != null) {
                        activity?.runOnUiThread {
                            textOutput.append("\n$gptName: $content\n")
                            scrollToBottom()
                            conversationHistory.add(
                                mapOf(
                                    "role" to "assistant",
                                    "content" to content
                                )
                            )
                            synthesizeSpeechWithSBV2(content)
                        }
                    } else {
                        activity?.runOnUiThread {
                            statusLabel.text = "不正なレスポンス形式"
                        }
                    }
                }
            }
        })
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun synthesizeSpeechWithSBV2(text: String) {
        val sbv2Synthesizer = StyleBertVITS2Synthesizer()
        sbv2Synthesizer.onAudioPlaybackFinished = {
            if (isConversationActive) {
                startRecognition()
            }
        }
        sbv2Synthesizer.synthesizeSpeech(text) { error ->
            if (error == null) {
                onSpeechSynthesisFinished()
            } else {
                Log.e("SBV2", "Error in synthesis: ${error.message}")
            }
        }
    }

    private fun onSpeechSynthesisFinished() {
        Log.d("HomeFragment", "Speech synthesis finished")
    }

    private fun promptForFileNameAndSave() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("ファイル名")
        builder.setMessage("保存するファイル名を入力してください。\n空欄の場合は日時がファイル名として使用されます。")

        val input = EditText(context)
        builder.setView(input)

        builder.setPositiveButton("保存") { dialog, which ->
            val fileName = input.text.toString()
            confirmAndSaveConversation(fileName)
        }
        builder.setNegativeButton("キャンセル", null)

        builder.show()
    }

    private fun confirmAndSaveConversation(fileName: String) {
        val documentsURL = context?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val finalFileName = if (fileName.isEmpty()) {
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        } else {
            fileName
        }

        val textFile = File(documentsURL, "$finalFileName.txt")
        val jsonFile = File(documentsURL, "$finalFileName.json")

        if (textFile.exists() || jsonFile.exists()) {
            val overwriteAlert = AlertDialog.Builder(requireContext())
            overwriteAlert.setTitle("上書き確認")
            overwriteAlert.setMessage("同じ名前のファイルが既に存在します。上書きしますか？")
            overwriteAlert.setPositiveButton("上書き") { _, _ ->
                saveConversationFiles(textFile, jsonFile)
            }
            overwriteAlert.setNegativeButton("キャンセル", null)
            overwriteAlert.show()
        } else {
            saveConversationFiles(textFile, jsonFile)
        }
    }

    private fun saveConversationFiles(textFile: File, jsonFile: File) {
        try {
            textFile.writeText(textOutput.text.toString())
            val jsonData = Gson().toJson(conversationHistory)
            jsonFile.writeText(jsonData)
            showAlert("保存完了", "会話履歴が保存されました")
        } catch (e: IOException) {
            showAlert("保存エラー", "会話履歴の保存に失敗しました: ${e.message}")
        }
    }

    private fun showAlert(title: String, message: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }

    private fun restoreConversation(conversationHistoryJson: String, filePath: String) {
        val loadedHistory: List<Map<String, String>> = Gson().fromJson(conversationHistoryJson, object : TypeToken<List<Map<String, String>>>() {}.type)
        conversationHistory.clear()
        conversationHistory.addAll(loadedHistory)
        restoreTextOutput(File(filePath))
    }

    fun restoreTextOutput(file: File) {
        try {
            val content = file.readText()
            textOutput.text = content
            scrollToBottom()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
