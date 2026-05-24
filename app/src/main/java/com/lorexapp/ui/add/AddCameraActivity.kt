package com.lorexapp.ui.add

import android.os.Bundle
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
            // Editing an existing camera — hide the copy button
            binding.btnCopyLast.visibility = View.GONE
            lifecycleScope.launch {
                val cam = repo.getById(cameraId)
                if (cam != null) { editingCamera = cam; populateFields(cam) }
            }
        } else {
            // Adding a new camera — show copy button only if there's a previous entry
            lifecycleScope.launch {
                val last = repo.getLastCamera()
                binding.btnCopyLast.visibility = if (last != null) View.VISIBLE else View.GONE
                binding.btnCopyLast.setOnClickListener {
                    if (last != null) copyFromLast(last)
                }
            }
        }

        binding.btnSave.setOnClickListener { saveCamera() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    /** Fill all fields from [source], then bump the channel by 1 so the
     *  user only has to change the camera name for the typical NVR use-case. */
    private fun copyFromLast(source: Camera) {
        populateFields(source.copy(
            channel = source.channel + 1,
            name = ""           // leave name blank so user types a new one
        ))
        binding.etName.requestFocus()
        Toast.makeText(this, "Copied from \"${source.displayLabel()}\"", Toast.LENGTH_SHORT).show()
    }

    private fun populateFields(cam: Camera) {
        binding.etName.setText(cam.name)
        binding.etHost.setText(cam.host)
        binding.etRtspPort.setText(cam.rtspPort.toString())
        binding.etHttpPort.setText(cam.httpPort.toString())
        binding.etUsername.setText(cam.username)
        binding.etPassword.setText(cam.password)
        binding.etChannel.setText(cam.channel.toString())
        binding.switchSubStream.isChecked = cam.subStream
    }

    private fun saveCamera() {
        val name      = binding.etName.text.toString().trim()
        val host      = binding.etHost.text.toString().trim()
        val rtspPort  = binding.etRtspPort.text.toString().toIntOrNull() ?: 554
        val httpPort  = binding.etHttpPort.text.toString().toIntOrNull() ?: 80
        val username  = binding.etUsername.text.toString().trim()
        val password  = binding.etPassword.text.toString()
        val channel   = binding.etChannel.text.toString().toIntOrNull() ?: 1
        val subStream = binding.switchSubStream.isChecked

        if (host.isBlank()) { binding.etHost.error = "Host / IP is required"; return }
        if (username.isBlank()) { binding.etUsername.error = "Username is required"; return }

        lifecycleScope.launch {
            val cam = (editingCamera ?: Camera(name = "", host = "", username = "", password = "")).copy(
                name = name, host = host, rtspPort = rtspPort, httpPort = httpPort,
                username = username, password = password, channel = channel, subStream = subStream
            )
            if (editingCamera != null) repo.update(cam) else repo.insert(cam)
            finish()
        }
    }

    private fun testConnection() {
        val host     = binding.etHost.text.toString().trim()
        val httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 80
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val channel  = binding.etChannel.text.toString().toIntOrNull() ?: 1

        if (host.isBlank()) { binding.etHost.error = "Enter a host first"; return }

        val temp = Camera(name = "test", host = host, httpPort = httpPort,
                          username = username, password = password, channel = channel)
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
