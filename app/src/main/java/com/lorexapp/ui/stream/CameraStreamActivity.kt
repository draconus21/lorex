package com.lorexapp.ui.stream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.*
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.lorexapp.LorexApp
import com.lorexapp.databinding.ActivityCameraStreamBinding
import com.lorexapp.detection.PersonDetector
import com.lorexapp.model.Camera
import com.lorexapp.network.LorexApiClient
import com.lorexapp.network.TalkbackManager
import com.lorexapp.R
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraStreamActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAMERA_ID = "camera_id"
        fun start(context: Context, cameraId: Int) =
            context.startActivity(Intent(context, CameraStreamActivity::class.java).apply {
                putExtra(EXTRA_CAMERA_ID, cameraId)
            })
    }

    private lateinit var binding: ActivityCameraStreamBinding

    private var camera: Camera? = null
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private val detector = PersonDetector()
    private var detectionJob: Job? = null
    private var detectionEnabled = false

    // Mute
    private var isMuted = false

    // Talkback
    private var talkbackManager: TalkbackManager? = null

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) takeSnapshot() }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startTalkback()
        else Toast.makeText(this, "Microphone permission required for talk", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full-screen immersive
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val cameraId = intent.getIntExtra(EXTRA_CAMERA_ID, -1)
        if (cameraId == -1) { finish(); return }

        lifecycleScope.launch {
            val app = application as LorexApp
            camera = app.repository.getById(cameraId) ?: run { finish(); return@launch }
            setupUi()
            startStream()
        }
    }

    private fun setupUi() {
        val cam = camera ?: return

        binding.tvCameraName.text = cam.displayLabel()
        binding.tvRtspUrl.text = "Ch ${cam.channel} · ${cam.host}"
        binding.tv4kBadge.visibility = if (cam.is4K) View.VISIBLE else View.GONE

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSnapshot.setOnClickListener { requestSnapshot() }
        binding.btnDetection.setOnClickListener { toggleDetection() }

        // Mute toggle
        binding.btnMute.setOnClickListener { toggleMute() }

        // Push-to-talk: hold to talk, release to stop
        binding.btnTalk.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { requestTalkback(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopTalkback(); true }
                else -> false
            }
        }

        // PTZ buttons — start on press, stop on release
        listOf(
            binding.btnPtzUp    to LorexApiClient.PtzAction.UP,
            binding.btnPtzDown  to LorexApiClient.PtzAction.DOWN,
            binding.btnPtzLeft  to LorexApiClient.PtzAction.LEFT,
            binding.btnPtzRight to LorexApiClient.PtzAction.RIGHT,
            binding.btnZoomIn   to LorexApiClient.PtzAction.ZOOM_IN,
            binding.btnZoomOut  to LorexApiClient.PtzAction.ZOOM_OUT
        ).forEach { (btn, action) ->
            btn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lifecycleScope.launch { camera?.let { LorexApiClient.ptzStart(it, action) } }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        lifecycleScope.launch { camera?.let { LorexApiClient.ptzStop(it, action) } }
                        true
                    }
                    else -> false
                }
            }
        }

        binding.btnPtzToggle.setOnClickListener {
            val vis = if (binding.ptzPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            binding.ptzPanel.visibility = vis
        }
    }

    private fun startStream() {
        val cam = camera ?: return

        // 4K H.265 streams need a much larger network buffer and explicit HW codec hints.
        // 1080p streams use a tight 200 ms cache to keep latency low.
        val networkCache = if (cam.is4K) 1500 else 200

        val options = ArrayList<String>().apply {
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("--rtsp-tcp")
            add("--network-caching=$networkCache")
            add("--clock-jitter=0")
            add("--clock-synchro=0")
            if (cam.is4K) {
                // Prefer hardware H.265 decoder; fall back through the chain automatically
                add("--codec=mediacodec_ndk,mediacodec,iomx,any")
                add("--video-filter=")          // disable sw filters that stall on 4K
            }
        }
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC!!).apply {
            val media = Media(libVLC, android.net.Uri.parse(cam.rtspUrl()))
            media.setHWDecoderEnabled(true, false)
            this.media = media
            media.release()
            // VLCVideoLayout is required by LibVLC 3.x Android binding
            attachViews(binding.vlcVideoLayout, null, false, false)
            play()
        }

        binding.tvStatus.text = "Connecting…"
        mediaPlayer?.setEventListener { event ->
            runOnUiThread {
                when (event.type) {
                    MediaPlayer.Event.Playing  -> binding.tvStatus.text = ""
                    MediaPlayer.Event.EncounteredError -> {
                        binding.tvStatus.text = "Stream error – retrying"
                        Handler(Looper.getMainLooper()).postDelayed({ retryStream() }, 3000)
                    }
                    MediaPlayer.Event.EndReached -> binding.tvStatus.text = "Stream ended"
                }
            }
        }
    }

    private fun retryStream() {
        releasePlayer()
        startStream()
    }

    // ── Snapshot (fetched via HTTP from camera, not frame-grabbed) ────────────

    private fun requestSnapshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        takeSnapshot()
    }

    private fun takeSnapshot() {
        val cam = camera ?: return
        lifecycleScope.launch {
            binding.btnSnapshot.isEnabled = false
            LorexApiClient.fetchSnapshot(cam)
                .onSuccess { bitmap ->
                    saveBitmap(bitmap, cam.displayLabel())
                    Toast.makeText(this@CameraStreamActivity, "Snapshot saved", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(this@CameraStreamActivity, "Snapshot failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            binding.btnSnapshot.isEnabled = true
        }
    }

    private fun saveBitmap(bitmap: Bitmap, cameraName: String) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "LorexCam_${cameraName}_$ts.jpg"
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }
        dir?.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
    }

    // ── Person Detection ──────────────────────────────────────────────────────

    private fun toggleDetection() {
        detectionEnabled = !detectionEnabled
        if (detectionEnabled) {
            binding.btnDetection.alpha = 1f
            startDetectionLoop()
        } else {
            binding.btnDetection.alpha = 0.5f
            detectionJob?.cancel()
            binding.detectionOverlay.clear()
        }
    }

    private fun startDetectionLoop() {
        detectionJob?.cancel()
        detectionJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive && detectionEnabled) {
                val bitmap = captureFrameViaPixelCopy()
                if (bitmap != null) {
                    try {
                        val persons = detector.detectPersons(bitmap)
                        val faces   = detector.detectFaces(bitmap)
                        withContext(Dispatchers.Main) {
                            binding.detectionOverlay.detectionScaleX =
                                binding.detectionOverlay.width.toFloat() / bitmap.width
                            binding.detectionOverlay.detectionScaleY =
                                binding.detectionOverlay.height.toFloat() / bitmap.height
                            binding.detectionOverlay.updateDetections(persons, faces)
                        }
                        bitmap.recycle()
                    } catch (_: Exception) {}
                }
                delay(500) // ~2 fps
            }
        }
    }

    /**
     * Captures the video surface using PixelCopy (API 26+, matches minSdk).
     * LibVLC's Android binding has no snapshot() method; PixelCopy reads
     * directly from the hardware-composited surface.
     */
    private suspend fun captureFrameViaPixelCopy(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val surface = binding.vlcVideoLayout
            if (surface.width == 0 || surface.height == 0) {
                cont.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }
            val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                window,
                android.graphics.Rect(
                    surface.left, surface.top,
                    surface.right, surface.bottom
                ),
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        cont.resumeWith(Result.success(bitmap))
                    } else {
                        bitmap.recycle()
                        cont.resumeWith(Result.success(null))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }

    // ── Mute ──────────────────────────────────────────────────────────────────

    private fun toggleMute() {
        isMuted = !isMuted
        mediaPlayer?.setVolume(if (isMuted) 0 else 100)
        binding.btnMute.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
        binding.btnMute.alpha = if (isMuted) 0.5f else 1f
    }

    // ── Talkback ──────────────────────────────────────────────────────────────

    private fun requestTalkback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startTalkback()
        }
    }

    private fun startTalkback() {
        val cam = camera ?: return
        if (talkbackManager?.isRunning == true) return
        talkbackManager = TalkbackManager(cam).also { it.start(lifecycleScope) }
        binding.btnTalk.setImageResource(R.drawable.ic_mic_active)
        binding.btnTalk.setBackgroundResource(R.drawable.bg_talk_button_active)
        binding.btnTalk.alpha = 1f
    }

    private fun stopTalkback() {
        talkbackManager?.stop()
        talkbackManager = null
        binding.btnTalk.setImageResource(R.drawable.ic_mic)
        binding.btnTalk.setBackgroundResource(R.drawable.bg_talk_button)
        binding.btnTalk.alpha = 0.85f
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.play()
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        detector.close()
        stopTalkback()
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
    }
}
