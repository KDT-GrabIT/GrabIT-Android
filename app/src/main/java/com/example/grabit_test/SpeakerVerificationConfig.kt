package com.example.grabitTest

object SpeakerVerificationConfig {
    const val LOG_TAG = "SpeakerVerification"
    const val RECORD_MILLIS = 2_000
    const val ENROLL_RECORDING_COUNT = 3

    // PC ONNX benchmark EER threshold. Keep this easy to change during device tuning.
    const val DEFAULT_THRESHOLD = 0.379785f
    const val EER_THRESHOLD = 0.379785f
    const val FA0_THRESHOLD = 0.666932f

    val THRESHOLD_CANDIDATES = floatArrayOf(
        0.35f,
        0.38f,
        0.40f,
        0.45f,
        0.50f,
        0.60f,
        0.66f
    )
}
