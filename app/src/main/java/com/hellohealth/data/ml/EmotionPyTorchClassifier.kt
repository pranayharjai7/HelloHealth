package com.hellohealth.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * PyTorch Lite emotion classifier over the `enet_b0_8_va_mtl.ptl` model (8 classes:
 * Anger, Contempt, Disgust, Fear, Happiness, Neutral, Sadness, Surprise — the model NEVER
 * emits "Calm"; that label is manual-only).
 *
 * Ported verbatim from the MyEmotions source app (`data/local/mtcnn/EmotionPyTorchClassifier.kt`).
 * Input pipeline (224×224 + torchvision ImageNet normalization + softmax over the logits) is part
 * of the model contract and must not be changed. Unlike the TFLite graphs, the `.ptl` must be
 * copied to filesDir before load ([assetFilePath]) because [LiteModuleLoader.load] needs a real
 * filesystem path. Construction can throw if the model fails to load; callers wrap this in a
 * capability check and fall back to manual logging.
 */
class EmotionPyTorchClassifier(context: Context) {

    private val labels: List<String>
    private val module: Module
    private val width = 224
    private val height = 224

    init {
        module = LiteModuleLoader.load(assetFilePath(context, MODEL_FILE))
        labels = loadLabels(context)
    }

    fun recognize(bitmap: Bitmap): String {
        val res = classifyImage(bitmap)
        val scores = res.second
        val numEmotions = Math.min(labels.size, scores.size)
        val index = Array(numEmotions) { it }

        index.sortWith { idx1, idx2 -> java.lang.Float.compare(scores[idx2], scores[idx1]) }
        return labels[index[0]]
    }

    fun recognizeWithConfidence(bitmap: Bitmap): Pair<String, Float> {
        val res = classifyImage(bitmap)
        val scores = res.second
        val numEmotions = Math.min(labels.size, scores.size)
        val index = Array(numEmotions) { it }

        // Apply softmax to get probabilities
        val maxScore = scores.maxOrNull() ?: 0f
        var sumExp = 0f
        val probabilities = FloatArray(numEmotions)
        for (i in 0 until numEmotions) {
            probabilities[i] = kotlin.math.exp(scores[i] - maxScore)
            sumExp += probabilities[i]
        }
        for (i in 0 until numEmotions) {
            probabilities[i] /= sumExp
        }

        index.sortWith { idx1, idx2 -> java.lang.Float.compare(probabilities[idx2], probabilities[idx1]) }

        val topLabel = labels[index[0]]
        val topScore = probabilities[index[0]]

        return Pair(topLabel, topScore)
    }

    private fun classifyImage(bitmap: Bitmap): Pair<Long, FloatArray> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            scaledBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        val startTime = SystemClock.uptimeMillis()
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val timecostMs = SystemClock.uptimeMillis() - startTime
        val scores = outputTensor.dataAsFloatArray
        return Pair(timecostMs, scores)
    }

    private fun loadLabels(context: Context): List<String> {
        val labels = mutableListOf<String>()
        try {
            context.assets.open("emotionsLabel.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val categoryInfo = line.trim().split(":")
                    if (categoryInfo.size > 1) {
                        labels.add(categoryInfo[1])
                    }
                }
            }
        } catch (e: IOException) {
            AppLogger.e(FeatureTag.EMOTION_ML, "Error reading label file", e)
        }
        return labels
    }

    companion object {
        private const val MODEL_FILE = "enet_b0_8_va_mtl.ptl"

        @Throws(IOException::class)
        fun assetFilePath(context: Context, assetName: String): String {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }

            context.assets.open(assetName).use { `is` ->
                FileOutputStream(file).use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (`is`.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
            }
            return file.absolutePath
        }
    }
}
