package com.unlam.senalar.handlers

import android.content.Context
import android.util.Log
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.Category
import kotlin.math.max

class HandNumberClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val maxResults: Int?
) : HandClassifier {
    companion object {
        private const val NUM_THREADS = 4
        private const val MAX_OPTIONS = 3
        private const val HAND_LANDMARKS_SIZE = 21
        private const val AXIS_LANDMARKS_SIZE = 2
        private const val NO_RESULT = "Esperando..."

        fun createHandNumberClassifier(context: Context, model_path: String, labels_path: String): HandNumberClassifier {
            // Create a TFLite interpreter from the TFLite model file.
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.setNumThreads(NUM_THREADS)
            val interpreter =
                Interpreter(FileUtil.loadMappedFile(context, model_path))

            // Load the label file.
            val labels = FileUtil.loadLabels(context, labels_path)

            return HandNumberClassifier(interpreter, labels, MAX_OPTIONS)
        }
    }

    override fun classify(handsResult: HandsResult): List<Category> {
        var leftHandLandmarks : FloatArray = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE)

        val numHands =  handsResult.multiHandLandmarks().size

        if (numHands == 0) {
            val dummyCategories = mutableListOf<Category>()
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            return dummyCategories
        }

        val indexHands = 0

        leftHandLandmarks = if (handsResult.multiHandedness()[indexHands].label == "Left") {
            getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList, true)
        } else {
            getLandMarksAsUniArray(handsResult.multiHandLandmarks()[indexHands].landmarkList, false)
        }


        val outputval = Array(1) {
            FloatArray(
                labels.size
            )
        }

        interpreter.run(leftHandLandmarks, outputval)

        val categoryList1 = createCategoryList(outputval[0])

        return categoryList1
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

        var firstLandmark = true
        var baseX = 0f
        var baseY = 0f
        for (landmark in landmarks) {
            if (firstLandmark) {
                baseX = landmark.x
                baseY = landmark.y
                firstLandmark = false
            }
            val landmarkX = landmark.x - baseX
            val landmarkY = landmark.y - baseY
            uniArray[indexArray] = if (flipLandmark) flipLandmarkPoint(landmarkX) else landmarkX
            indexArray++
            uniArray[indexArray] = landmarkY
            indexArray++
        }

        return uniArray
    }

    private fun flipLandmarkPoint(landMarkPoint: Float): Float {
        return landMarkPoint * -1
    }

    override fun close() {
        interpreter.close()
    }
}