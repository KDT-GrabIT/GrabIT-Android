package com.example.grabitTest

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceprintStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(voiceprint: FloatArray) {
        require(voiceprint.size == TitaNetOnnxRunner.EMBEDDING_DIM) {
            "Expected ${TitaNetOnnxRunner.EMBEDDING_DIM}-D voiceprint, got ${voiceprint.size}."
        }
        val bytes = ByteBuffer.allocate(voiceprint.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (value in voiceprint) bytes.putFloat(value)
        prefs.edit()
            .putString(KEY_VOICEPRINT, Base64.encodeToString(bytes.array(), Base64.NO_WRAP))
            .apply()
    }

    fun load(): FloatArray? {
        val encoded = prefs.getString(KEY_VOICEPRINT, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size != TitaNetOnnxRunner.EMBEDDING_DIM * Float.SIZE_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(TitaNetOnnxRunner.EMBEDDING_DIM) { buffer.getFloat() }
    }

    fun clear() {
        prefs.edit().remove(KEY_VOICEPRINT).apply()
    }

    fun hasVoiceprint(): Boolean = load() != null

    companion object {
        private const val PREFS_NAME = "speaker_verification"
        private const val KEY_VOICEPRINT = "titanet_s_voiceprint"
    }
}
