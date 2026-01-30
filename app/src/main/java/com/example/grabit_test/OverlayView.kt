package com.example.grabitTest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 그릴 데이터
    private var detectionBoxes = listOf<DetectionBox>()
    private var handLandmarks = listOf<List<NormalizedLandmark>>()

    // Paint 객체들
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val handPointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val handLinePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
    }

    data class DetectionBox(
        val label: String,
        val confidence: Float,
        val rect: RectF
    )

    // YOLOX 결과 설정
    fun setDetections(boxes: List<DetectionBox>) {
        detectionBoxes = boxes
        invalidate()
    }

    // MediaPipe Hands 결과 설정
    fun setHands(results: HandLandmarkerResult?) {
        handLandmarks = results?.landmarks() ?: emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. YOLOX 박스 그리기
        detectionBoxes.forEach { box ->
            canvas.drawRect(box.rect, boxPaint)
            canvas.drawText(
                "${box.label} ${(box.confidence * 100).toInt()}%",
                box.rect.left,
                box.rect.top - 10f,
                textPaint
            )
        }

        // 2. MediaPipe Hands 그리기
        handLandmarks.forEach { hand ->
            // 21개 점 그리기
            hand.forEach { landmark ->
                val x = landmark.x() * width
                val y = landmark.y() * height
                canvas.drawCircle(x, y, 8f, handPointPaint)
            }

            // 손가락 연결선 그리기
            drawHandConnections(canvas, hand)
        }
    }

    private fun drawHandConnections(canvas: Canvas, hand: List<NormalizedLandmark>) {
        // MediaPipe Hands 연결 정의
        val connections = listOf(
            // 손목 → 엄지
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // 손목 → 검지
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // 손목 → 중지
            Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // 손목 → 약지
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // 손목 → 새끼
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20)
        )

        connections.forEach { (start, end) ->
            val startX = hand[start].x() * width
            val startY = hand[start].y() * height
            val endX = hand[end].x() * width
            val endY = hand[end].y() * height
            canvas.drawLine(startX, startY, endX, endY, handLinePaint)
        }
    }

    fun clear() {
        detectionBoxes = emptyList()
        handLandmarks = emptyList()
        invalidate()
    }
}