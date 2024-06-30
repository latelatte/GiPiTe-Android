package ai.latelatte.gipite

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ConversationDetailViewController : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var continueButton: Button
    private lateinit var fileURL: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_detail_view_controller)

        textView = findViewById(R.id.textView)
        continueButton = findViewById(R.id.continueButton)

        // インテントからファイルURLを取得
        val filePath = intent.getStringExtra("fileURL")
        if (filePath != null) {
            fileURL = File(filePath)
            title = fileURL.nameWithoutExtension
            textView.isFocusable = false
            loadConversationDetail()
        } else {
            Toast.makeText(this, "ファイルパスを取得できませんでした", Toast.LENGTH_SHORT).show()
            finish()
        }

        continueButton.setOnClickListener {
            continueConversation()
        }
    }

    private fun loadConversationDetail() {
        try {
            if (fileURL.exists()) {
                val content = fileURL.readText()
                Log.d("ConversationDetail", "ファイル内容: $content") // デバッグログ追加
                textView.text = content
            } else {
                Log.e("ConversationDetail", "ファイルが存在しません: ${fileURL.absolutePath}")
                Toast.makeText(this, "ファイルが存在しません: ${fileURL.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ConversationDetail", "会話の詳細を読み込む際にエラーが発生しました: ${e.message}", e)
            Toast.makeText(this, "会話の詳細を読み込む際にエラーが発生しました: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun continueConversation() {
        val jsonFileURL = File(fileURL.path.replace(".txt", ".json"))
        try {
            val jsonData = jsonFileURL.readText()
            val loadedHistory: List<Map<String, String>> = Gson().fromJson(jsonData, object : TypeToken<List<Map<String, String>>>() {}.type)
            // MainActivity (ViewController) にアクセスして appendToConversationHistory メソッドを呼び出す
            val mainActivity = MainActivity.instance
            mainActivity?.appendToConversationHistory(loadedHistory)
            mainActivity?.restoreTextOutput(fileURL)
            finish()
        } catch (e: Exception) {
            showAlert("読み込みエラー", "会話履歴の読み込みに失敗しました: ${e.message}")
        }
    }

    private fun showAlert(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
