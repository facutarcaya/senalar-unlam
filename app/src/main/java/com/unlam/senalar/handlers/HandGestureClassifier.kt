package com.unlam.senalar.handlers

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
) : HandClassifier {
    companion object {
        private const val NUM_THREADS = 4
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

    override fun classify(handsResult: HandsResult): List<Category> {
        var leftHandLandmarks : FloatArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)
        var rightHandLandmarks : FloatArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)

        val numHands =  handsResult.multiHandLandmarks().size

        if (numHands == 0) {
            val dummyCategories = mutableListOf<Category>()
            dummyCategories.add(Category("None1", 0.0f))
            dummyCategories.add(Category("None2", 0.0f))
            dummyCategories.add(Category("None3", 0.0f))
            return dummyCategories
        }

        val indexHands = 0

        leftHandLandmarks = getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList, false)
        rightHandLandmarks = getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList, true)


        val outputval = Array(1) {
            FloatArray(
                labels.size
            )
        }

        interpreter.run(leftHandLandmarks, outputval)

        val categoryList1 = createCategoryList(outputval[0])

        interpreter.run(rightHandLandmarks, outputval)

        val categoryList2 = createCategoryList(outputval[0])

        return if (categoryList1[0].score > categoryList2[0].score) categoryList1 else categoryList2
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

    private fun getLandMarksAsUniArray(landmarks: List<LandmarkProto.NormalizedLandmark>, flipLandmark: Boolean) : FloatArray {
        val uniArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)
        var indexArray = 0

        for (landmark in landmarks) {
            uniArray[indexArray] = if (flipLandmark) flipLandmarkPoint(landmark.x) else landmark.x
            indexArray++
            uniArray[indexArray] = landmark.y
            indexArray++
        }

        return uniArray
    }

    private fun flipLandmarkPoint(landMarkPoint: Float): Float {
        return 1.0f - landMarkPoint
    }

    override fun close() {
        interpreter.close()
    }
}