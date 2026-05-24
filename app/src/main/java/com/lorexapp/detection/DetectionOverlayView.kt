package com.lorexapp.detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.lorexapp.R

/**
 * Transparent overlay that draws person and face bounding boxes
 * on top of the video SurfaceView.
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val personPaint = Paint().apply {
        color = Color.parseColor("#FF4CAF50")  // green
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val facePaint = Paint().apply {
        color = Color.parseColor("#FF2196F3")  // blue
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    // Scale factors: detection runs on a potentially different resolution
    var scaleX: Float = 1f
    var scaleY: Float = 1f

    private var persons: List<PersonDetector.DetectedPerson> = emptyList()
    private var faces: List<PersonDetector.DetectedFace> = emptyList()

    fun updateDetections(
        persons: List<PersonDetector.DetectedPerson>,
        faces: List<PersonDetector.DetectedFace>
    ) {
        this.persons = persons
        this.faces = faces
        invalidate()
    }

    fun clear() {
        persons = emptyList()
        faces = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        persons.forEach { person ->
            val box = person.boundingBox.toScaled()
            canvas.drawRect(box, personPaint)
            val label = "Person ${(person.confidence * 100).toInt()}%"
            drawLabel(canvas, label, box.left, box.top - 8f, personPaint.color)
        }

        faces.forEach { face ->
            val box = face.boundingBox.toScaled()
            canvas.drawRect(box, facePaint)
            val label = face.label ?: "Unknown"
            drawLabel(canvas, label, box.left, box.bottom + 36f, facePaint.color)
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, color: Int) {
        val textWidth = textPaint.measureText(text)
        val bgRect = RectF(x - 4f, y - 36f, x + textWidth + 8f, y + 4f)
        labelBgPaint.color = color and 0x80FFFFFF.toInt()
        canvas.drawRect(bgRect, labelBgPaint)
        canvas.drawText(text, x, y, textPaint)
    }

    private fun Rect.toScaled() = RectF(
        left * scaleX, top * scaleY,
        right * scaleX, bottom * scaleY
    )
}
