# LorexCam – Android App

A native Android app to stream, control, and monitor your Lorex IP cameras.

## Features

| Feature | Details |
|---|---|
| **RTSP Streaming** | Live video via LibVLC — main stream or sub-stream |
| **Multi-camera grid** | 9+ cameras in a 2- or 3-column grid with thumbnails |
| **Full-screen stream** | Tap any camera to go fullscreen |
| **PTZ controls** | Pan / Tilt / Zoom (press & hold to move, release to stop) |
| **Snapshots** | Downloads JPEG snapshot from camera, saved to Gallery |
| **Person detection** | On-device ML Kit — green bounding boxes around people |
| **Face detection** | Blue boxes around detected faces (label via long-press) |
| **Local + Remote** | Works on LAN (IP) and remotely via DDNS hostname |
| **Camera management** | Add / edit / delete cameras stored in a local Room DB |
| **Test connection** | One-tap reachability check before saving |

---

## Requirements

- Android Studio Hedgehog (2023.1) or newer
- Android SDK 34
- A physical Android device or emulator (API 26+)
- Lorex NVR/DVR or standalone IP cameras with RTSP enabled

---

## Quick Start

### 1. Open in Android Studio
```
File → Open → select the LorexCam/ folder
```
Gradle will sync automatically and download all dependencies (~200 MB first run).

### 2. Build & run
- Connect your Android device (USB debugging on) **or** start an emulator
- Click ▶ Run (or Shift+F10)

### 3. Add your first camera
1. Tap the **+** FAB button
2. Fill in your camera details:

| Field | Example |
|---|---|
| Camera Name | Front Door |
| Host / IP | `192.168.1.100` (LAN) or `yourddns.lorex.com` (remote) |
| RTSP Port | `554` (default) |
| HTTP Port | `80` (default) |
| Username | `admin` |
| Password | your Lorex password |
| Channel | `1` for first camera, `2` for second, etc. |
| Sub-stream | Toggle ON for lower bandwidth / slower network |

3. Tap **Test Connection** to verify reachability
4. Tap **Save Camera**

---

## RTSP URL Format

The app builds RTSP URLs automatically using the Dahua/Lorex SDK format:
```
rtsp://username:password@host:554/cam/realmonitor?channel=N&subtype=0
```
- `channel` = 1-based channel number
- `subtype=0` = main stream (high quality), `subtype=1` = sub-stream (lower bandwidth)

---

## PTZ Control

Lorex cameras use the **Dahua CGI API** for PTZ:
```
GET /cgi-bin/ptz.cgi?action=start&channel=0&code=Up&arg1=0&arg2=4&arg3=0
GET /cgi-bin/ptz.cgi?action=stop&channel=0&code=Up&arg1=0&arg2=0&arg3=0
```
- **Press & hold** any PTZ button → camera moves
- **Release** → camera stops
- Speed is fixed at 4/8 — you can change `speed` in `LorexApiClient.kt`

**Supported directions:** Up, Down, Left, Right, ZoomTele (in), ZoomWide (out)

---

## Person Detection & Face Recognition

### Person detection
- Tap the 👤 button in the stream toolbar to toggle on/off
- Runs at ~2 FPS to save battery
- Green boxes drawn around detected persons

### Face detection
- Automatically runs alongside person detection
- Blue boxes drawn around faces
- To label a face: long-press the box → type the person's name
  *(Labels are stored in-memory per session; persistence can be added with Room)*

### About "recognition"
Full face **recognition** (identifying who someone is across sessions) requires either:
- Training a custom TensorFlow Lite model with FaceNet embeddings, **or**
- A cloud API (AWS Rekognition, Google Cloud Vision)

The current implementation detects and tracks faces during a session. For persistent recognition, see `PersonDetector.kt` — the `knownFaces` map can be populated from a database of saved face embeddings.

---

## Remote Access Setup

### Option A – Port forwarding (direct RTSP)
Forward these ports on your router to the NVR's local IP:
- **RTSP:** 554 (or your custom port)
- **HTTP:** 80 (or your custom port)

Then use your **public IP** or **DDNS hostname** as the host in the app.

### Option B – Lorex DDNS
Lorex provides free DDNS. In the NVR web UI:
- Network → DDNS → Enable → note the hostname (e.g. `abc123.lorexddns.net`)
- Use that hostname as the camera host in this app

---

## Project Structure

```
app/src/main/java/com/lorexapp/
├── model/          Camera.kt              – data class + RTSP URL builder
├── data/           CameraDao, DB, Repo    – Room persistence
├── viewmodel/      CameraViewModel        – MVVM bridge
├── network/        LorexApiClient         – PTZ + snapshot HTTP calls
├── detection/      PersonDetector         – ML Kit wrapper
│                   DetectionOverlayView   – bounding box canvas overlay
└── ui/
    ├── MainActivity                       – navigation host
    ├── grid/  CameraGridFragment          – multi-camera grid
    │          CameraGridAdapter
    ├── stream/ CameraStreamActivity       – fullscreen player + PTZ
    └── add/    AddCameraActivity          – camera add/edit form
```

---

## Dependencies

| Library | Purpose |
|---|---|
| `org.videolan.android:libvlc-all:3.6.0` | RTSP video playback |
| `com.google.mlkit:object-detection` | On-device person detection |
| `com.google.mlkit:face-detection` | Face bounding boxes |
| `androidx.room` | Local camera config database |
| `com.squareup.okhttp3` | PTZ & snapshot HTTP requests |
| `io.coil-kt:coil` | Thumbnail image loading |
| `androidx.navigation` | Fragment navigation |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Black screen / no stream | Check RTSP URL, credentials, and that RTSP is enabled in NVR settings |
| "Stream error – retrying" | Verify port 554 is accessible; try sub-stream toggle |
| PTZ not working | Confirm HTTP port is open; some cameras use port 8080 |
| Snapshot fails | Check HTTP port and credentials; try `http://host:port/cgi-bin/snapshot.cgi?channel=0` in browser |
| Remote not working | Check port forwarding rules; try the DDNS hostname |
| Person detection slow | Expected — runs at 2 FPS by default to preserve battery. Adjust `delay(500)` in `CameraStreamActivity.kt` |

---

## Licence
MIT — free to use and modify for personal use.
