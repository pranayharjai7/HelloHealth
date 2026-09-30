package com.hellohealth.data.food

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] that reads product barcodes (EAN-13 / EAN-8 / UPC-A / UPC-E) with
 * ML Kit's on-device scanner and reports the FIRST valid hit exactly once via [onBarcode].
 *
 * Fire-once: a scan screen wants a single result, not a stream, so an [AtomicBoolean] latch drops
 * every frame after the first hit — the caller unbinds the analyzer on the callback. Hardened per
 * the no-crash guarantee: a failed detection just closes the frame and waits for the next one; the
 * analyzer never throws into the CameraX pipeline. Callers still supply their own timeout / manual
 * fallback for the "camera sees nothing" case.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val delivered = AtomicBoolean(false)

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError") // imageProxy.image is stable CameraX usage
    override fun analyze(imageProxy: ImageProxy) {
        if (delivered.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                if (value != null && delivered.compareAndSet(false, true)) {
                    onBarcode(value)
                }
            }
            .addOnFailureListener { t ->
                AppLogger.w(FeatureTag.NUTRITION, "barcode analyze failed: ${t.message}")
            }
            .addOnCompleteListener {
                // Always release the frame so CameraX can hand us the next one.
                imageProxy.close()
            }
    }
}
