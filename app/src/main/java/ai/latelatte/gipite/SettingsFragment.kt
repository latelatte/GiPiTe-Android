package ai.latelatte.gipite

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsFragment : Fragment() {

    private lateinit var apiKeyTextField: EditText
    private lateinit var gptNameTextField: EditText
    private lateinit var gptSettingsTextView: EditText
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var loadPresetButton: Button
    private lateinit var savePresetButton: Button

    private var presets: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        apiKeyTextField = view.findViewById(R.id.apiKeyTextField)
        gptNameTextField = view.findViewById(R.id.gptNameTextField)
        gptSettingsTextView = view.findViewById(R.id.gptSettingsTextView)
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

        loadPresetButton.setOnClickListener {
            showPresetSelection()
        }

        savePresetButton.setOnClickListener {
            savePreset()
        }

        loadSettings()
        loadPresets()

        return view
    }

    private fun clearSettings() {
        apiKeyTextField.text.clear()
        gptNameTextField.text.clear()
        gptSettingsTextView.text.clear()
    }

    private fun saveSettings() {
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)?.edit()
        prefs?.putString("apiKey", apiKeyTextField.text.toString())
        prefs?.putString("gptName", gptNameTextField.text.toString())
        prefs?.putString("systemMessage", gptSettingsTextView.text.toString())
        prefs?.apply()
        showAlert("保存完了", "設定が保存されました") {
            navigateToHome()
        }
    }

    private fun loadSettings() {
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        apiKeyTextField.setText(prefs?.getString("apiKey", ""))
        gptNameTextField.setText(prefs?.getString("gptName", ""))
        gptSettingsTextView.setText(prefs?.getString("systemMessage", ""))
    }

    private fun loadPresets() {
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val jsonPresets = prefs?.getString("presets", "")
        if (!jsonPresets.isNullOrEmpty()) {
            val type = object : TypeToken<MutableMap<String, Map<String, String>>>() {}.type
            presets = Gson().fromJson(jsonPresets, type)
        }
    }

    private fun showPresetSelection() {
        val presetNames = presets.keys.toTypedArray()
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("プリセットを選択")
        builder.setItems(presetNames) { _, which ->
            showPresetOptions(presetNames[which])
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun showPresetOptions(presetName: String) {
        val options = arrayOf("ロード", "削除")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("プリセット操作を選択")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> loadPreset(presetName) // ロード
                1 -> deletePreset(presetName) // 削除
            }
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun loadPreset(name: String) {
        val preset = presets[name] ?: return
        apiKeyTextField.setText(preset["apiKey"])
        gptNameTextField.setText(preset["gptName"])
        gptSettingsTextView.setText(preset["systemMessage"])
    }

    private fun deletePreset(name: String) {
        presets.remove(name)
        val prefs = activity?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)?.edit()
        prefs?.putString("presets", Gson().toJson(presets))
        prefs?.apply()
        showAlert("削除完了", "プリセットが削除されました")
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
                "systemMessage" to gptSettingsTextView.text.toString()
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

    private fun showAlert(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            onDismiss?.invoke()
        }
        builder.show()
    }

    private fun navigateToHome() {
        val mainActivity = activity as? MainActivity
        mainActivity?.loadFragment(HomeFragment())
    }
}
