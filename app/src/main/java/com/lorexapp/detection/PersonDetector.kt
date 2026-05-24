package com.lorexapp.detection

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit Object Detection (person) and Face Detection.
 */
class PersonDetector {

    data class DetectedPerson(
        val boundingBox: Rect,
        val confidence: Float,
        val trackingId: Int?
    )

    data class DetectedFace(
        val boundingBox: Rect,
        val label: String?   // set by user via labeling UI
    )

    // ── Object detector – person class ───────────────────────────────────────

    private val objectDetector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // frame-by-frame
            .enableClassification()
            .enableMultipleObjects()
            .build()
    )

    // ── Face detector ─────────────────────────────────────────────────────────

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    /**
     * Detect persons in [bitmap]. Returns boxes for objects classified as "Person".
     */
    suspend fun detectPersons(bitmap: Bitmap): List<DetectedPerson> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            objectDetector.process(image)
                .addOnSuccessListener { objects ->
                    val persons = objects
                        .filter { obj ->
                            obj.labels.any { label ->
                                label.text.equals("Person", ignoreCase = true) && label.confidence > 0.5f
                            }
                        }
                        .map { obj ->
                            val conf = obj.labels
                                .firstOrNull { it.text.equals("Person", ignoreCase = true) }
                                ?.confidence ?: 0f
                            DetectedPerson(obj.boundingBox, conf, obj.trackingId)
                        }
                    cont.resume(persons)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /**
     * Detect faces in [bitmap]. Returns face bounding boxes with optional labels.
     * Labels are looked up from [knownFaces] map (trackingId or face hash → name).
     */
    suspend fun detectFaces(
        bitmap: Bitmap,
        knownFaces: Map<Int, String> = emptyMap()
    ): List<DetectedFace> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val result = faces.map { face ->
                    val label = face.trackingId?.let { knownFaces[it] }
                    DetectedFace(face.boundingBox, label)
                }
                cont.resume(result)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun close() {
        objectDetector.close()
        faceDetector.close()
    }
}
