package com.hellohealth.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.util.HashMap
import java.util.Vector
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MTCNN cascade face detector (P-Net → R-Net → O-Net) backed by three TFLite graphs.
 *
 * Ported verbatim from the MyEmotions source app (`data/local/mtcnn/MTCNN.kt`). The three
 * `.tflite` graphs are memory-mapped straight from the APK assets via [FileUtil.loadMappedFile]
 * (which is why the build keeps them uncompressed — see `noCompress` in build.gradle.kts).
 *
 * The cascade constants (thresholds 0.6/0.7/0.7, pyramid factor 0.709, min-face 32px, the named
 * output tensors, the (px-127.5)/128 normalization) are part of the algorithm and must not be
 * changed — they are baked into the model files. Construction can throw if the graphs fail to
 * load; callers wrap this in a capability check and fall back to manual logging.
 */
class MTCNN(context: Context) {
    private val factor = 0.709f
    private val pNetThreshold = 0.6f
    private val rNetThreshold = 0.7f
    private val oNetThreshold = 0.7f

    private val pInterpreter: Interpreter
    private val rInterpreter: Interpreter
    private val oInterpreter: Interpreter

    init {
        val options = Interpreter.Options().apply { numThreads = 4 }
        pInterpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE_PNET), options)
        rInterpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE_RNET), options)
        oInterpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE_ONET), options)
    }

    fun detectFaces(bitmap: Bitmap, minFaceSize: Int): Vector<Box> {
        var boxes = Vector<Box>()
        try {
            boxes = pNet(bitmap, minFaceSize)
            square_limit(boxes, bitmap.width, bitmap.height)
            boxes = rNet(bitmap, boxes)
            square_limit(boxes, bitmap.width, bitmap.height)
            boxes = oNet(bitmap, boxes)
        } catch (e: IllegalArgumentException) {
            AppLogger.w(FeatureTag.EMOTION_ML, "MTCNN detectFaces failed; returning no faces", e)
            boxes = Vector<Box>()
        }
        return boxes
    }

    private fun square_limit(boxes: Vector<Box>, w: Int, h: Int) {
        for (i in boxes.indices) {
            boxes[i].toSquareShape()
            boxes[i].limitSquare(w, h)
        }
    }

    private fun pNet(bitmap: Bitmap, minSize: Int): Vector<Box> {
        val whMin = min(bitmap.width, bitmap.height)
        var currentFaceSize = minSize.toFloat()
        val totalBoxes = Vector<Box>()

        while (currentFaceSize <= whMin) {
            val scale = 12.0f / currentFaceSize
            val bm = bitmapResize(bitmap, scale)
            val w = bm.width
            val h = bm.height

            val outW = ceil(w * 0.5 - 5).roundToInt()
            val outH = ceil(h * 0.5 - 5).roundToInt()

            // Note: TFLite expects [1, height, width, channels] implicitly for NHWC,
            // but the original code mapped axes explicitly. The mapping was: 1xWxHxC
            var prob1 = Array(1) { Array(outW) { Array(outH) { FloatArray(2) } } }
            var conv4_2_BiasAdd = Array(1) { Array(outW) { Array(outH) { FloatArray(4) } } }

            pNetForward(bm, prob1, conv4_2_BiasAdd)
            prob1 = transposeBatch(prob1)
            conv4_2_BiasAdd = transposeBatch(conv4_2_BiasAdd)

            val curBoxes = Vector<Box>()
            generateBoxes(prob1, conv4_2_BiasAdd, scale, curBoxes)
            nms(curBoxes, 0.5f, "Union")

            for (i in curBoxes.indices) {
                if (!curBoxes[i].deleted) totalBoxes.addElement(curBoxes[i])
            }
            currentFaceSize /= factor
        }

        nms(totalBoxes, 0.7f, "Union")
        BoundingBoxReggression(totalBoxes)
        return updateBoxes(totalBoxes)
    }

    private fun pNetForward(bitmap: Bitmap, prob1: Array<Array<Array<FloatArray>>>, conv4_2_BiasAdd: Array<Array<Array<FloatArray>>>) {
        val img = normalizeImage(bitmap)
        var pNetIn = Array(1) { img }
        pNetIn = transposeBatch(pNetIn)

        val outputs = HashMap<Int, Any>()
        outputs[pInterpreter.getOutputIndex("pnet/prob1")] = prob1
        outputs[pInterpreter.getOutputIndex("pnet/conv4-2/BiasAdd")] = conv4_2_BiasAdd

        pInterpreter.runForMultipleInputsOutputs(arrayOf<Any>(pNetIn), outputs)
    }

    private fun generateBoxes(prob1: Array<Array<Array<FloatArray>>>, conv4_2_BiasAdd: Array<Array<Array<FloatArray>>>, scale: Float, boxes: Vector<Box>) {
        val h = prob1[0].size
        val w = prob1[0][0].size

        for (y in 0 until h) {
            for (x in 0 until w) {
                val score = prob1[0][y][x][1]
                if (score > pNetThreshold) {
                    val box = Box()
                    box.score = score
                    box.box[0] = (x * 2 / scale).roundToInt()
                    box.box[1] = (y * 2 / scale).roundToInt()
                    box.box[2] = ((x * 2 + 11) / scale).roundToInt()
                    box.box[3] = ((y * 2 + 11) / scale).roundToInt()
                    for (i in 0..3) {
                        box.bbr[i] = conv4_2_BiasAdd[0][y][x][i]
                    }
                    boxes.addElement(box)
                }
            }
        }
    }

    private fun nms(boxes: Vector<Box>, threshold: Float, method: String) {
        for (i in boxes.indices) {
            val box = boxes[i]
            if (!box.deleted) {
                for (j in i + 1 until boxes.size) {
                    val box2 = boxes[j]
                    if (!box2.deleted) {
                        val x1 = max(box.box[0], box2.box[0])
                        val y1 = max(box.box[1], box2.box[1])
                        val x2 = min(box.box[2], box2.box[2])
                        val y2 = min(box.box[3], box2.box[3])
                        if (x2 < x1 || y2 < y1) continue
                        val areaIoU = (x2 - x1 + 1) * (y2 - y1 + 1)
                        var iou = 0f
                        if (method == "Union") iou = 1.0f * areaIoU / (box.area() + box2.area() - areaIoU)
                        else if (method == "Min") iou = 1.0f * areaIoU / min(box.area(), box2.area())
                        if (iou >= threshold) {
                            if (box.score > box2.score) box2.deleted = true else box.deleted = true
                        }
                    }
                }
            }
        }
    }

    private fun BoundingBoxReggression(boxes: Vector<Box>) {
        for (i in boxes.indices) boxes[i].calibrate()
    }

    private fun rNet(bitmap: Bitmap, boxes: Vector<Box>): Vector<Box> {
        val num = boxes.size
        val rNetIn = Array(num) { Array(24) { Array(24) { FloatArray(3) } } }
        for (i in 0 until num) {
            var curCrop = cropAndResize(bitmap, boxes[i], 24)
            curCrop = transposeImage(curCrop)
            rNetIn[i] = curCrop
        }
        rNetForward(rNetIn, boxes)
        for (i in 0 until num) {
            if (boxes[i].score < rNetThreshold) {
                boxes[i].deleted = true
            }
        }
        nms(boxes, 0.7f, "Union")
        BoundingBoxReggression(boxes)
        return updateBoxes(boxes)
    }

    private fun rNetForward(rNetIn: Array<Array<Array<FloatArray>>>, boxes: Vector<Box>) {
        val num = rNetIn.size
        if (num == 0) return
        val prob1 = Array(num) { FloatArray(2) }
        val conv5_2_conv5_2 = Array(num) { FloatArray(4) }

        val outputs = HashMap<Int, Any>()
        outputs[rInterpreter.getOutputIndex("rnet/prob1")] = prob1
        outputs[rInterpreter.getOutputIndex("rnet/conv5-2/conv5-2")] = conv5_2_conv5_2
        rInterpreter.runForMultipleInputsOutputs(arrayOf<Any>(rNetIn), outputs)

        for (i in 0 until num) {
            boxes[i].score = prob1[i][1]
            for (j in 0..3) {
                boxes[i].bbr[j] = conv5_2_conv5_2[i][j]
            }
        }
    }

    private fun oNet(bitmap: Bitmap, boxes: Vector<Box>): Vector<Box> {
        val num = boxes.size
        val oNetIn = Array(num) { Array(48) { Array(48) { FloatArray(3) } } }
        for (i in 0 until num) {
            var curCrop = cropAndResize(bitmap, boxes[i], 48)
            curCrop = transposeImage(curCrop)
            oNetIn[i] = curCrop
        }
        oNetForward(oNetIn, boxes)
        for (i in 0 until num) {
            if (boxes[i].score < oNetThreshold) {
                boxes[i].deleted = true
            }
        }
        BoundingBoxReggression(boxes)
        nms(boxes, 0.7f, "Min")
        return updateBoxes(boxes)
    }

    private fun oNetForward(oNetIn: Array<Array<Array<FloatArray>>>, boxes: Vector<Box>) {
        val num = oNetIn.size
        if (num == 0) return
        val prob1 = Array(num) { FloatArray(2) }
        val conv6_2_conv6_2 = Array(num) { FloatArray(4) }
        val conv6_3_conv6_3 = Array(num) { FloatArray(10) }

        val outputs = HashMap<Int, Any>()
        outputs[oInterpreter.getOutputIndex("onet/prob1")] = prob1
        outputs[oInterpreter.getOutputIndex("onet/conv6-2/conv6-2")] = conv6_2_conv6_2
        outputs[oInterpreter.getOutputIndex("onet/conv6-3/conv6-3")] = conv6_3_conv6_3
        oInterpreter.runForMultipleInputsOutputs(arrayOf<Any>(oNetIn), outputs)

        for (i in 0 until num) {
            boxes[i].score = prob1[i][1]
            for (j in 0..3) {
                boxes[i].bbr[j] = conv6_2_conv6_2[i][j]
            }
            for (j in 0..4) {
                val x = (boxes[i].left() + conv6_3_conv6_3[i][j] * boxes[i].width()).roundToInt()
                val y = (boxes[i].top() + conv6_3_conv6_3[i][j + 5] * boxes[i].height()).roundToInt()
                boxes[i].landmark[j] = Point(x, y)
            }
        }
    }

    companion object {
        private const val MODEL_FILE_PNET = "pnet.tflite"
        private const val MODEL_FILE_RNET = "rnet.tflite"
        private const val MODEL_FILE_ONET = "onet.tflite"

        fun updateBoxes(boxes: Vector<Box>): Vector<Box> {
            val b = Vector<Box>()
            for (i in boxes.indices) {
                if (!boxes[i].deleted) {
                    b.addElement(boxes[i])
                }
            }
            return b
        }

        fun normalizeImage(bitmap: Bitmap): Array<Array<FloatArray>> {
            val h = bitmap.height
            val w = bitmap.width
            val floatValues = Array(h) { Array(w) { FloatArray(3) } }

            val imageMean = 127.5f
            val imageStd = 128f

            val pixels = IntArray(h * w)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, w, h)
            for (i in 0 until h) {
                for (j in 0 until w) {
                    val `val` = pixels[i * w + j]
                    val r = (((`val` shr 16) and 0xFF) - imageMean) / imageStd
                    val g = (((`val` shr 8) and 0xFF) - imageMean) / imageStd
                    val b = ((`val` and 0xFF) - imageMean) / imageStd
                    floatValues[i][j] = floatArrayOf(r, g, b)
                }
            }
            return floatValues
        }

        fun bitmapResize(bitmap: Bitmap, scale: Float): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        }

        fun transposeImage(`in`: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
            val h = `in`.size
            val w = `in`[0].size
            val channel = `in`[0][0].size
            val out = Array(w) { Array(h) { FloatArray(channel) } }
            for (i in 0 until h) {
                for (j in 0 until w) {
                    out[j][i] = `in`[i][j]
                }
            }
            return out
        }

        fun transposeBatch(`in`: Array<Array<Array<FloatArray>>>): Array<Array<Array<FloatArray>>> {
            val batch = `in`.size
            val h = `in`[0].size
            val w = `in`[0][0].size
            val channel = `in`[0][0][0].size
            val out = Array(batch) { Array(w) { Array(h) { FloatArray(channel) } } }
            for (i in 0 until batch) {
                for (j in 0 until h) {
                    for (k in 0 until w) {
                        out[i][k][j] = `in`[i][j][k]
                    }
                }
            }
            return out
        }

        fun cropAndResize(bitmap: Bitmap, box: Box, size: Int): Array<Array<FloatArray>> {
            val matrix = Matrix()
            val scaleW = 1.0f * size / box.width()
            val scaleH = 1.0f * size / box.height()
            matrix.postScale(scaleW, scaleH)
            val rect = box.transform2Rect()
            // Make sure crop coordinates are inside the bitmap!
            val cropX = max(0, rect.left)
            val cropY = max(0, rect.top)
            val cropW = min(bitmap.width - cropX, box.width())
            val cropH = min(bitmap.height - cropY, box.height())

            // Fix invalid crop if box is completely outside
            if (cropW <= 0 || cropH <= 0) {
                return normalizeImage(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888))
            }

            val cropped = Bitmap.createBitmap(
                bitmap, cropX, cropY, cropW, cropH, matrix, true
            )
            return normalizeImage(cropped)
        }
    }
}
