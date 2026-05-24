package com.lorexapp.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lorexapp.model.Camera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Handles Lorex/Dahua CGI HTTP commands:
 *  - PTZ (pan, tilt, zoom start/stop)
 *  - Snapshot capture
 *  - Light control (IR / white light)
 */
object LorexApiClient {

    private fun buildClient(camera: Camera): OkHttpClient {
        val credentials = Credentials.basic(camera.username, camera.password)
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .authenticator { _, response ->
                if (response.request.header("Authorization") != null) null
                else response.request.newBuilder()
                    .header("Authorization", credentials)
                    .build()
            }
            .build()
    }

    // ── PTZ Commands ─────────────────────────────────────────────────────────

    enum class PtzAction { UP, DOWN, LEFT, RIGHT, ZOOM_IN, ZOOM_OUT, STOP }

    private fun ptzCode(action: PtzAction): String = when (action) {
        PtzAction.UP       -> "Up"
        PtzAction.DOWN     -> "Down"
        PtzAction.LEFT     -> "Left"
        PtzAction.RIGHT    -> "Right"
        PtzAction.ZOOM_IN  -> "ZoomTele"
        PtzAction.ZOOM_OUT -> "ZoomWide"
        PtzAction.STOP     -> "Stop"
    }

    suspend fun ptzStart(camera: Camera, action: PtzAction, speed: Int = 4): Result<Unit> =
        ptzCommand(camera, "start", ptzCode(action), speed)

    suspend fun ptzStop(camera: Camera, action: PtzAction = PtzAction.STOP): Result<Unit> =
        ptzCommand(camera, "stop", ptzCode(action), 0)

    private suspend fun ptzCommand(
        camera: Camera, act: String, code: String, speed: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ch = camera.channel - 1
            val url = "${camera.httpBase()}/cgi-bin/ptz.cgi" +
                      "?action=$act&channel=$ch&code=$code&arg1=0&arg2=$speed&arg3=0"
            val req = Request.Builder().url(url).build()
            buildClient(camera).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("PTZ error ${resp.code}: ${resp.message}")
            }
        }
    }

    // ── Preset recall ─────────────────────────────────────────────────────────

    suspend fun gotoPreset(camera: Camera, presetNo: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ch = camera.channel - 1
                val url = "${camera.httpBase()}/cgi-bin/ptz.cgi" +
                          "?action=start&channel=$ch&code=GotoPreset&arg1=0&arg2=$presetNo&arg3=0"
                val req = Request.Builder().url(url).build()
                buildClient(camera).newCall(req).execute().use { }
            }
        }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    suspend fun fetchSnapshot(camera: Camera): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(camera.snapshotUrl()).build()
                val bytes = buildClient(camera).newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Snapshot error ${resp.code}")
                    resp.body?.bytes() ?: error("Empty response")
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("Failed to decode snapshot image")
            }
        }

    // ── Light control ─────────────────────────────────────────────────────────

    enum class LightMode { AUTO, ON, OFF }

    /**
     * Cycles the camera IR / white light:
     *   AUTO  – camera decides based on ambient light
     *   ON    – force illuminators on at full brightness
     *   OFF   – turn all illuminators off
     */
    suspend fun setLight(camera: Camera, mode: LightMode): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ch = camera.channel - 1
                val modeStr = when (mode) {
                    LightMode.AUTO -> "Auto"
                    LightMode.ON   -> "Manual"
                    LightMode.OFF  -> "Off"
                }
                var url = "${camera.httpBase()}/cgi-bin/configManager.cgi" +
                          "?action=setConfig&Lighting[$ch][0].Mode=$modeStr"
                if (mode == LightMode.ON) {
                    url += "&Lighting[$ch][0].MiddleLight[0].Light=100"
                }
                val req = Request.Builder().url(url).build()
                buildClient(camera).newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Light error ${resp.code}: ${resp.message}")
                }
            }
        }

    // ── Reachability check ────────────────────────────────────────────────────

    suspend fun isReachable(camera: Camera): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${camera.httpBase()}/cgi-bin/magicBox.cgi?action=getSystemInfo")
                .build()
            buildClient(camera).newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
