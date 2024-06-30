package ai.latelatte.gipite

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsViewController : AppCompatActivity() {

    private lateinit var apiKeyTextField: EditText
    private lateinit var gptNameTextField: EditText
    private lateinit var gptSettingsTextView: EditText
    private lateinit var buttonContainer: LinearLayout
    private lateinit var apiKeyURLLabel: TextView
    private lateinit var pasteButton: Button
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var loadPresetButton: Button
    private lateinit var savePresetButton: Button

    private val models = arrayOf("gpt-4o", "gpt-3.5-turbo")
    private var selectedModel: String? = "gpt-4o"
    private var presets: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_view_controller)

        apiKeyTextField = findViewById<EditText>(R.id.apiKeyTextField)
        gptNameTextField = findViewById<EditText>(R.id.gptNameTextField)
        gptSettingsTextView = findViewById<EditText>(R.id.gptSettingsTextView)
        buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
        apiKeyURLLabel = findViewById<TextView>(R.id.apiKeyURLLabel)
        pasteButton = findViewById<Button>(R.id.pasteButton)
        saveButton = findViewById<Button>(R.id.saveButton)
        clearButton = findViewById<Button>(R.id.clearButton)
        loadPresetButton = findViewById<Button>(R.id.loadPresetButton)
        savePresetButton = findViewById<Button>(R.id.savePresetButton)

        saveButton.setOnClickListener {
            saveSettings()
        }

        clearButton.setOnClickListener {
            clearSettings()
        }

        pasteButton.setOnClickListener {
            pasteApiKey()
        }

        loadPresetButton.setOnClickListener {
            showPresetSelection()
        }

        savePresetButton.setOnClickListener {
            savePreset()
        }

        loadSettings()
    }

    private fun pasteApiKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            apiKeyTextField.setText(item.text)
        } else {
            Toast.makeText(this, "クリップボードが空です", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearSettings() {
        apiKeyTextField.text.clear()
        gptNameTextField.text.clear()
        gptSettingsTextView.text.clear()
        selectedModel = models[0]
        buttonContainer.findViewById<Button>(R.id.gpt4Button).isActivated = true
        buttonContainer.findViewById<Button>(R.id.gpt35Button).isActivated = false
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
        prefs.putString("apiKey", apiKeyTextField.text.toString())
        prefs.putString("gptName", gptNameTextField.text.toString())
        prefs.putString("systemMessage", gptSettingsTextView.text.toString())
        prefs.putString("model", selectedModel)
        prefs.apply()
        showAlert("保存完了", "設定が保存されました")
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        apiKeyTextField.setText(prefs.getString("apiKey", ""))
        gptNameTextField.setText(prefs.getString("gptName", ""))
        gptSettingsTextView.setText(prefs.getString("systemMessage", ""))
        selectedModel = prefs.getString("model", models[0])
        updateModelButtons()
    }

    private fun showPresetSelection() {
        val presetNames = presets.keys.toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("プリセットを選択")
        builder.setItems(presetNames) { dialog, which ->
            loadPreset(presetNames[which])
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun loadPreset(name: String) {
        val preset = presets[name] ?: return
        apiKeyTextField.setText(preset["apiKey"])
        gptNameTextField.setText(preset["gptName"])
        gptSettingsTextView.setText(preset["systemMessage"])
        selectedModel = preset["model"]
        updateModelButtons()
    }

    private fun updateModelButtons() {
        val gpt4Button = buttonContainer.findViewById<Button>(R.id.gpt4Button)
        val gpt35Button = buttonContainer.findViewById<Button>(R.id.gpt35Button)
        gpt4Button.isActivated = selectedModel == models[0]
        gpt35Button.isActivated = selectedModel == models[1]
    }

    private fun savePreset() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("プリセット名")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("保存") { dialog, which ->
            val name = input.text.toString()
            val preset = mapOf(
                "apiKey" to apiKeyTextField.text.toString(),
                "gptName" to gptNameTextField.text.toString(),
                "systemMessage" to gptSettingsTextView.text.toString(),
                "model" to selectedModel!!
            )
            presets[name] = preset
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            prefs.putString("presets", Gson().toJson(presets))
            prefs.apply()
            showAlert("保存完了", "プリセットが保存されました")
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun showAlert(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
