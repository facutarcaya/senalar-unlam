package com.example.senalar

import android.Manifest
import android.content.Context
import android.content.DialogInterface
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
import com.example.senalar.connection.ClientPC
import com.example.senalar.databinding.ActivityCameraBinding
import com.example.senalar.databinding.CameraUiContainerBinding
import com.example.senalar.handlers.CalculateUtils
import com.example.senalar.handlers.HandClassifier
import com.example.senalar.handlers.VideoClassifier
import com.example.senalar.helpers.PreferencesHelper
import com.example.senalar.helpers.PreferencesHelper.Companion.SOUND_ON_PREF
import com.google.mediapipe.solutions.hands.HandLandmark
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import org.tensorflow.lite.support.label.Category
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@androidx.camera.core.ExperimentalGetImage
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@androidx.camera.core.ExperimentalUseCaseGroup
@androidx.camera.lifecycle.ExperimentalUseCaseGroupLifecycle
class CameraActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val debugMode = true

    private lateinit var binding : ActivityCameraBinding
    private lateinit var cameraUiContainerBinding: CameraUiContainerBinding

    private val lock = Any()
    private lateinit var executor: ExecutorService
    //private var videoClassifier: VideoClassifier? = null
    private var handClassifier: HandClassifier? = null

    private var lastInferenceStartTime: Long = 0
    private var numThread = 4

    // Saves the last result of the analysis
    private var lastResult : String = "Nothing"

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    // Flashlight variables
    private var flashLightOn = false

    // Mute variables
    private var muteOn = true
    private val COLOR_OFF = "#D34A4A"
    private val COLOR_ON = "#30E334"
    private var soundOn = true

    // Subtitles variables
    private var firstLine = mutableListOf<String>()
    private var secondLine = mutableListOf<String>()
    private var thirdLine = mutableListOf<String>()
    private var text : String = ""

    //TTS variables
    private var tts : TextToSpeech? = null
    private var language = Locale("spa", "MEX")

    //Preferences variables
    private lateinit var preferencesHelper: PreferencesHelper

    //Mediapipe variables
    private lateinit var hands: Hands

    //Server variables
    private var clientPC: ClientPC? = null
    private var connectedToPc = false

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityCameraBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //Initialize preferences
        preferencesHelper = PreferencesHelper(this.applicationContext)

        // Initialize the TTS
        tts = TextToSpeech(this, this)

        //Initialize Hand Detection
        initializeHandsDetector()

        // Create Classifier
        createClassifier()

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
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_play_circle_outline_24), COLOR_ON)
                muteOn = false
            } else {
                // We restart the last word, since the user decided to stop the inference
                lastResult = ""

                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_pause_circle_outline_24), COLOR_OFF)
                muteOn = true
            }
        }
    }

    private fun initializeFlashButton() {
        cameraUiContainerBinding.btnSwitchFlash.setOnClickListener {
            val camManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var cameraId: String = ""
            if (flashLightOn) {
                try {
                    getTorchCameraId(camManager)?.let { it1 -> camManager.setTorchMode(it1, false) }
                    changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchFlash, getDrawable(R.drawable.ic_baseline_flash_off_24), null)
                    flashLightOn = false
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            } else {
                try {
                    getTorchCameraId(camManager)?.let { it1 -> camManager.setTorchMode(it1, true) }
                    changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchFlash, getDrawable(R.drawable.ic_baseline_flash_on_24), null)
                    flashLightOn = true
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
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
                val targetFpsMultiplier = MAX_CAPTURE_FPS.div(MODEL_FPS)
                val targetCaptureFps = MODEL_FPS * targetFpsMultiplier
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
            if (diff * MODEL_FPS >= 1000 /* milliseconds */ * (1 - MODEL_FPS_ERROR_RANGE)) {

                lastInferenceStartTime = currentTime

                handClassifier?.let { classifier ->

                    // Run inference using the TFLite model.

                    val results = classifier.classify(handsResult)
                    val inputFps = 1000f / diff

                    showResultsInDebug(results)

                    val newWord = sanitizeNewWord(results[0].label)
                    val newWordScore = results[0].score

                    if (!muteOn && newWord != lastResult && newWordScore >= SCORE_THRESHOLD) {
                        addWordToSubtitle(newWord)
                        speakThroughTTS(newWord)
                        sendToPC(newWord)
                        lastResult = newWord
                    }

                    if (inputFps < MODEL_FPS * (1 - MODEL_FPS_ERROR_RANGE)) {
                        Log.w(
                            TAG, "Current input FPS ($inputFps) is " +
                                    "significantly lower than the TFLite model's " +
                                    "expected FPS ($MODEL_FPS). It's likely because " +
                                    "model inference takes too long on this device."
                        )
                    }
                }
            }
        }
    }

    private fun sanitizeNewWord(word: String): String {
        val splitWord = word.split("_")
        return splitWord[0]
    }

    private fun sendToPC(newWord: String) {
        this.clientPC?.let { it.addWordToQueue("${newWord}|") }
    }

    /**
     * Function for TTS
     *
     */
    private fun speakThroughTTS(newWord: String) {
        if (soundOn) {
            tts!!.speak(newWord, TextToSpeech.QUEUE_ADD, null, "")
        }
    }

    private fun showResultsInDebug(results: List<Category>) {
        if (debugMode) {
            runOnUiThread {
                cameraUiContainerBinding.tvDetectedItem0.text =
                    results[0].label + ": " + String.format("%.2f", results[0].score * 100) + "%"
                cameraUiContainerBinding.tvDetectedItem1.text =
                    results[1].label + ": " + String.format("%.2f", results[1].score * 100) + "%"
                cameraUiContainerBinding.tvDetectedItem2.text =
                    results[2].label + ": " + String.format("%.2f", results[2].score * 100) + "%"
            }
        }
    }

    private fun addWordToSubtitle(newWord: String) {
        runOnUiThread {
            var breakWord = false
            text = "${text}$newWord "
            cameraUiContainerBinding.tvSubtitlesGhost.text = text

            when (cameraUiContainerBinding.tvSubtitlesGhost.lineCount) {
                1 -> firstLine.add(newWord)
                2 -> secondLine.add(newWord)
                3 -> thirdLine.add(newWord)
                4 -> {
                    firstLine = secondLine
                    secondLine = thirdLine
                    thirdLine = mutableListOf(newWord)
                    breakWord = true
                }
            }

            var finalText = ""

            if (breakWord) {
                for (word in firstLine) {
                    finalText = "${finalText}$word "
                }
                for (word in secondLine) {
                    finalText = "${finalText}$word "
                }
                for (word in thirdLine) {
                    finalText = "${finalText}$word "
                }
                text = finalText
                cameraUiContainerBinding.tvSubtitlesGhost.text = text
            } else {
                finalText = text
            }
            cameraUiContainerBinding.tvSubtitles.text = finalText
        }
    }

    /**
     * Initialize the TFLite video classifier.

    private fun createClassifier() {
        synchronized(lock) {
            if (videoClassifier != null) {
                videoClassifier?.close()
                videoClassifier = null
            }
            val options =
                VideoClassifier.VideoClassifierOptions.builder()
                    .setMaxResult(MAX_RESULT)
                    .setNumThreads(numThread)
                    .build()
            val modelFile = MODEL_MOVINET_A1_FILE

            videoClassifier = VideoClassifier.createFromFileAndLabelsAndOptions(
                this,
                modelFile,
                MODEL_LABEL_FILE,
                options
            )

            Log.d(TAG, "Classifier created.")
        }
    }
     */

    private fun createClassifier() {
        synchronized(lock) {
            if (handClassifier != null) {
                handClassifier?.close()
                handClassifier = null
            }

            handClassifier = HandClassifier.createHandClassifier(
                this
            )

            Log.d(TAG, "Classifier created.")
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
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "TFLite-VidClassify"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_FPS = 5 // Ensure the input images are fed to the model at this fps.
        private const val MODEL_FPS_ERROR_RANGE = 0.1 // Acceptable error range in fps.
        private const val MAX_CAPTURE_FPS = 20
        private const val SCORE_THRESHOLD = 0.30 // Min score to assume inference is correct
    }

    override fun onDestroy() {
        super.onDestroy()
        handClassifier?.close()
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
