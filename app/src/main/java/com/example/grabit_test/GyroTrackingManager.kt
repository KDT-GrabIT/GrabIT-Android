package com.example.grabitTest

import android.content.Context
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlin.math.PI

/**
 * 자이로 센서 기반 고정(IMU Locking) 추적 매니저.
 * OpenCV 트래커 없이 TYPE_ROTATION_VECTOR로 회전량을 측정해 박스 위치를 보정.
 */
class GyroTrackingManager(
    private val context: Context,
    private val runYOLOX: (android.graphics.Bitmap) -> List<OverlayView.DetectionBox>,
    private val findTargetMatch: (List<OverlayView.DetectionBox>, String) -> OverlayView.DetectionBox?,
    private val getTargetLabel: () -> String,
    private val onTransitionToLocked: (OverlayView.DetectionBox, Int, Int) -> Unit,
    private val onTransitionToSearching: () -> Unit,
    private val getCurrentBox: () -> OverlayView.DetectionBox?,
    private val setCurrentBox: (OverlayView.DetectionBox?, Int, Int) -> Unit
) : SensorEventListener {

    enum class State { SCANNING, LOCKED }

    var state: State = State.SCANNING
        private set

    var lockedTargetLabel: String = ""
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var isSensorRegistered = false

    /** 락 시점의 박스 (이미지 좌표) */
    private var initialRect: RectF = RectF()
    /** 락 시점의 회전 (azimuth, pitch, roll) rad. 첫 센서 이벤트에서 설정 */
    private var initialOrientation = FloatArray(3)
    private var hasInitialRotation = false
    /** 최신 회전 벡터 (센서에서 수신) */
    @Volatile
    private var latestRotationVector: FloatArray? = null
    private var imageWidth = 0
    private var imageHeight = 0

    /** 픽셀/도 비율 (FOV 기반) */
    private var pixelsPerDegreeX = 1f
    private var pixelsPerDegreeY = 1f

    private val R = FloatArray(9)
    private val orientation = FloatArray(3)

    /** 자이로 기반 박스 업데이트 간격 (1초 약 4회 ≈ 250ms) */
    private val GYRO_UPDATE_INTERVAL_MS = 250L
    private var lastGyroUpdateTimeMillis = 0L

    /** 스무딩 비율 (0.2 = 매 업데이트마다 목표 위치의 20%만 반영 → 둔감) */
    private val GYRO_SMOOTH_FACTOR = 0.2f
    private var smoothedRect: RectF = RectF()

    val isGyroAvailable: Boolean
        get() = rotationSensor != null

    init {
        updateFovFromCamera()
    }

    /** CameraManager로 후면 카메라 FOV를 가져와 픽셀/도 비율 계산 */
    private fun updateFovFromCamera() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val fovHKey = CameraCharacteristics.Key("android.lens.info.horizontalViewAngle", Float::class.java)
            val fovVKey = CameraCharacteristics.Key("android.lens.info.verticalViewAngle", Float::class.java)
            val fovH = chars.get(fovHKey) ?: 60f
            val fovV = chars.get(fovVKey)
                ?: if (imageWidth > 0 && imageHeight > 0) fovH * imageHeight / imageWidth else 45f

            if (imageWidth > 0 && imageHeight > 0) {
                pixelsPerDegreeX = imageWidth / fovH
                pixelsPerDegreeY = imageHeight / fovV
                Log.d(TAG, "FOV: H=${fovH}° V=${fovV}° -> px/deg X=$pixelsPerDegreeX Y=$pixelsPerDegreeY")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FOV 계산 실패, 기본값 사용", e)
            pixelsPerDegreeX = 20f
            pixelsPerDegreeY = 20f
        }
    }

    /**
     * State A (SCANNING): YOLO 추론. 타겟 발견 시 initialRect/initialRotation 저장 후 LOCKED 전환.
     */
    fun processFrameSearching(bitmap: android.graphics.Bitmap): ProcessResult {
        val detections = runYOLOX(bitmap)
        val targetLabel = getTargetLabel()
        val matched = findTargetMatch(detections, targetLabel)
        if (matched != null && matched.confidence >= TARGET_CONFIDENCE) {
            imageWidth = bitmap.width
            imageHeight = bitmap.height
            initialRect.set(matched.rect)
            updateFovFromCamera()
            hasInitialRotation = false
            latestRotationVector = null
            smoothedRect.set(matched.rect)
            state = State.LOCKED
            lockedTargetLabel = matched.label
            setCurrentBox(matched, imageWidth, imageHeight)
            onTransitionToLocked(matched, imageWidth, imageHeight)
            registerSensor()
            return ProcessResult.SwitchedToLocked(matched)
        }
        return ProcessResult.Scanning(detections)
    }

    /**
     * State B (LOCKED): 자이로만 사용. 회전 delta로 박스 위치 보정.
     * YOLO 실행 안 함. 탈출 조건: 박스가 화면 완전히 벗어남.
     */
    fun processFrameLocked(bitmap: android.graphics.Bitmap): ProcessResult {
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        updateFovFromCamera()

        val currentRect = computeRectFromGyro()
        if (currentRect == null) {
            return ProcessResult.Locked(getCurrentBox() ?: run {
                val fallback = OverlayView.DetectionBox(
                    lockedTargetLabel, 0.9f, initialRect,
                    listOf(lockedTargetLabel to 90)
                )
                setCurrentBox(fallback, imageWidth, imageHeight)
                fallback
            })
        }

        if (isRectCompletelyOutside(currentRect, imageWidth, imageHeight)) {
            Log.d(TAG, "박스가 화면 밖으로 완전히 벗어남 → SCANNING 복귀")
            resetToSearching()
            return ProcessResult.ReturnedToSearching
        }

        val now = System.currentTimeMillis()
        val shouldUpdate = (now - lastGyroUpdateTimeMillis) >= GYRO_UPDATE_INTERVAL_MS
        if (shouldUpdate) {
            lastGyroUpdateTimeMillis = now
            // 스무딩: 목표 위치의 일부만 반영해 둔감하게
            smoothedRect.left = smoothedRect.left + (currentRect.left - smoothedRect.left) * GYRO_SMOOTH_FACTOR
            smoothedRect.top = smoothedRect.top + (currentRect.top - smoothedRect.top) * GYRO_SMOOTH_FACTOR
            smoothedRect.right = smoothedRect.right + (currentRect.right - smoothedRect.right) * GYRO_SMOOTH_FACTOR
            smoothedRect.bottom = smoothedRect.bottom + (currentRect.bottom - smoothedRect.bottom) * GYRO_SMOOTH_FACTOR
        }

        val box = OverlayView.DetectionBox(
            label = lockedTargetLabel,
            confidence = 0.9f,
            rect = RectF(smoothedRect),
            topLabels = listOf(lockedTargetLabel to 90)
        )
        if (shouldUpdate) setCurrentBox(box, imageWidth, imageHeight)
        return ProcessResult.Locked(getCurrentBox() ?: box)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || state != State.LOCKED) return
        latestRotationVector = event.values.clone()
        if (!hasInitialRotation) {
            computeOrientationFromVector(event.values)?.let { o ->
                initialOrientation[0] = o[0]
                initialOrientation[1] = o[1]
                initialOrientation[2] = o[2]
                hasInitialRotation = true
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastGyroUpdateTimeMillis < GYRO_UPDATE_INTERVAL_MS) return
        lastGyroUpdateTimeMillis = now

        val targetRect = computeRectFromGyro() ?: return
        if (isRectCompletelyOutside(targetRect, imageWidth, imageHeight)) {
            runOnMain { resetToSearching() }
            return
        }
        // 스무딩 적용 (processFrameLocked와 동일)
        smoothedRect.left = smoothedRect.left + (targetRect.left - smoothedRect.left) * GYRO_SMOOTH_FACTOR
        smoothedRect.top = smoothedRect.top + (targetRect.top - smoothedRect.top) * GYRO_SMOOTH_FACTOR
        smoothedRect.right = smoothedRect.right + (targetRect.right - smoothedRect.right) * GYRO_SMOOTH_FACTOR
        smoothedRect.bottom = smoothedRect.bottom + (targetRect.bottom - smoothedRect.bottom) * GYRO_SMOOTH_FACTOR

        val db = OverlayView.DetectionBox(
            label = lockedTargetLabel,
            confidence = 0.9f,
            rect = RectF(smoothedRect),
            topLabels = listOf(lockedTargetLabel to 90)
        )
        runOnMain {
            setCurrentBox(db, imageWidth, imageHeight)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun runOnMain(block: () -> Unit) {
        (context as? android.app.Activity)?.runOnUiThread(block)
    }

    private fun ensureRotationVector4(rv: FloatArray): FloatArray {
        if (rv.size >= 4) return rv
        val x = rv.getOrNull(0) ?: 0f
        val y = rv.getOrNull(1) ?: 0f
        val z = rv.getOrNull(2) ?: 0f
        val w = 1f - x * x - y * y - z * z
        return floatArrayOf(x, y, z, kotlin.math.sqrt(w.coerceAtLeast(0f)))
    }

    private fun computeOrientationFromVector(rv: FloatArray): FloatArray? {
        return try {
            val rv4 = ensureRotationVector4(rv)
            SensorManager.getRotationMatrixFromVector(R, rv4)
            val out = FloatArray(3)
            SensorManager.getOrientation(R, out)
            out
        } catch (e: Exception) {
            null
        }
    }

    /** 최신 회전 벡터로 currentRect 계산. initial과의 delta 사용 */
    private fun computeRectFromGyro(): RectF? {
        val rv = latestRotationVector ?: return null
        if (!hasInitialRotation) return null
        try {
            val current = computeOrientationFromVector(rv) ?: return null
            val deltaAzimuth = (current[0] - initialOrientation[0]).radToDeg()
            val deltaPitch = (current[1] - initialOrientation[1]).radToDeg()

            // 폰을 오른쪽으로 돌리면(양의 yaw) 박스는 왼쪽으로 → pixelShiftX = -deltaYaw * px/deg
            val pixelShiftX = -deltaAzimuth * pixelsPerDegreeX
            val pixelShiftY = deltaPitch * pixelsPerDegreeY

            return RectF(
                initialRect.left + pixelShiftX,
                initialRect.top + pixelShiftY,
                initialRect.right + pixelShiftX,
                initialRect.bottom + pixelShiftY
            )
        } catch (e: Exception) {
            Log.e(TAG, "자이로 계산 실패", e)
            return null
        }
    }

    private fun Float.radToDeg() = this * 180f / PI.toFloat()

    /** 박스가 [0,0,w,h] 영역과 전혀 겹치지 않으면 true */
    private fun isRectCompletelyOutside(rect: RectF, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        return rect.right < 0 || rect.left > w || rect.bottom < 0 || rect.top > h
    }

    private fun registerSensor() {
        if (rotationSensor != null && !isSensorRegistered) {
            sensorManager.registerListener(
                this, rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            isSensorRegistered = true
            Log.d(TAG, "자이로 센서 등록")
        }
    }

    private fun unregisterSensor() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
            Log.d(TAG, "자이로 센서 해제")
        }
    }

    fun resetToSearching() {
        unregisterSensor()
        state = State.SCANNING
        lockedTargetLabel = ""
        setCurrentBox(null, 0, 0)
        onTransitionToSearching()
    }

    fun resetToSearchingFromUI() = resetToSearching()

    sealed class ProcessResult {
        data class Scanning(val detections: List<OverlayView.DetectionBox>) : ProcessResult()
        data class SwitchedToLocked(val box: OverlayView.DetectionBox) : ProcessResult()
        data class Locked(val box: OverlayView.DetectionBox) : ProcessResult()
        object ReturnedToSearching : ProcessResult()
    }

    companion object {
        private const val TAG = "GyroTracking"
        private const val TARGET_CONFIDENCE = 0.6f  // 60% 이상에서만 고정 (잘못된 클래스 방지)
    }
}
