package com.example.senalar.handlers

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class HandClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val maxResults: Int?
) {
    private val inputShape = interpreter
        .getInputTensor(0)
        .shape()
    private val outputCategoryCount = interpreter
        .getOutputTensor(0)
        .shape()[1]

    companion object {
        private const val ACCURACY_THRESHOLD = 0.7f
        private const val MODEL_PATH = "keypoint_image_classifier.tflite"
        private const val LABELS_PATH = "keypoint_image_labels.txt"
        private const val NUM_THREADS = 1
        private const val MAX_OPTIONS = 3

        fun createHandClassifier(context: Context): HandClassifier {
            // Create a TFLite interpreter from the TFLite model file.
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.setNumThreads(NUM_THREADS)
            val interpreter =
                Interpreter(FileUtil.loadMappedFile(context, MODEL_PATH))

            // Load the label file.
            val labels = FileUtil.loadLabels(context, LABELS_PATH)

            return HandClassifier(interpreter, labels, MAX_OPTIONS)
        }
    }
}