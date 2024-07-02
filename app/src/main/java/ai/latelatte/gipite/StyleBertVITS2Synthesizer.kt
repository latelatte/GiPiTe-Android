package ai.latelatte.gipite

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException

class StyleBertVITS2Synthesizer(private val context: Context) {
    var audioPlayer: MediaPlayer? = null
    var onAudioPlaybackFinished: (() -> Unit)? = null
    private var retryCount = 0
    private val maxRetries = 3

    fun synthesizeSpeech(from: String, completion: (Exception?) -> Unit) {
        val apiURL = "http://34.146.101.75:8000/api/text_synthesis"
        val json = """
            {
                "text": "$from"
            }
        """.trimIndent()

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val request = Request.Builder()
            .url(apiURL)
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completion(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body
                if (!response.isSuccessful || responseBody == null) {
                    completion(IOException("Failed to synthesize speech"))
                    return
                }
                val audioData = responseBody.byteStream().readBytes()
                playAudioData(audioData, completion)
            }
        })
    }

    private fun playAudioData(data: ByteArray, completion: (Exception?) -> Unit) {
        try {
            audioPlayer?.release()
            audioPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                reset()
                val tmpFile = File.createTempFile("prefix", ".wav", context.cacheDir)
                tmpFile.writeBytes(data)
                setDataSource(tmpFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onAudioPlaybackFinished?.invoke()
                    tmpFile.delete() // 再生後に一時ファイルを削除
                    release() // 再生終了後にリリース
                    audioPlayer = null // オブジェクトの参照を解放
                    clearCache(context) // キャッシュのクリア
                    retryCount = 0 // リトライカウントをリセット
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MediaPlayerError", "Error occurred: what=$what, extra=$extra")
                    if (retryCount < maxRetries) {
                        retryCount++
                        playAudioData(data, completion) // 再試行
                    } else {
                        completion(IOException("MediaPlayer failed after $maxRetries retries"))
                    }
                    true
                }
                start()
            }
            completion(null)
        } catch (e: IOException) {
            completion(e)
        }
    }

    private fun clearCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e("CacheClearError", "Failed to clear cache: ${e.message}")
        }
    }
}
