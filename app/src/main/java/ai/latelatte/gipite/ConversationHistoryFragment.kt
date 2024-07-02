package ai.latelatte.gipite

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class ConversationHistoryFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var conversationFiles: MutableList<File>
    private var isSelecting = false
    private var selectedFiles: MutableList<File> = mutableListOf()
    private lateinit var fabSelectMode: FloatingActionButton
    private lateinit var fabDelete: FloatingActionButton
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_conversation_history, container, false)
        listView = view.findViewById(R.id.conversationHistoryListView)
        fabSelectMode = view.findViewById(R.id.fab_select_mode)
        fabDelete = view.findViewById(R.id.fab_delete)

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isSelecting) {
                toggleSelection(position)
            } else {
                openConversationDetail(conversationFiles[position])
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isSelecting) {
                showRenameAlert(conversationFiles[position])
            }
            toggleSelection(position)
            true
        }

        fabSelectMode.setOnClickListener {
            if (isSelecting) {
                exitSelectionMode()
            } else {
                enterSelectionMode()
            }
        }

        fabDelete.setOnClickListener {
            confirmDeleteSelectedFiles()
        }

        loadConversationFiles()
        setHasOptionsMenu(true)
        return view
    }

    private fun loadConversationFiles() {
        val documentsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        conversationFiles = documentsDir?.listFiles { file ->
            file.extension == "txt"
        }?.toMutableList() ?: mutableListOf()

        val fileNames = conversationFiles.map { it.nameWithoutExtension }
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fileNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_NONE
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
        updateFabDeleteVisibility()
    }

    private fun openConversationDetail(file: File) {
        val intent = Intent(activity, ConversationDetailViewController::class.java)
        intent.putExtra("fileURL", file.absolutePath)
        startActivity(intent)
    }

    private fun enterSelectionMode() {
        isSelecting = true
        fabSelectMode.setImageResource(R.drawable.ic_cancel)
        fabDelete.visibility = View.VISIBLE
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        updateAdapterForSelectionMode()
    }

    private fun exitSelectionMode() {
        isSelecting = false
        selectedFiles.clear()
        fabSelectMode.setImageResource(R.drawable.ic_select)
        fabDelete.visibility = View.GONE
        listView.clearChoices()
        listView.choiceMode = ListView.CHOICE_MODE_NONE
        updateAdapterForSelectionMode()
    }

    private fun updateFabDeleteVisibility() {
        fabDelete.visibility = if (selectedFiles.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "削除するファイルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("確認")
            .setMessage("選択したファイルを削除してもよろしいですか？")
            .setPositiveButton("削除") { _, _ ->
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
        exitSelectionMode()
    }

    private fun showRenameAlert(file: File) {
        val editText = EditText(requireContext()).apply {
            setText(file.nameWithoutExtension)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("名前の変更")
            .setMessage("新しいファイル名を入力してください")
            .setView(editText)
            .setPositiveButton("名前の変更") { _, _ ->
                val newName = editText.text.toString()
                renameFile(file, newName)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun renameFile(file: File, newName: String) {
        val newFile = File(file.parent, "$newName.txt")
        if (newFile.exists()) {
            Toast.makeText(requireContext(), "同じ名前のファイルが既に存在します", Toast.LENGTH_SHORT).show()
        } else {
            file.renameTo(newFile)
            loadConversationFiles()
        }
    }

    private fun updateAdapterForSelectionMode() {
        val fileNames = conversationFiles.map { it.nameWithoutExtension }
        val updatedAdapter = if (isSelecting) {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, fileNames)
        } else {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fileNames)
        }
        listView.adapter = updatedAdapter
    }
}
