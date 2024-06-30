package ai.latelatte.gipite

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.speech.RecognitionListener
import androidx.constraintlayout.widget.ConstraintLayout


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var textOutput: TextView
    private lateinit var saveConversationButton: Button
    private lateinit var cancelRecognitionButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var rootLayout: ConstraintLayout
    private var recognizedText: String = ""
    private var conversationHistory: MutableList<Map<String, String>> = mutableListOf()
    private var isConversationActive = false
    private var isRecognitionActive = false
    private var audioPlayer: MediaPlayer? = null
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

    companion object {
        var instance: MainActivity? = null
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        instance = this

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusLabel = findViewById(R.id.statusLabel)
        textOutput = findViewById(R.id.textOutput)
        saveConversationButton = findViewById(R.id.saveConversationButton)
        cancelRecognitionButton = findViewById(R.id.cancelRecognitionButton)
        rootLayout = findViewById(R.id.rootLayout)

        rootLayout.setOnClickListener {
            cancelRecognition()
        }

        textToSpeech = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        configureUI()
        loadSettings()
        setupNavigationBar()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
        }

        startButton.setOnClickListener {
            startRecognition()
        }

        stopButton.setOnClickListener {
            stopRecognition()
        }

        saveConversationButton.setOnClickListener {
            promptForFileNameAndSave()
        }

        cancelRecognitionButton.setOnClickListener {
            cancelRecognition()
        }

        rootLayout.setOnClickListener {
            cancelRecognition()
        }

        val settingsButton: Button = findViewById(R.id.settingsButton)
        val historyButton: Button = findViewById(R.id.historyButton)

        settingsButton.setOnClickListener {
            openSettings()
        }

        historyButton.setOnClickListener {
            viewConversationHistory()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // パーミッションが許可された場合の処理
                startRecognition()
            } else {
                // パーミッションが拒否された場合の処理
                statusLabel.text = "マイクの使用許可が必要です"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        textToSpeech.shutdown()
        audioPlayer?.release()
        speechRecognizer?.destroy()
    }

    private fun configureUI() {
        startButton.text = "会話を始める！"
        stopButton.text = "会話を終える！"
        statusLabel.text = "準備完了"
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        apiKey = prefs.getString("apiKey", "") ?: ""
        gptModel = prefs.getString("model", "") ?: ""
        systemMessage["role"] = "system"
        systemMessage["content"] = prefs.getString("systemMessage", "") ?: ""
        gptName = prefs.getString("gptName", gptModel) ?: gptModel

        Log.d("MainActivity", "API Key: $apiKey")
    }

    private fun setupNavigationBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
    }

    private fun initializeConversationHistory() {
        conversationHistory.clear()
        conversationHistory.add(systemMessage)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.JAPANESE
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // 音声合成が開始したときの処理
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
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
            textOutput.text = "ここに会話が記録されます\n"
            initializeConversationHistory()
        }

        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusLabel.text = "音声認識を開始しました…"

        // 音声認識を開始する処理を追加
        startSpeechRecognition()
    }

    // SpeechRecognizerのセットアップ
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



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE) {
            isRecognitionActive = false
            if (resultCode == Activity.RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                results?.let {
                    recognizedText = it[0]
                    textOutput.append("\nYou: $recognizedText\n")
                    if (recognizedText.contains("またね")) {
                        endConversation()
                    } else {
                        sendToGPT(recognizedText)
                    }
                }
            } else {
                // 認識できなかった場合、再度音声認識を開始
                if (isConversationActive) {
                    startSpeechRecognition()
                }
            }
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
        audioPlayer?.stop()
        speechRecognizer?.stopListening() // ここで音声認識を停止
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
        // 音声認識を再開するなど、音声合成が完了した後の処理をここに記述
        Log.d("MainActivity", "Speech synthesis finished")
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
                runOnUiThread {
                    statusLabel.text = "GPTリクエストエラー: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body
                responseBody?.string()?.let {
                    Log.d("MainActivity", "API Response: $it") // レスポンス全体をログ出力
                    val responseJSON = Gson().fromJson<Map<String, Any>>(
                        it,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val choices = responseJSON["choices"] as? List<Map<String, Any>>
                    val message = choices?.firstOrNull()?.get("message") as? Map<String, String>
                    val content = message?.get("content")
                    if (content != null) {
                        runOnUiThread {
                            textOutput.append("\n$gptName: $content\n")
                            conversationHistory.add(
                                mapOf(
                                    "role" to "assistant",
                                    "content" to content
                                )
                            )
                            synthesizeSpeechWithSBV2(content)
                        }
                    } else {
                        runOnUiThread {
                            statusLabel.text = "不正なレスポンス形式"
                        }
                    }
                }
            }
        })
    }

    fun appendToConversationHistory(history: List<Map<String, String>>) {
        conversationHistory.addAll(history)
    }

    fun restoreTextOutput(fileURL: File) {
        try {
            val content = fileURL.readText()
            textOutput.text = content
        } catch (e: IOException) {
            Toast.makeText(
                this,
                "Error loading conversation detail: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsViewController::class.java)
        startActivity(intent)
    }

    private fun viewConversationHistory() {
        val intent = Intent(this, ConversationHistoryViewController::class.java)
        startActivity(intent)
    }

    private fun promptForFileNameAndSave() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ファイル名")
        builder.setMessage("保存するファイル名を入力してください。\n空欄の場合は日時がファイル名として使用されます。")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("保存") { dialog, which ->
            val fileName = input.text.toString()
            confirmAndSaveConversation(fileName)
        }
        builder.setNegativeButton("キャンセル") { dialog, which -> dialog.cancel() }

        builder.show()
    }

    private fun confirmAndSaveConversation(fileName: String) {
        val documentsURL = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val finalFileName = if (fileName.isEmpty()) {
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        } else {
            fileName
        }

        val textFile = File(documentsURL, "$finalFileName.txt")
        val jsonFile = File(documentsURL, "$finalFileName.json")

        if (textFile.exists() || jsonFile.exists()) {
            val overwriteAlert = AlertDialog.Builder(this)
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
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}
