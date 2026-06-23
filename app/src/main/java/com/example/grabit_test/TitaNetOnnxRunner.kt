package com.example.grabitTest

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlin.math.sqrt

class TitaNetOnnxRunner(
    context: Context,
    assetName: String = MODEL_ASSET_NAME
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputSignalName: String
    private val inputLengthName: String
    private val outputName: String

    init {
        val modelBytes = context.assets.open(assetName).use { it.readBytes() }
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
        inputSignalName = session.inputNames.first { it == "input_signal" }
        inputLengthName = session.inputNames.first { it == "input_signal_length" }
        outputName = session.outputNames.first()
    }

    fun extractEmbedding(pcm16kMonoFloat: FloatArray, validSamples: Int = pcm16kMonoFloat.size): FloatArray {
        val fixed = FloatArray(FIXED_SAMPLE_COUNT)
        val copyLength = minOf(pcm16kMonoFloat.size, FIXED_SAMPLE_COUNT)
        System.arraycopy(pcm16kMonoFloat, 0, fixed, 0, copyLength)
        val length = longArrayOf(maxOf(1, minOf(validSamples, FIXED_SAMPLE_COUNT)).toLong())

        OnnxTensor.createTensor(env, arrayOf(fixed)).use { signalTensor ->
            OnnxTensor.createTensor(env, length).use { lengthTensor ->
                session.run(
                    mapOf(
                        inputSignalName to signalTensor,
                        inputLengthName to lengthTensor
                    )
                ).use { result ->
                    val value = result[outputName].get().value
                    val embedding = when (value) {
                        is Array<*> -> (value[0] as FloatArray).copyOf()
                        is FloatArray -> value.copyOf()
                        else -> error("Unexpected TitaNet output type: ${value::class.java.name}")
                    }
                    return l2Normalize(embedding)
                }
            }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val MODEL_ASSET_NAME = "titanet_s.onnx"
        const val SAMPLE_RATE = 16_000
        const val FIXED_SECONDS = 2
        const val FIXED_SAMPLE_COUNT = SAMPLE_RATE * FIXED_SECONDS
        const val EMBEDDING_DIM = 192

        fun cosine(a: FloatArray, b: FloatArray): Float {
            require(a.size == b.size) { "Embedding dimensions differ: ${a.size} vs ${b.size}" }
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA * normB)
            return if (denom > 0.0) (dot / denom).toFloat() else 0f
        }

        fun l2Normalize(values: FloatArray): FloatArray {
            var sum = 0.0
            for (value in values) sum += value * value
            val norm = sqrt(sum).toFloat()
            if (norm <= 0f) return values
            for (i in values.indices) values[i] /= norm
            return values
        }

        fun averageAndNormalize(embeddings: List<FloatArray>): FloatArray {
            require(embeddings.isNotEmpty()) { "No embeddings to average." }
            val dim = embeddings.first().size
            val mean = FloatArray(dim)
            for (embedding in embeddings) {
                require(embedding.size == dim) { "Embedding dimensions differ." }
                for (i in 0 until dim) mean[i] += embedding[i]
            }
            for (i in 0 until dim) mean[i] /= embeddings.size.toFloat()
            return l2Normalize(mean)
        }
    }
}
