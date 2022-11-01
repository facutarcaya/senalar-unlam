package com.unlam.senalar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Range
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.unlam.senalar.connection.ClientPC
import com.unlam.senalar.databinding.ActivityCameraBinding
import com.unlam.senalar.databinding.CameraUiContainerBinding
import com.unlam.senalar.helpers.LanguageHelper
import com.unlam.senalar.helpers.Predictions
import com.unlam.senalar.helpers.PreferencesHelper
import com.unlam.senalar.helpers.PreferencesHelper.Companion.SOUND_ON_PREF
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import com.unlam.senalar.handlers.*
import org.tensorflow.lite.support.label.Category
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@androidx.camera.core.ExperimentalGetImage
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@androidx.camera.core.ExperimentalUseCaseGroup
@androidx.camera.lifecycle.ExperimentalUseCaseGroupLifecycle
class CameraActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val debugMode = false

    private lateinit var binding : ActivityCameraBinding
    private lateinit var cameraUiContainerBinding: CameraUiContainerBinding

    private val lock = Any()
    private lateinit var executor: ExecutorService

    // Model variables
    private var handClassifier: HandClassifier? = null
    private var handWordsClassifier: HandActionClassifier? = null
    private var handWordsPredictionClassifier: HandActionClassifier? = null
    private var handNumbersClassifier: HandNumberClassifier? = null
    private var handLettersClassifier: HandLetterClassifier? = null
    private var isActionDetection = true
    private var scoreThreshold = DYNAMIC_SCORE_THRESHOLD // Min score to assume inference is correct
    private var modelFps = 16 // Model FPS
    private var currentModel = "Inicio"
    private var isPredictionModel = false
    private var comeFromWords = false

    private var lastInferenceStartTime: Long = 0
    private var lastPredictionStartTime: Long = 0
    private var lastDetectionStartTime: Long = 0

    // Saves the last result of the analysis
    private var lastResult : String = "Nothing"
    private var actionLastResult : String = "None"
    private var detectionCount = 0

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    // Char variables
    private var chartOn = true

    // Mute variables
    private var muteOn = true
    private var soundOn = true

    // Subtitles variables
    private var firstLine = mutableListOf<NewWord>()
    private var secondLine = mutableListOf<NewWord>()
    private var thirdLine = mutableListOf<NewWord>()
    private var text : String = ""

    // TTS variables
    private var tts : TextToSpeech? = null
    private lateinit var languageTranslation : String
    private lateinit var countryTranslation : String
    private lateinit var language : Locale

    // Preferences variables
    private lateinit var preferencesHelper: PreferencesHelper

    // Mediapipe variables
    private lateinit var hands: Hands

    //Server variables
    private var clientPC: ClientPC? = null
    private var connectedToPc = false

    // Predictions variables
    private lateinit var predictionsFile: Predictions

    // Letters to word variables
    private var letterToWord = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityCameraBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize preferences
        preferencesHelper = PreferencesHelper(this.applicationContext)

        // Initialize language
        initializeLanguage()

        // Initialize the TTS
        tts = TextToSpeech(this, this)

        loadPredictionsFile()

        // Initialize Hand Detection
        initializeHandsDetector()

        // Create Classifier
        createClassifiers()

        // Start the camera.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
            LayoutInflater.from(binding.preview.context),
            binding.root,
            true
        )

        initializeButtons()
    }

    private fun loadPredictionsFile() {
        lateinit var jsonString: String
        try {
            jsonString = this.assets.open("predictions/predictions_${languageTranslation}.json")
                .bufferedReader()
                .use { it.readText() }

            val predictionsType = object : TypeToken<Predictions>() {}.type
            predictionsFile = Gson().fromJson(jsonString, predictionsType)
        } catch (e: Exception) {
            predictionsFile = Predictions()
        }
    }

    private fun initializeLanguage() {
        languageTranslation = LanguageHelper.DEFAULT_LANGUAGE
        countryTranslation = LanguageHelper.DEFAULT_COUNTRY
        preferencesHelper.getStringPreference(PreferencesHelper.LANGUAGE_TRANSLATION)?.let {
            languageTranslation = it
        }
        preferencesHelper.getStringPreference(PreferencesHelper.COUNTRY_TRANSLATION)?.let {
            countryTranslation = it
        }

        language = Locale(languageTranslation, countryTranslation)
    }

    private fun initializeHandsDetector() {
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands = Hands(
            this,
            HandsOptions.builder()
                .setStaticImageMode(false)
                .setMaxNumHands(2)
                .setRunOnGpu(RUN_ON_GPU)
                .build()
        )
        hands.setErrorListener { message: String, e: RuntimeException? ->
            Log.e(
                "MEDIAPIPE_ERROR",
                "MediaPipe Hands error:$message"
            )
        }
        hands.setResultListener {
                handsResult -> runInference(handsResult)
        }
    }

    @Throws(CameraAccessException::class)
    private fun getTorchCameraId(cameraManager: CameraManager): String? {
        val cameraIdList = cameraManager.cameraIdList
        var result: String? = null
        for (id in cameraIdList) {
            if (cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!
            ) {
                result = id
                break
            }
        }
        return result
    }

    private fun initializeButtons() {
        initializeSoundButton()
        initializeMuteButton()
        initializeCameraButton()
        initializeFlashButton()
        initializeCastButton()
        initializeModelButtons()
        initializeDeleteButton()
    }

    private fun initializeDeleteButton() {
        cameraUiContainerBinding.btnDeleteWords.setOnClickListener {
            deleteLastWord()
        }

        cameraUiContainerBinding.btnDeleteWords.setOnLongClickListener {
            deleteAllWords()
            true
        }
    }

    private fun initializeModelButtons() {
        cameraUiContainerBinding.btnNumbers.setOnClickListener {
            cameraUiContainerBinding.btnNumbers.setImageDrawable(getDrawable(R.drawable.numbers_selected))
            cameraUiContainerBinding.btnWords.setImageDrawable(getDrawable(R.drawable.words_not_selected))
            cameraUiContainerBinding.btnLetters.setImageDrawable(getDrawable(R.drawable.letters_not_selected))

            handClassifier = handNumbersClassifier
            isActionDetection = false
            scoreThreshold = NUMBERS_SCORE_THRESHOLD
            modelFps = 5
            currentModel = "Números"
            isPredictionModel = false
            letterToWord = ""
            makeWordsNotDeletable()
            comeFromWords = false
        }

        cameraUiContainerBinding.btnLetters.setOnClickListener {
            cameraUiContainerBinding.btnNumbers.setImageDrawable(getDrawable(R.drawable.numbers_not_selected))
            cameraUiContainerBinding.btnWords.setImageDrawable(getDrawable(R.drawable.words_not_selected))
            cameraUiContainerBinding.btnLetters.setImageDrawable(getDrawable(R.drawable.letters_selected))

            handClassifier = handLettersClassifier
            isActionDetection = false
            scoreThreshold = LETTERS_SCORE_THRESHOLD
            modelFps = 5
            currentModel = "Letras"
            isPredictionModel = false
            letterToWord = ""
            makeWordsNotDeletable()
            comeFromWords = false
        }

        cameraUiContainerBinding.btnWords.setOnClickListener {
            cameraUiContainerBinding.btnNumbers.setImageDrawable(getDrawable(R.drawable.numbers_not_selected))
            cameraUiContainerBinding.btnWords.setImageDrawable(getDrawable(R.drawable.words_selected))
            cameraUiContainerBinding.btnLetters.setImageDrawable(getDrawable(R.drawable.letters_not_selected))

            handClassifier = handWordsClassifier
            isActionDetection = true
            scoreThreshold = DYNAMIC_SCORE_THRESHOLD
            lastDetectionStartTime = SystemClock.uptimeMillis()
            modelFps = 16
            currentModel = "Inicio"
            isPredictionModel = false
            letterToWord = ""
            makeWordsNotDeletable()
            comeFromWords = false
        }
    }

    private fun initializeCastButton() {
        cameraUiContainerBinding.btnCastToPC.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater

            if (connectedToPc) {
                with(builder) {
                    setTitle("Desconectar")
                    setMessage("¿Está seguro que detener la transmisión?")
                    setPositiveButton("OK") { dialogInterface, i ->
                        clientPC?.let { it.disconnect() }
                    }
                    setNegativeButton("CANCELAR") {dialogInterface, i -> /** DO NOTHING*/}
                    show()
                }
            } else {
                builder.setTitle("Conectar a PC")
                val dialogLayout = inflater.inflate(R.layout.alert_dialog_with_edittext, null)
                val editText  = dialogLayout.findViewById<EditText>(R.id.editText)
                builder.setView(dialogLayout)
                builder.setPositiveButton("CONECTAR") { dialogInterface, i ->
                    connectToIP(editText.text.toString())
                }
                builder.setNegativeButton("CANCELAR") {dialogInterface, i -> /** DO NOTHING*/}
                builder.show()
            }
        }
    }

    private fun connectToIP(ip: String) {
        if (ip.isNotEmpty()) {
            this.clientPC = ClientPC(this, ip)
        }
    }

    private fun initializeSoundButton() {
        soundOn = preferencesHelper.getBooleanPreference(SOUND_ON_PREF)
        if (soundOn) {
            changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchVolume, getDrawable(R.drawable.ic_baseline_volume_up_24), null)
        } else {
            changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchVolume, getDrawable(R.drawable.ic_baseline_volume_off_24), null)
        }

        cameraUiContainerBinding.btnSwitchVolume.setOnClickListener {
            if (soundOn) {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchVolume, getDrawable(R.drawable.ic_baseline_volume_off_24), null)
                soundOn = false
                preferencesHelper.setBooleanPreference(SOUND_ON_PREF, soundOn)
            } else {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchVolume, getDrawable(R.drawable.ic_baseline_volume_up_24), null)
                soundOn = true
                preferencesHelper.setBooleanPreference(SOUND_ON_PREF, soundOn)
            }
        }
    }

    private fun initializeMuteButton() {
        cameraUiContainerBinding.btnSwitchMute.setOnClickListener {
            if (muteOn) {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_pause_circle_outline_24), null)
                muteOn = false
            } else {
                // We restart the last word, since the user decided to stop the inference
                lastResult = ""

                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_play_circle_outline_24), null)
                muteOn = true
            }
        }
    }

    private fun initializeFlashButton() {
        cameraUiContainerBinding.btnSwitchChart.setOnClickListener {
            if (chartOn) {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchChart, getDrawable(R.drawable.ic_baseline_visibility_off_24), null)
                cameraUiContainerBinding.chart.visibility = View.INVISIBLE
                chartOn = false
            } else {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchChart, getDrawable(R.drawable.ic_baseline_visibility_24), null)
                cameraUiContainerBinding.chart.visibility = View.VISIBLE
                chartOn = true
            }
        }
    }

    private fun initializeCameraButton() {
        cameraUiContainerBinding.btnSwitchCamera.setOnClickListener {
            changeCamera()
        }
    }

    private fun changeCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        startCamera()
    }

    /**
     * Start the image capturing pipeline.
     */
    private fun startCamera() {
        executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Create a Preview to show the image captured by the camera on screen.
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.preview.surfaceProvider)
                }

            try {
                // Unbind use cases before rebinding.
                cameraProvider.unbindAll()

                // Create an ImageAnalysis to continuously capture still images using the camera,
                // and feed them to the TFLite model. We set the capturing frame rate to a multiply
                // of the TFLite model's desired FPS to keep the preview smooth, then drop
                // unnecessary frames during image analysis.
                val targetFpsMultiplier = MAX_CAPTURE_FPS.div(modelFps)
                val targetCaptureFps = modelFps * targetFpsMultiplier
                val builder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                val extender: Camera2Interop.Extender<*> = Camera2Interop.Extender(builder)
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(targetCaptureFps, targetCaptureFps)
                )
                val imageAnalysis = builder.build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImage(imageProxy)
                }

                // Combine the ImageAnalysis and Preview into a use case group.
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .setViewPort(binding.preview.viewPort!!)
                    .build()

                // Bind use cases to camera.
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, useCaseGroup
                )

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed.", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Run a frames received from the camera through the TFLite video classification pipeline.
     */
    private fun processImage(imageProxy: ImageProxy) {
        val image = imageProxy.image
        image?.let {
            // Convert the captured frame to Bitmap.
            val imageBitmap = Bitmap.createBitmap(
                it.width,
                it.height,
                Bitmap.Config.ARGB_8888
            )
            CalculateUtils.yuvToRgb(image, imageBitmap)

            // Rotate the image to the correct orientation.
            val rotateMatrix = Matrix()
            rotateMatrix.postRotate(
                imageProxy.imageInfo.rotationDegrees.toFloat()
            )
            val rotatedBitmap = Bitmap.createBitmap(
                imageBitmap, 0, 0, it.width, it.height,
                rotateMatrix, false
            )

            val currentTime = SystemClock.uptimeMillis()
            hands.send(rotatedBitmap, currentTime)
        }
        imageProxy.close()
    }

    private fun runInference(handsResult: HandsResult) {
        // Ensure that only one frame is processed at any given moment.
        synchronized(lock) {
            val currentTime = SystemClock.uptimeMillis()
            val diff = currentTime - lastInferenceStartTime

            // Check to ensure that we only run inference at a frequency required by the
            // model, within an acceptable error range (e.g. 10%). Discard the frames
            // that comes too early.
            if (diff * modelFps >= MILLIS_IN_SECONDS /* milliseconds */ * (1 - MODEL_FPS_ERROR_RANGE)) {

                lastInferenceStartTime = currentTime

                handClassifier?.let { classifier ->

                    // Run inference using the TFLite model.

                    val results = classifier.classify(handsResult)
                    val inputFps = MILLIS_IN_SECONDS / diff

                    showResultsInDebug(results)

                    val newWord = sanitizeNewWord(results[0].label).let {
                        if (it.lowercase() == "n" && handsResult.multiHandLandmarks().size > 1) {
                            "Ñ"
                        } else {
                            it
                        }
                    }
                    val newWordScore = results[0].score

                    if (!muteOn &&
                        (newWord != lastResult || ((currentTime - lastDetectionStartTime) / MILLIS_IN_SECONDS) > SAME_WORD_SECONDS_WINDOW) &&
                        newWordScore >= scoreThreshold &&
                        (((currentTime - lastDetectionStartTime) / MILLIS_IN_SECONDS) > NEW_WORD_SECONDS_WINDOW)) {
                        if (newWord != actionLastResult) {
                            detectionCount = 0
                            actionLastResult = newWord
                        } else {
                            detectionCount++
                            if ((detectionCount >= MIN_DETECTION_ACTION && isActionDetection)
                                ||
                                (detectionCount >= MIN_DETECTION_GESTURE && !isActionDetection)) {
                                if (!isPredictionModel || ((currentTime - lastPredictionStartTime) / MILLIS_IN_SECONDS) > DONT_DETECT_SECONDS_WINDOW) {
                                    processWord(lastResult, newWord)
                                    lastResult = newWord
                                    detectionCount = 0
                                    lastDetectionStartTime = SystemClock.uptimeMillis()
                                }
                            }
                        }
                    }

                    if (isPredictionModel && ((currentTime - lastPredictionStartTime) / MILLIS_IN_SECONDS) > PREDICTION_SECONDS_WINDOW ) {
                        returnToBaseModel()
                    }

                    if (inputFps < modelFps * (1 - MODEL_FPS_ERROR_RANGE)) {
                        Log.w(
                            TAG, "Current input FPS ($inputFps) is " +
                                    "significantly lower than the TFLite model's " +
                                    "expected FPS ($modelFps). It's likely because " +
                                    "model inference takes too long on this device."
                        )
                    }
                }
            }
        }
    }

    private fun returnToBaseModel() {
        if (handWordsClassifier != null) {
            handWordsClassifier?.close()
            handWordsClassifier = null
        }

        handWordsClassifier = HandActionClassifier.createHandActionClassifier(
            this,
            "dynamic/base_model/base_model_model.tflite",
            "dynamic/base_model/base_model_labels.txt"
        )
        handClassifier = handWordsClassifier
        isActionDetection = true
        scoreThreshold = DYNAMIC_SCORE_THRESHOLD
        lastDetectionStartTime = SystemClock.uptimeMillis()
        modelFps = 16
        currentModel = "Inicio"
        isPredictionModel = false
    }

    private fun processWord(lastWord: String, newWord: String) {
        var replacedWord = searchForPredictions(lastWord, newWord)
        var newReplacedWord = searchForStopSign(replacedWord)
        addWordToSubtitle(newReplacedWord)
        speakThroughTTS(newReplacedWord.word)
        sendToPC(newReplacedWord.word)
        changeDetectionModel(newWord)
    }

    private fun searchForStopSign(replacedWord: String): NewWord {
        if (!isActionDetection) {
            if (replacedWord.lowercase() != STOP_WORD) {
                letterToWord += replacedWord
            }
        } else {
            letterToWord = ""
        }

        return if (replacedWord.lowercase() == STOP_WORD && !isActionDetection) {
            val newLetterToWord = letterToWord.capitalize()
            letterToWord = ""
            NewWord(newLetterToWord, true)
        } else {
            NewWord(replacedWord, false)
        }
    }

    private fun changeDetectionModel(newWord: String) {
        if (isActionDetection) {
            predictionsFile.sentences?.let { sentences ->
                sentences[newWord.lowercase()]?.let { newModelName ->
                    if (newModelName == "numbers_model") {
                        runOnUiThread {
                            cameraUiContainerBinding.btnNumbers.performClick()
                            comeFromWords = true
                        }
                        return
                    }
                    if (newModelName == "letters_model") {
                        runOnUiThread {
                            cameraUiContainerBinding.btnLetters.performClick()
                            comeFromWords = true
                        }
                        return
                    }

                    if (handWordsPredictionClassifier != null) {
                        handWordsPredictionClassifier?.close()
                        handWordsPredictionClassifier = null
                    }

                    handWordsPredictionClassifier = HandActionClassifier.createHandActionClassifier(
                        this,
                        "dynamic/models/$newModelName/$newModelName.tflite",
                        "dynamic/models/$newModelName/${newModelName}_labels.txt"
                    )

                    handClassifier = handWordsPredictionClassifier

                    currentModel = newWord
                    isPredictionModel = true
                    lastPredictionStartTime = SystemClock.uptimeMillis()

                    scoreThreshold = when(newWord.lowercase()) {
                        "saber" -> 0.70
                        else -> {
                            DYNAMIC_SCORE_THRESHOLD
                        }
                    }

                    return
                }
                if (isPredictionModel) {
                    returnToBaseModel()
                }
            }
        } else {
            if (newWord.lowercase() == STOP_WORD && comeFromWords) {
                runOnUiThread {
                    cameraUiContainerBinding.btnWords.performClick()
                }
            }
        }
    }

    private fun searchForPredictions(lastWord: String, newWord: String): String {
        predictionsFile.predictions?.let { predictionsMap ->
            predictionsMap[lastWord.lowercase()]?.let { lastWordMap ->
                lastWordMap[newWord.lowercase()]?.let {
                    return it
                }
            }
        }

        predictionsFile.replacements?.let { replacementsMap ->
            replacementsMap[newWord.lowercase()]?.let {
                return it
            }
        }

        return newWord
    }

    private fun sanitizeNewWord(word: String): String {
        val splitWord = word.split("_")
        return splitWord[0]
    }

    private fun sendToPC(newWord: String) {
        val pcLanguage = when (languageTranslation) {
            LanguageHelper.ENGLISH_LANGUAGE -> "en-us"
            LanguageHelper.PORTUGUESE_LANGUAGE -> "pt"
            else -> "es-us"
        }

        this.clientPC?.let { it.addWordToQueue("${pcLanguage}/${newWord}|") }
    }

    /**
     * Function for TTS
     *
     */
    private fun speakThroughTTS(newWord: String) {
        if (soundOn) {
            tts!!.speak(newWord.lowercase(), TextToSpeech.QUEUE_ADD, null, "")
        }
    }

    private fun showResultsInDebug(results: List<Category>) {
        if (debugMode) {
            runOnUiThread {
                cameraUiContainerBinding.tvDetectedItem0.text =
                    sanitizeNewWord(results[0].label) + ": " + String.format("%.2f", results[0].score * 100) + "%"
                cameraUiContainerBinding.pgDetectedItem0.visibility = View.GONE
                cameraUiContainerBinding.tvDetectedItem1.text =
                    sanitizeNewWord(results[1].label) + ": " + String.format("%.2f", results[1].score * 100) + "%"
                cameraUiContainerBinding.pgDetectedItem1.visibility = View.GONE
                cameraUiContainerBinding.tvDetectedItem2.text =
                    sanitizeNewWord(results[2].label) + ": " + String.format("%.2f", results[2].score * 100) + "%"
                cameraUiContainerBinding.pgDetectedItem2.visibility = View.GONE
                cameraUiContainerBinding.tvModel.text = "Etapa: $currentModel"
            }
        } else {
            runOnUiThread {
                cameraUiContainerBinding.tvDetectedItem0.text =
                    sanitizeNewWord(results[0].label) + ":"
                cameraUiContainerBinding.pgDetectedItem0.visibility = View.VISIBLE
                cameraUiContainerBinding.pgDetectedItem0.progress = if ((results[0].score * 100 / scoreThreshold) >= 100) {
                        100 } else {
                    (results[2].score * 100 / scoreThreshold)
                }.toInt()
                cameraUiContainerBinding.tvDetectedItem1.text =
                            sanitizeNewWord(results[1].label) + ":"
                cameraUiContainerBinding.pgDetectedItem1.visibility = View.VISIBLE
                cameraUiContainerBinding.pgDetectedItem1.progress = if ((results[1].score * 100 / scoreThreshold) >= 100) {
                    100 } else {
                    (results[2].score * 100 / scoreThreshold)
                }.toInt()
                cameraUiContainerBinding.tvDetectedItem2.text =
                            sanitizeNewWord(results[2].label) + ":"
                cameraUiContainerBinding.pgDetectedItem2.visibility = View.VISIBLE
                cameraUiContainerBinding.pgDetectedItem2.progress = if ((results[1].score * 100 / scoreThreshold) >= 100) {
                    100 } else {
                    (results[2].score * 100 / scoreThreshold)
                }.toInt()
                cameraUiContainerBinding.tvModel.text = "Etapa: $currentModel"
            }
        }
    }

    private fun addWordToSubtitle(newWord: NewWord) {
        for (splitWord in newWord.word.split(" ")) {
            runOnUiThread {
                var breakWord = false

                if (isActionDetection || !newWord.deletable) {
                    text = "${text}$splitWord "
                    cameraUiContainerBinding.tvSubtitlesGhost.text = text
                } else {
                    deleteOldWords()
                    breakWord = true
                }

                when (cameraUiContainerBinding.tvSubtitlesGhost.lineCount) {
                    1 -> firstLine.add(NewWord(splitWord, !isActionDetection && !newWord.deletable))
                    2 -> secondLine.add(NewWord(splitWord, !isActionDetection && !newWord.deletable))
                    3 -> thirdLine.add(NewWord(splitWord, !isActionDetection && !newWord.deletable))
                    4 -> {
                        firstLine = secondLine
                        secondLine = thirdLine
                        thirdLine = mutableListOf(NewWord(splitWord, !isActionDetection && !newWord.deletable))
                        breakWord = true
                    }
                }

                var finalText = ""

                if (breakWord) {
                    for (word in firstLine) {
                        finalText = "${finalText}${word.word} "
                    }
                    for (word in secondLine) {
                        finalText = "${finalText}${word.word} "
                    }
                    for (word in thirdLine) {
                        finalText = "${finalText}${word.word} "
                    }
                    text = finalText
                    cameraUiContainerBinding.tvSubtitlesGhost.text = text
                } else {
                    finalText = text
                }
                cameraUiContainerBinding.tvSubtitles.text = finalText
            }
        }
    }

    private fun deleteLastWord() {

        if (this.thirdLine.isNotEmpty()) {
            this.thirdLine.removeLast()
        } else if (this.secondLine.isNotEmpty()) {
            this.secondLine.removeLast()
        } else if (this.firstLine.isNotEmpty()) {
            this.firstLine.removeLast()
        } else {
            return
        }

        if (letterToWord.isNotEmpty()) {
            letterToWord = letterToWord.dropLast(1)
        }

        var finalText = ""
        for (word in firstLine) {
            finalText = "${finalText}${word.word} "
        }
        for (word in secondLine) {
            finalText = "${finalText}${word.word} "
        }
        for (word in thirdLine) {
            finalText = "${finalText}${word.word} "
        }
        text = finalText
        cameraUiContainerBinding.tvSubtitlesGhost.text = text
        cameraUiContainerBinding.tvSubtitles.text = finalText
    }

    private fun deleteAllWords() {
        text = ""
        cameraUiContainerBinding.tvSubtitlesGhost.text = text
        cameraUiContainerBinding.tvSubtitles.text = getString(R.string.text_sample)
        this.firstLine = mutableListOf<NewWord>()
        this.secondLine = mutableListOf<NewWord>()
        this.thirdLine = mutableListOf<NewWord>()
        letterToWord = ""
    }

    private fun deleteOldWords() {
        firstLine.removeAll {
            it.deletable
        }
        secondLine.removeAll {
            it.deletable
        }
        thirdLine.removeAll {
            it.deletable
        }
    }

    private fun makeWordsNotDeletable() {
        firstLine.forEach {
            it.deletable = false
        }
        secondLine.forEach {
            it.deletable = false
        }
        thirdLine.forEach {
            it.deletable = false
        }
    }

    private fun createClassifiers() {
        synchronized(lock) {
            if (handWordsClassifier != null) {
                handWordsClassifier?.close()
                handWordsClassifier = null
            }

            handWordsClassifier = HandActionClassifier.createHandActionClassifier(
                this,
                "dynamic/base_model/base_model_model.tflite",
                "dynamic/base_model/base_model_labels.txt"
            )

            Log.d(TAG, "Words Classifier created.")

            if (handNumbersClassifier != null) {
                handNumbersClassifier?.close()
                handNumbersClassifier = null
            }

            handNumbersClassifier = HandNumberClassifier.createHandNumberClassifier(
                this,
                "static/numbers_model.tflite",
                "static/numbers_labels.txt"
            )

            Log.d(TAG, "Numbers Classifier created.")

            if (handLettersClassifier != null) {
                handLettersClassifier?.close()
                handLettersClassifier = null
            }

            handLettersClassifier = HandLetterClassifier.createHandLetterClassifier(
                this,
                "static/letters_model.tflite",
                "static/letters_labels.txt"
            )

            Log.d(TAG, "Letters Classifier created.")

            if (handClassifier != null) {
                handClassifier?.close()
                handClassifier = null
            }

            handClassifier = handWordsClassifier
        }
    }


    /**
     * Check whether camera permission is already granted.
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_LONG)
                    .show()
                finish()
            }
        }
    }

    private fun changeImageAndColorToButton(imageButton: ImageButton, imageDrawable: Drawable?, color: String?) {
        imageButton.setImageDrawable(imageDrawable)

        color?.let {
            imageButton.setColorFilter(Color.parseColor(color))
        }
    }

    companion object {
        //Mediapipe
        private const val RUN_ON_GPU = true

        //Tensorflow
        private const val TAG = "TFLite-VidClassify"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_FPS_ERROR_RANGE = 0.1 // Acceptable error range in fps.
        private const val MAX_CAPTURE_FPS = 20
        private const val MIN_DETECTION_ACTION = 10
        private const val MIN_DETECTION_GESTURE = 5
        private const val PREDICTION_SECONDS_WINDOW = 10
        private const val SAME_WORD_SECONDS_WINDOW = 5
        private const val NEW_WORD_SECONDS_WINDOW = 2
        private const val DONT_DETECT_SECONDS_WINDOW = 4
        private const val DYNAMIC_SCORE_THRESHOLD = 0.30
        private const val NUMBERS_SCORE_THRESHOLD = 0.45
        private const val LETTERS_SCORE_THRESHOLD = 0.45

        // Constants
        private const val MILLIS_IN_SECONDS = 1000f
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val STOP_WORD = "stop"

    }

    override fun onDestroy() {
        super.onDestroy()
        handWordsClassifier?.close()
        handNumbersClassifier?.close()
        handLettersClassifier?.close()
        executor.shutdown()

        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set Spanish language for TTS
            val result = tts!!.setLanguage(language)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This language is not supported")
            }
        } else {
            Log.e("TTS", "Error initializing TTS")
        }
    }

    fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun changeCastState(state: Boolean) {
        runOnUiThread {
            connectedToPc = state
            if (state) {
                changeImageAndColorToButton(cameraUiContainerBinding.btnCastToPC, getDrawable(R.drawable.ic_baseline_cast_connected_24), null)
            } else {
                changeImageAndColorToButton(cameraUiContainerBinding.btnCastToPC, getDrawable(R.drawable.ic_baseline_cast_24), null)
            }
        }
    }
}
