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
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_conversation_history, container, false)
        listView = view.findViewById(R.id.conversationHistoryListView)
        fab = view.findViewById(R.id.fab_select_mode)

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isSelecting) {
                toggleSelection(position)
            } else {
                openConversationDetail(conversationFiles[position])
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            showRenameAlert(conversationFiles[position])
            true
        }

        fab.setOnClickListener {
            if (isSelecting) {
                exitSelectionMode()
            } else {
                enterSelectionMode()
            }
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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, fileNames)
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
        activity?.invalidateOptionsMenu()
    }

    private fun openConversationDetail(file: File) {
        val intent = Intent(activity, ConversationDetailViewController::class.java)
        intent.putExtra("fileURL", file.absolutePath)
        startActivity(intent)
    }

    private fun enterSelectionMode() {
        isSelecting = true
        fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        activity?.invalidateOptionsMenu()
    }

    private fun exitSelectionMode() {
        isSelecting = false
        selectedFiles.clear()
        fab.setImageResource(android.R.drawable.ic_menu_agenda)
        listView.clearChoices()
        listView.choiceMode = ListView.CHOICE_MODE_NONE
        activity?.invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_conversation_history, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.action_delete)?.isVisible = isSelecting && selectedFiles.isNotEmpty()
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                confirmDeleteSelectedFiles()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDeleteSelectedFiles() {
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
}
