/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent sync.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Full-screen camera QR scanner. Requests CAMERA permission, shows live
 * preview, and calls [onScanned] with the first decoded text result.
 * Caller is responsible for dismissing the composable after callback.
 *
 * Decoding is pure ZXing-core on the CameraX luminance plane: no ML Kit,
 * no Google Play services, no bundled ML model.
 *
 * @param onScanned callback with decoded QR text
 * @param onDismiss callback when user taps the close button
 */
@Composable
fun QrScanner(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(
                onScanned = onScanned,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Camera permission needed to scan QR codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Close button — top-right
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close scanner",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // One reader per thread is required: MultiFormatReader keeps mutable state.
    val reader = remember { MultiFormatReader() }
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!scanned) {
                                val result = decodeQr(imageProxy, reader, hints)
                                imageProxy.close()
                                if (result != null && !scanned) {
                                    scanned = true
                                    onScanned(result)
                                }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Camera init failed — nothing to do, preview stays blank
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

/**
 * Decodes a QR code from a CameraX YUV frame with ZXing, synchronously on
 * the analyzer thread. Returns null when nothing is found. The caller owns
 * [imageProxy] and closes it.
 */
private fun decodeQr(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
    hints: Map<DecodeHintType, Any>
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val rotation = imageProxy.imageInfo.rotationDegrees

    // Pack the Y plane (rowStride may exceed width) into a dense buffer.
    val width = plane.rowStride
    val height = imageProxy.height
    val buffer = plane.buffer
    buffer.rewind()
    val packed = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(row * plane.rowStride)
        buffer.get(packed, row * width, width)
    }

    // Rotate the luminance data so it matches the displayed orientation.
    // rotateLuminance transposes, so after a 90/270 turn the dimensions swap.
    val turns = (rotation / 90) % 4
    var data = packed
    var srcW = width
    var srcH = height
    repeat(turns) {
        data = rotateLuminance(data, srcW, srcH)
        val t = srcW; srcW = srcH; srcH = t
    }

    val source = PlanarYUVLuminanceSource(data, srcW, srcH, 0, 0, srcW, srcH, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decode(bitmap, hints)?.text
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/** Rotates a packed luminance matrix 90 degrees clockwise. */
private fun rotateLuminance(src: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(src.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[x * height + (height - 1 - y)] = src[y * width + x]
        }
    }
    return out
}
