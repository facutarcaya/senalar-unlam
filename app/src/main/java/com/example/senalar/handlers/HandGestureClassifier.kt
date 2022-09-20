package com.example.senalar.handlers

import android.content.Context
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.Category
import kotlin.math.max

class HandGestureClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val maxResults: Int?
) {
    companion object {
        private const val NUM_THREADS = 1
        private const val MAX_OPTIONS = 3
        private const val HAND_LANDMARKS_SIZE = 21
        private const val AXIS_LANDMARKS_SIZE = 2

        fun createHandGestureClassifier(context: Context, model_path: String, labels_path: String): HandGestureClassifier {
            // Create a TFLite interpreter from the TFLite model file.
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.setNumThreads(NUM_THREADS)
            val interpreter =
                Interpreter(FileUtil.loadMappedFile(context, model_path))

            // Load the label file.
            val labels = FileUtil.loadLabels(context, labels_path)

            return HandGestureClassifier(interpreter, labels, MAX_OPTIONS)
        }
    }

    fun classify(handsResult: HandsResult): List<Category> {
        var leftHandLandmarks : FloatArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)
        var rightHandLandmarks : FloatArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)

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
                labels.size
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
        val uniArrayLandmarks = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE * 2)

        var handIndex = 0
        var totalIndex = 0

        while (handIndex < HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE) {
            uniArrayLandmarks[totalIndex++] = leftHandLandmarks[handIndex++]
        }

        handIndex = 0

        while (handIndex < HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE) {
            uniArrayLandmarks[totalIndex++] = rightHandLandmarks[handIndex++]
        }

        return uniArrayLandmarks
    }

    private fun getLandMarksAsUniArray(landmarks: List<LandmarkProto.NormalizedLandmark>) : FloatArray {
        val uniArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)
        var indexArray = 0

        for (landmark in landmarks) {
            uniArray[indexArray] = landmark.x
            indexArray++
            uniArray[indexArray] = landmark.y
            indexArray++
        }

        return uniArray
    }

    fun close() {
        interpreter.close()
    }
}