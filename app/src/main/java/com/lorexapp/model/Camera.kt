package com.lorexapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class Camera(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val host: String,           // IP or hostname (LAN or DDNS)
    val rtspPort: Int = 554,
    val httpPort: Int = 80,
    val username: String,
    val password: String,
    val channel: Int = 1,       // camera channel on NVR/DVR (1-based)
    val subStream: Boolean = false, // false=main stream, true=sub (lower bandwidth)
    val isEnabled: Boolean = true,
    val is4K: Boolean = false,
    val sortOrder: Int = 0,
    val thumbnailPath: String? = null
) {
    /** RTSP URL used by LibVLC */
    fun rtspUrl(): String {
        val sub = if (subStream) 1 else 0
        return "rtsp://$username:$password@$host:$rtspPort" +
               "/cam/realmonitor?channel=$channel&subtype=$sub"
    }

    /** Base HTTP URL for CGI commands */
    fun httpBase(): String = "http://$host:$httpPort"

    /** Snapshot URL */
    fun snapshotUrl(): String =
        "${httpBase()}/cgi-bin/snapshot.cgi?channel=${channel - 1}"

    /** Display label */
    fun displayLabel(): String = if (name.isNotBlank()) name else "Camera $channel"
}
