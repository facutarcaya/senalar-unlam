package com.unlam.senalar.handlers

import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.support.label.Category

interface HandClassifier {
    fun classify(handsResult: HandsResult): List<Category>

    fun close()
}