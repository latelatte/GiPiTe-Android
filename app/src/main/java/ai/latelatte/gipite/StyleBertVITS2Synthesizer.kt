package ai.latelatte.gipite

import android.media.AudioAttributes
import android.media.MediaPlayer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.ByteArrayInputStream
import java.io.IOException

class StyleBertVITS2Synthesizer {
    var audioPlayer: MediaPlayer? = null
    var onAudioPlaybackFinished: (() -> Unit)? = null

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
                val tmpFile = createTempFile(suffix = ".wav")
                tmpFile.writeBytes(data)
                setDataSource(tmpFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onAudioPlaybackFinished?.invoke()
                    tmpFile.deleteOnExit() // 再生後に一時ファイルを削除（非同期に削除を行う）
                }
                start()
            }
            completion(null)
        } catch (e: IOException) {
            completion(e)
        }
    }
}
