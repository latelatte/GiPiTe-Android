package ai.latelatte.gipite

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.Gson

class SettingsFragment : Fragment() {

    private lateinit var apiKeyTextField: EditText
    private lateinit var gptNameTextField: EditText
    private lateinit var gptSettingsTextView: EditText
    private lateinit var buttonContainer: LinearLayout
    private lateinit var pasteButton: Button
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var loadPresetButton: Button
    private lateinit var savePresetButton: Button

    private val models = arrayOf("gpt-4o", "gpt-3.5-turbo")
    private var selectedModel: String? = "gpt-4o"
    private var presets: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        apiKeyTextField = view.findViewById(R.id.apiKeyTextField)
        gptNameTextField = view.findViewById(R.id.gptNameTextField)
        gptSettingsTextView = view.findViewById(R.id.gptSettingsTextView)
        buttonContainer = view.findViewById(R.id.buttonContainer)
        pasteButton = view.findViewById(R.id.pasteButton)
        saveButton = view.findViewById(R.id.saveButton)
        clearButton = view.findViewById(R.id.clearButton)
        loadPresetButton = view.findViewById(R.id.loadPresetButton)
        savePresetButton = view.findViewById(R.id.savePresetButton)

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
        return view
    }

    private fun pasteApiKey() {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            apiKeyTextField.setText(item.text)
        } else {
            Toast.makeText(activity, "クリップボードが空です", Toast.LENGTH_SHORT).show()
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
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)?.edit()
        prefs?.putString("apiKey", apiKeyTextField.text.toString())
        prefs?.putString("gptName", gptNameTextField.text.toString())
        prefs?.putString("systemMessage", gptSettingsTextView.text.toString())
        prefs?.putString("model", selectedModel)
        prefs?.apply()
        showAlert("保存完了", "設定が保存されました")
    }

    private fun loadSettings() {
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        apiKeyTextField.setText(prefs?.getString("apiKey", ""))
        gptNameTextField.setText(prefs?.getString("gptName", ""))
        gptSettingsTextView.setText(prefs?.getString("systemMessage", ""))
        selectedModel = prefs?.getString("model", models[0])
        updateModelButtons()
    }

    private fun showPresetSelection() {
        val presetNames = presets.keys.toTypedArray()
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("プリセットを選択")
        builder.setItems(presetNames) { _, which ->
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
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("プリセット名")
        val input = EditText(requireContext())
        builder.setView(input)
        builder.setPositiveButton("保存") { _, _ ->
            val name = input.text.toString()
            val preset = mapOf(
                "apiKey" to apiKeyTextField.text.toString(),
                "gptName" to gptNameTextField.text.toString(),
                "systemMessage" to gptSettingsTextView.text.toString(),
                "model" to selectedModel!!
            )
            presets[name] = preset
            val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)?.edit()
            prefs?.putString("presets", Gson().toJson(presets))
            prefs?.apply()
            showAlert("保存完了", "プリセットが保存されました")
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun showAlert(title: String, message: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
