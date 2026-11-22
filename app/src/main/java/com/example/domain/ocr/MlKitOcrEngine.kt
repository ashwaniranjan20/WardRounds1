package com.example.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MlKitOcrEngine {

    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri, rotationDegrees: Int = 0): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = if (rotationDegrees == 0) {
                    InputImage.fromFilePath(context, imageUri)
                } else {
                    // Load and wrap with rotation
                    val baseImage = InputImage.fromFilePath(context, imageUri)
                    InputImage.fromBitmap(baseImage.bitmapInternal ?: throw IllegalStateException("Cannot load image"), rotationDegrees)
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}
