package com.example.senalar

import android.Manifest
import android.content.Context
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
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.senalar.databinding.ActivityCameraBinding
import com.example.senalar.databinding.CameraUiContainerBinding
import com.example.senalar.handlers.CalculateUtils
import com.example.senalar.handlers.VideoClassifier
import kotlinx.coroutines.*
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
    private var videoClassifier: VideoClassifier? = null

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

    // Subtitles variables
    private var firstLine = mutableListOf<String>()
    private var secondLine = mutableListOf<String>()
    private var thirdLine = mutableListOf<String>()
    private var text : String = ""

    //TTS variables
    private var tts : TextToSpeech? = null
    private var language = Locale.US

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityCameraBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Initialize the TTS
        tts = TextToSpeech(this, this)

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
        cameraUiContainerBinding.btnSwitchMute.setOnClickListener {
            if (muteOn) {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_play_circle_outline_24), COLOR_ON)
                muteOn = false
            } else {
                changeImageAndColorToButton(cameraUiContainerBinding.btnSwitchMute, getDrawable(R.drawable.ic_baseline_pause_circle_outline_24), COLOR_OFF)
                muteOn = true
            }
        }

        cameraUiContainerBinding.btnSwitchCamera.setOnClickListener {
            changeCamera()
        }

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
        // Ensure that only one frame is processed at any given moment.
        synchronized(lock) {
            val currentTime = SystemClock.uptimeMillis()
            val diff = currentTime - lastInferenceStartTime

            // Check to ensure that we only run inference at a frequency required by the
            // model, within an acceptable error range (e.g. 10%). Discard the frames
            // that comes too early.
            if (diff * MODEL_FPS >= 1000 /* milliseconds */ * (1 - MODEL_FPS_ERROR_RANGE)) {
                lastInferenceStartTime = currentTime

                val image = imageProxy.image
                image?.let {
                    videoClassifier?.let { classifier ->
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

                        // Run inference using the TFLite model.
                        val startTimeForReference = SystemClock.uptimeMillis()
                        val results = classifier.classify(rotatedBitmap)
                        val endTimeForReference =
                            SystemClock.uptimeMillis() - startTimeForReference
                        val inputFps = 1000f / diff

                        showResultsInDebug(results)

                        if (!muteOn && results[0].label != lastResult && results[0].score >= SCORE_THRESHOLD) {
                            val newWord = results[0].label
                            addWordToSubtitle(newWord)
                            speakThroughTTS(newWord)
                            lastResult = results[0].label
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
            imageProxy.close()
        }
    }

    /**
     * Function for TTS
     *
     */
    private fun speakThroughTTS(newWord: String) {
        tts!!.speak(newWord, TextToSpeech.QUEUE_ADD, null, "")
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
     */
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
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "TFLite-VidClassify"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MAX_RESULT = 3
        //private const val MODEL_MOVINET_A1_FILE = "movinet_a1_stream_int8.tflite"
        //private const val MODEL_LABEL_FILE = "kinetics600_label_map.txt"
        private const val MODEL_MOVINET_A1_FILE = "tf_model_lsa.tflite"
        private const val MODEL_LABEL_FILE = "class_ind.txt"
        private const val MODEL_FPS = 5 // Ensure the input images are fed to the model at this fps.
        private const val MODEL_FPS_ERROR_RANGE = 0.1 // Acceptable error range in fps.
        private const val MAX_CAPTURE_FPS = 20
        private const val SCORE_THRESHOLD = 0.50 // Min score to assume inference is correct
    }

    override fun onDestroy() {
        super.onDestroy()
        videoClassifier?.close()
        executor.shutdown()

        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set Spanish language for TTS
            // TODO make this text
            val result = tts!!.setLanguage(language)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This language is not supported")
            }
        } else {
            Log.e("TTS", "Error initializing TTS")
        }
    }
}
