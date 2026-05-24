package com.lorexapp.ui.multistream

import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lorexapp.LorexApp
import com.lorexapp.databinding.ActivityMultiStreamBinding
import com.lorexapp.databinding.ItemStreamCellBinding
import com.lorexapp.detection.PersonDetector
import com.lorexapp.model.Camera
import com.lorexapp.ui.stream.CameraStreamActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.text.SimpleDateFormat
import java.util.*

class MultiStreamActivity : AppCompatActivity() {

    companion object {
        fun start(context: android.content.Context) =
            context.startActivity(Intent(context, MultiStreamActivity::class.java))
    }

    private lateinit var binding: ActivityMultiStreamBinding

    // One entry per camera
    private data class StreamSlot(
        val camera: Camera,
        val libVLC: LibVLC,
        val player: MediaPlayer,
        val cellBinding: ItemStreamCellBinding
    )

    private val slots = mutableListOf<StreamSlot>()
    private val detector = PersonDetector()
    private val faceAdapter = FaceEntryAdapter()

    private var detectionEnabled = false
    private var detectionJob: Job? = null

    // Rotate through cameras one at a time for detection
    private var detectionCursor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full-screen
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding.btnMsBack.setOnClickListener { finish() }

        // Faces RecyclerView
        binding.rvFaces.layoutManager = LinearLayoutManager(this)
        binding.rvFaces.adapter = faceAdapter

        binding.btnMsDetection.setOnClickListener { toggleDetection() }

        lifecycleScope.launch {
            buildGrid(getCameras())
        }
    }

    private suspend fun getCameras(): List<Camera> =
        (application as LorexApp).repository.allCameras.first()

    private fun buildGrid(cameras: List<Camera>) {
        if (cameras.isEmpty()) return

        binding.tvMsCount.text = "${cameras.size} camera${if (cameras.size > 1) "s" else ""}"

        val cellWidthPx = resources.displayMetrics.widthPixels * 3 / 4 / 2  // 2 columns in 3/4 width
        val cellHeightPx = cellWidthPx * 9 / 16

        cameras.forEach { cam ->
            val cellBinding = ItemStreamCellBinding.inflate(layoutInflater)
            cellBinding.tvCellName.text = cam.displayLabel()
            cellBinding.tvCellStatus.text = "Connecting…"

            // Set cell dimensions for 16:9
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).also {
                it.width  = cellWidthPx
                it.height = cellHeightPx
                it.setMargins(2, 2, 2, 2)
            }
            binding.gridCameras.addView(cellBinding.root, params)

            // Tap to open fullscreen
            cellBinding.root.setOnClickListener {
                CameraStreamActivity.start(this, cam.id)
            }

            // Start stream — use sub-stream for grid to save bandwidth
            val vlcOptions = ArrayList<String>().apply {
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                add("--rtsp-tcp")
                add(if (cam.is4K) "--network-caching=1500" else "--network-caching=400")
                add("--clock-jitter=0")
                add("--clock-synchro=0")
            }
            val libVLC = LibVLC(this, vlcOptions)
            val player = MediaPlayer(libVLC)

            // Force sub-stream URL for grid (saves bandwidth across many cameras)
            val gridUrl = cam.copy(subStream = true).rtspUrl()
            val media = Media(libVLC, android.net.Uri.parse(gridUrl))
            media.setHWDecoderEnabled(true, false)
            player.media = media
            media.release()
            player.attachViews(cellBinding.vlcCell, null, false, false)

            player.setEventListener { event ->
                runOnUiThread {
                    when (event.type) {
                        MediaPlayer.Event.Playing ->
                            cellBinding.tvCellStatus.text = ""
                        MediaPlayer.Event.EncounteredError ->
                            cellBinding.tvCellStatus.text = "Error"
                        MediaPlayer.Event.EndReached ->
                            cellBinding.tvCellStatus.text = "Ended"
                    }
                }
            }
            player.play()

            slots.add(StreamSlot(cam, libVLC, player, cellBinding))
        }
    }

    // ── Face detection ────────────────────────────────────────────────────────

    private fun toggleDetection() {
        detectionEnabled = !detectionEnabled
        binding.btnMsDetection.text = if (detectionEnabled) "Stop Detection" else "Start Detection"
        binding.tvDetectionHint.visibility = if (detectionEnabled) View.GONE else View.VISIBLE

        if (detectionEnabled) {
            startDetectionLoop()
        } else {
            detectionJob?.cancel()
            detectionJob = null
        }
    }

    /**
     * Rotates through cameras one at a time: capture frame → detect faces →
     * crop each face → add to the right panel.
     * One camera every 2 seconds, so a 9-camera system cycles every ~18 s.
     */
    private fun startDetectionLoop() {
        detectionJob?.cancel()
        detectionJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive && detectionEnabled) {
                if (slots.isEmpty()) { delay(1000); continue }

                val slot = slots[detectionCursor % slots.size]
                detectionCursor++

                val bitmap = captureCell(slot)
                if (bitmap == null) { delay(500); continue }

                try {
                    val faces = detector.detectFaces(bitmap)
                    if (faces.isNotEmpty()) {
                        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                        val entries = faces.mapNotNull { face ->
                            val cropped = cropFace(bitmap, face.boundingBox) ?: return@mapNotNull null
                            FaceEntry(
                                face = cropped,
                                cameraName = slot.camera.displayLabel(),
                                time = ts
                            )
                        }
                        if (entries.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                faceAdapter.addFaces(entries)
                            }
                        }
                    }
                    bitmap.recycle()
                } catch (_: Exception) {
                    bitmap.recycle()
                }

                delay(2000)
            }
        }
    }

    private suspend fun captureCell(slot: StreamSlot): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val v = slot.cellBinding.vlcCell
            if (v.width == 0 || v.height == 0) { cont.resumeWith(Result.success(null)); return@suspendCancellableCoroutine }
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                window,
                android.graphics.Rect(v.left, v.top, v.right, v.bottom),
                bmp,
                { result ->
                    if (result == PixelCopy.SUCCESS) cont.resumeWith(Result.success(bmp))
                    else { bmp.recycle(); cont.resumeWith(Result.success(null)) }
                },
                Handler(Looper.getMainLooper())
            )
        }

    private fun cropFace(src: Bitmap, box: android.graphics.Rect): Bitmap? {
        val l = box.left.coerceIn(0, src.width - 1)
        val t = box.top.coerceIn(0, src.height - 1)
        val w = (box.width()).coerceIn(1, src.width - l)
        val h = (box.height()).coerceIn(1, src.height - t)
        return if (w > 0 && h > 0) Bitmap.createBitmap(src, l, t, w, h) else null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        slots.forEach { it.player.pause() }
    }

    override fun onResume() {
        super.onResume()
        slots.forEach { it.player.play() }
    }

    override fun onDestroy() {
        detectionJob?.cancel()
        detector.close()
        slots.forEach { slot ->
            slot.player.stop()
            slot.player.detachViews()
            slot.player.release()
            slot.libVLC.release()
        }
        slots.clear()
        super.onDestroy()
    }
}
