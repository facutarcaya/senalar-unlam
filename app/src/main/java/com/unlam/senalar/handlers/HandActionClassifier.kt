package com.unlam.senalar.handlers

import android.content.Context
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.Category
import java.util.*
import kotlin.math.max

class HandActionClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val maxResults: Int?
) : HandClassifier {
    var frameCount = 0
    var frameQueue = LinkedList<FloatArray>()

    companion object {
        private const val NUM_THREADS = 4
        private const val MAX_OPTIONS = 3
        private const val HAND_LANDMARKS_SIZE = 21
        private const val AXIS_LANDMARKS_SIZE = 2
        private const val MODEL_FPS = 16
        private const val NO_RESULT = "Esperando..."

        fun createHandActionClassifier(context: Context, model_path: String, labels_path: String): HandActionClassifier {
            // Create a TFLite interpreter from the TFLite model file.
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.setNumThreads(NUM_THREADS)
            val interpreter =
                Interpreter(FileUtil.loadMappedFile(context, model_path))

            // Load the label file.
            val labels = FileUtil.loadLabels(context, labels_path)

            return HandActionClassifier(interpreter, labels, MAX_OPTIONS)
        }
    }

    override fun classify(handsResult: HandsResult): List<Category> {
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

        val uniArrayLandmarks = getFinalUniArray(leftHandLandmarks, rightHandLandmarks)

        frameQueue.add(uniArrayLandmarks)

        if (frameQueue.size < MODEL_FPS) {
            val dummyCategories = mutableListOf<Category>()
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            dummyCategories.add(Category(NO_RESULT, 0.0f))
            return dummyCategories
        }

        if (frameQueue.size > MODEL_FPS) {
            frameQueue.poll()
        }

        val framesUniArrayLandmarks = getFramesFinalUniArray(frameQueue)

        val outputval = Array(1) {
            FloatArray(
                labels.size
            )
        }

        interpreter.run(framesUniArrayLandmarks, outputval)

        val categoryList = createCategoryList(outputval.get(0))

        return categoryList
    }

    private fun getFramesFinalUniArray(frameQueue: LinkedList<FloatArray>): FloatArray {
        val frameArrayLandmarks = FloatArray(HAND_LANDMARKS_SIZE * AXIS_LANDMARKS_SIZE * 2 * MODEL_FPS)

        var frameIndex = 0
        var totalIndex = 0

        frameQueue.forEach {
            while (frameIndex < it.size) {
                frameArrayLandmarks[totalIndex] = it[frameIndex]
                frameIndex++
                totalIndex++
            }
            frameIndex = 0
        }

        return frameArrayLandmarks
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

    override fun close() {
        interpreter.close()
    }
}