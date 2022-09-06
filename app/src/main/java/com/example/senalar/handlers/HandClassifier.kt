package com.example.senalar.handlers

import android.content.Context
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.Category
import kotlin.math.max

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

    fun classify(handsResult: HandsResult): List<Category> {
        var leftHandLandmarks : FloatArray = FloatArray(63)
        var rightHandLandmarks : FloatArray = FloatArray(63)

        val numHands =  handsResult.multiHandLandmarks().size

        var indexHands = 0

        while (indexHands < numHands) {
            if (handsResult.multiHandedness()[indexHands].label == "Left") {
                leftHandLandmarks = getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList)
            } else {
                rightHandLandmarks = getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList)
            }

            indexHands++
        }

        var uniArrayLandmarks = getFinalUniArray(leftHandLandmarks, rightHandLandmarks)

        val outputval = Array(1) {
            FloatArray(
                6
            )
        }

        interpreter.run(uniArrayLandmarks, outputval)

        val categoryList = createCategoryList(outputval.get(0))

        return categoryList
    }

    private fun createCategoryList(outputsInFloat: FloatArray): List<Category> {
        var categories = mutableListOf<Category>()
        outputsInFloat.forEachIndexed { index, probability ->
            categories.add(Category(labels[index], probability))
        }

        categories.sortByDescending { it.score }

        maxResults?.let {
            categories = categories.subList(0, max(maxResults, categories.size))
        }

        return categories
    }

    private fun getFinalUniArray(
        leftHandLandmarks: FloatArray,
        rightHandLandmarks: FloatArray
    ): FloatArray {
        val uniArrayLandmarks = FloatArray(126)

        var handIndex = 0
        var totalIndex = 0

        while (handIndex < 63) {
            uniArrayLandmarks[totalIndex++] = leftHandLandmarks[handIndex++]
        }

        handIndex = 0

        while (handIndex < 63) {
            uniArrayLandmarks[totalIndex++] = rightHandLandmarks[handIndex++]
        }

        return uniArrayLandmarks
    }

    private fun getLandMarksAsUniArray(landmarks: List<LandmarkProto.NormalizedLandmark>) : FloatArray {
        val uniArray = FloatArray(63)
        var indexArray = 0

        for (landmark in landmarks) {
            uniArray[indexArray] = landmark.x
            indexArray++
            uniArray[indexArray] = landmark.y
            indexArray++
            uniArray[indexArray] = landmark.z
            indexArray++
        }

        return uniArray
    }

    fun close() {
        interpreter.close()
    }
}