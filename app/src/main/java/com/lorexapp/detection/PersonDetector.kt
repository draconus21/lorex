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

/**
 * Wraps ML Kit Object Detection (person) and Face Detection.
 * Uses resumeWith(Result) throughout to avoid the onCancellation
 * overload ambiguity introduced in Kotlin 1.9.
 */
class PersonDetector {

    data class DetectedPerson(
        val boundingBox: Rect,
        val confidence: Float,
        val trackingId: Int?
    )

    data class DetectedFace(
        val boundingBox: Rect,
        val label: String?
    )

    private val objectDetector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    suspend fun detectPersons(bitmap: Bitmap): List<DetectedPerson> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            objectDetector.process(image)
                .addOnSuccessListener { objects ->
                    val persons = objects
                        .filter { obj ->
                            obj.labels.any { label ->
                                label.text.equals("Person", ignoreCase = true) &&
                                    label.confidence > 0.5f
                            }
                        }
                        .map { obj ->
                            val conf = obj.labels
                                .firstOrNull { it.text.equals("Person", ignoreCase = true) }
                                ?.confidence ?: 0f
                            DetectedPerson(obj.boundingBox, conf, obj.trackingId)
                        }
                    cont.resumeWith(Result.success(persons))
                }
                .addOnFailureListener { e ->
                    cont.resumeWith(Result.failure(e))
                }
        }

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
                cont.resumeWith(Result.success(result))
            }
            .addOnFailureListener { e ->
                cont.resumeWith(Result.failure(e))
            }
    }

    fun close() {
        objectDetector.close()
        faceDetector.close()
    }
}
