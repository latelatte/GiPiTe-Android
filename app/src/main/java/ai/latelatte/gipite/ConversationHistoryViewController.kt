package ai.latelatte.gipite

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ConversationHistoryViewController : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var conversationFiles: MutableList<File>
    private var isSelecting = false
    private var selectedFiles: MutableList<File> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_history)

        listView = findViewById(R.id.conversationHistoryListView)
        listView.setOnItemClickListener { parent, view, position, id ->
            if (isSelecting) {
                toggleSelection(position)
            } else {
                openConversationDetail(conversationFiles[position])
            }
        }

        listView.setOnItemLongClickListener { parent, view, position, id ->
            if (!isSelecting) {
                isSelecting = true
                invalidateOptionsMenu()
            }
            toggleSelection(position)
            true
        }

        loadConversationFiles()
    }

    private fun loadConversationFiles() {
        val documentsDir = getExternalFilesDir(null) // You might want to specify a sub-directory if needed
        conversationFiles = documentsDir?.listFiles { file ->
            file.extension == "txt"
        }?.toMutableList() ?: mutableListOf()

        val fileNames = conversationFiles.map { it.nameWithoutExtension }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, fileNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
    }

    private fun toggleSelection(position: Int) {
        val file = conversationFiles[position]
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
            listView.setItemChecked(position, false)
        } else {
            selectedFiles.add(file)
            listView.setItemChecked(position, true)
        }
        invalidateOptionsMenu()
    }

    private fun openConversationDetail(file: File) {
        val intent = Intent(this, ConversationDetailViewController::class.java)
        intent.putExtra("fileURL", file.absolutePath)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_conversation_history, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_select)?.isVisible = !isSelecting
        menu.findItem(R.id.action_delete)?.isVisible = isSelecting
        menu.findItem(R.id.action_rename)?.isVisible = isSelecting && selectedFiles.size == 1
        menu.findItem(R.id.action_cancel)?.isVisible = isSelecting
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select -> {
                isSelecting = true
                invalidateOptionsMenu()
                true
            }
            R.id.action_delete -> {
                confirmDeleteSelectedFiles()
                true
            }
            R.id.action_rename -> {
                showRenameAlert(selectedFiles.first())
                true
            }
            R.id.action_cancel -> {
                isSelecting = false
                selectedFiles.clear()
                invalidateOptionsMenu()
                listView.clearChoices()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDeleteSelectedFiles() {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage("選択したファイルを削除してもよろしいですか？")
            .setPositiveButton("削除") { dialog, which ->
                deleteSelectedFiles()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun deleteSelectedFiles() {
        for (file in selectedFiles) {
            file.delete()
        }
        loadConversationFiles()
        isSelecting = false
        selectedFiles.clear()
        invalidateOptionsMenu()
        listView.clearChoices()
    }

    private fun showRenameAlert(file: File) {
        val editText = EditText(this).apply {
            setText(file.nameWithoutExtension)
        }

        AlertDialog.Builder(this)
            .setTitle("名前の変更")
            .setMessage("新しいファイル名を入力してください")
            .setView(editText)
            .setPositiveButton("名前の変更") { dialog, which ->
                val newName = editText.text.toString()
                renameFile(file, newName)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun renameFile(file: File, newName: String) {
        val newFile = File(file.parent, "$newName.txt")
        if (newFile.exists()) {
            Toast.makeText(this, "同じ名前のファイルが既に存在します", Toast.LENGTH_SHORT).show()
        } else {
            file.renameTo(newFile)
            loadConversationFiles()
        }
    }
}
