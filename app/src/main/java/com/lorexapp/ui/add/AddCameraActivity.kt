package com.lorexapp.ui.add

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lorexapp.LorexApp
import com.lorexapp.databinding.ActivityAddCameraBinding
import com.lorexapp.model.Camera
import com.lorexapp.network.LorexApiClient
import kotlinx.coroutines.launch

class AddCameraActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAMERA_ID = "camera_id"
    }

    private lateinit var binding: ActivityAddCameraBinding
    private var editingCamera: Camera? = null
    private val repo get() = (application as LorexApp).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cameraId = intent.getIntExtra(EXTRA_CAMERA_ID, -1)
        val isEditing = cameraId != -1

        binding.tvTitle.text = if (isEditing) "Edit Camera" else "Add Camera"

        if (isEditing) {
            // Editing: hide multi-channel and copy-from-last controls
            binding.switchMultiChannel.visibility = View.GONE
            binding.layoutMultiChannel.visibility = View.GONE
            binding.btnCopyLast.visibility = View.GONE
            lifecycleScope.launch {
                val cam = repo.getById(cameraId)
                if (cam != null) { editingCamera = cam; populateFields(cam) }
            }
        } else {
            setupMultiChannelToggle()
            setupCopyFromLast()
        }

        binding.btnSave.setOnClickListener { saveCamera() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    // ── Multi-channel ─────────────────────────────────────────────────────────

    private fun setupMultiChannelToggle() {
        binding.switchMultiChannel.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSingleChannel.visibility = if (isChecked) View.GONE else View.VISIBLE
            binding.layoutMultiChannel.visibility  = if (isChecked) View.VISIBLE else View.GONE
            binding.btnSave.text = if (isChecked) "Add Cameras" else "Save Camera"
            updatePreview()
        }

        // Live preview update
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updatePreview()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        binding.etChannelFrom.addTextChangedListener(watcher)
        binding.etChannelTo.addTextChangedListener(watcher)
        binding.etNamePrefix.addTextChangedListener(watcher)
        updatePreview()
    }

    private fun updatePreview() {
        val from   = binding.etChannelFrom.text.toString().toIntOrNull() ?: 1
        val to     = binding.etChannelTo.text.toString().toIntOrNull() ?: from
        val prefix = binding.etNamePrefix.text.toString().trim()

        if (from > to) {
            binding.tvMultiPreview.text = "⚠ From must be ≤ To"
            return
        }
        val count = to - from + 1
        val names = (from..to).map { ch -> cameraName(prefix, ch) }
        val preview = when {
            count <= 4 -> names.joinToString(", ")
            else       -> "${names.take(3).joinToString(", ")} … ${names.last()} ($count total)"
        }
        binding.tvMultiPreview.text = "Will add: $preview"
    }

    private fun cameraName(prefix: String, channel: Int) =
        if (prefix.isBlank()) "Camera $channel" else "$prefix $channel"

    // ── Copy from last ────────────────────────────────────────────────────────

    private fun setupCopyFromLast() {
        lifecycleScope.launch {
            val last = repo.getLastCamera()
            binding.btnCopyLast.visibility = if (last != null) View.VISIBLE else View.GONE
            binding.btnCopyLast.setOnClickListener {
                if (last != null) copyFromLast(last)
            }
        }
    }

    private fun copyFromLast(source: Camera) {
        populateFields(source.copy(channel = source.channel + 1, name = ""))
        binding.etChannelFrom.setText((source.channel + 1).toString())
        binding.etChannelTo.setText((source.channel + 4).toString())
        binding.etName.requestFocus()
        Toast.makeText(this, "Copied from \"${source.displayLabel()}\"", Toast.LENGTH_SHORT).show()
    }

    // ── Populate / Save ───────────────────────────────────────────────────────

    private fun populateFields(cam: Camera) {
        binding.etName.setText(cam.name)
        binding.etHost.setText(cam.host)
        binding.etRtspPort.setText(cam.rtspPort.toString())
        binding.etHttpPort.setText(cam.httpPort.toString())
        binding.etUsername.setText(cam.username)
        binding.etPassword.setText(cam.password)
        binding.etChannel.setText(cam.channel.toString())
        binding.switchSubStream.isChecked = cam.subStream
        binding.switch4k.isChecked = cam.is4K
    }

    private fun saveCamera() {
        val host      = binding.etHost.text.toString().trim()
        val rtspPort  = binding.etRtspPort.text.toString().toIntOrNull() ?: 554
        val httpPort  = binding.etHttpPort.text.toString().toIntOrNull() ?: 80
        val username  = binding.etUsername.text.toString().trim()
        val password  = binding.etPassword.text.toString()
        val subStream = binding.switchSubStream.isChecked
        val is4K      = binding.switch4k.isChecked

        if (host.isBlank()) { binding.etHost.error = "Host / IP is required"; return }
        if (username.isBlank()) { binding.etUsername.error = "Username is required"; return }

        lifecycleScope.launch {
            if (binding.switchMultiChannel.isChecked && editingCamera == null) {
                // ── Multi-channel insert ──────────────────────────────────────
                val from   = binding.etChannelFrom.text.toString().toIntOrNull() ?: 1
                val to     = binding.etChannelTo.text.toString().toIntOrNull() ?: from
                val prefix = binding.etNamePrefix.text.toString().trim()

                if (from > to) {
                    Toast.makeText(this@AddCameraActivity,
                        "From channel must be ≤ To channel", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val count = to - from + 1
                for (ch in from..to) {
                    repo.insert(Camera(
                        name = cameraName(prefix, ch),
                        host = host, rtspPort = rtspPort, httpPort = httpPort,
                        username = username, password = password,
                        channel = ch, subStream = subStream, is4K = is4K
                    ))
                }
                Toast.makeText(this@AddCameraActivity,
                    "Added $count camera${if (count > 1) "s" else ""}",
                    Toast.LENGTH_SHORT).show()

            } else {
                // ── Single insert / edit ──────────────────────────────────────
                val name    = binding.etName.text.toString().trim()
                val channel = binding.etChannel.text.toString().toIntOrNull() ?: 1
                val cam = (editingCamera ?: Camera(
                    name = "", host = "", username = "", password = ""
                )).copy(
                    name = name, host = host, rtspPort = rtspPort, httpPort = httpPort,
                    username = username, password = password,
                    channel = channel, subStream = subStream, is4K = is4K
                )
                if (editingCamera != null) repo.update(cam) else repo.insert(cam)
            }
            finish()
        }
    }

    // ── Test connection ───────────────────────────────────────────────────────

    private fun testConnection() {
        val host     = binding.etHost.text.toString().trim()
        val httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 80
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (host.isBlank()) { binding.etHost.error = "Enter a host first"; return }

        val temp = Camera(name = "test", host = host, httpPort = httpPort,
                          username = username, password = password, channel = 1)
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "Testing…"

        lifecycleScope.launch {
            val ok = LorexApiClient.isReachable(temp)
            binding.btnTestConnection.isEnabled = true
            binding.btnTestConnection.text = "Test Connection"
            Toast.makeText(this@AddCameraActivity,
                if (ok) "✓ Camera reachable" else "✗ Could not reach camera",
                Toast.LENGTH_SHORT).show()
        }
    }
}
