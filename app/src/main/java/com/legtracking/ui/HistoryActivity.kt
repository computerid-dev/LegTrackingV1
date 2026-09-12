package com.legtracking.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.legtracking.R
import com.legtracking.data.AppDatabase
import com.legtracking.databinding.ActivityHistoryBinding
import com.legtracking.util.BackupManager
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: TrackAdapter
    private lateinit var backupManager: BackupManager

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val success = backupManager.exportToUri(uri)
            showToast(if (success) R.string.msg_export_success else R.string.msg_export_failed)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val success = backupManager.importFromUri(uri)
            showToast(if (success) R.string.msg_import_success else R.string.msg_import_failed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        backupManager = BackupManager(this)

        setSupportActionBar(binding.toolbar)
        title = getString(R.string.history_title)

        adapter = TrackAdapter { track ->
            MapActivity.startWithTrack(this, track.id)
            finish()
        }

        binding.recyclerTracks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTracks.adapter = adapter

        binding.btnExport.setOnClickListener {
            exportLauncher.launch("legtracking-backup.json")
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        observeTracks()
    }

    private fun observeTracks() {
        val dao = AppDatabase.getInstance(this).trackDao()
        lifecycleScope.launch {
            dao.observeTracks().collect { tracks ->
                adapter.submitList(tracks)
            }
        }
    }

    private fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}
