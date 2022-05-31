package com.example.senalar

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Range
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.senalar.databinding.ActivityCameraBinding

@androidx.camera.core.ExperimentalGetImage
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
@androidx.camera.core.ExperimentalUseCaseGroup
@androidx.camera.lifecycle.ExperimentalUseCaseGroupLifecycle
class CameraActivity : AppCompatActivity() {

    private lateinit var binding : ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityCameraBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Start the camera.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    /**
     * Start the image capturing pipeline.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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

                /*
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImage(imageProxy)
                }
                */

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
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "TFLite-VidClassify"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MAX_RESULT = 3
        private const val MODEL_MOVINET_A1_FILE = "movinet_a1_stream_int8.tflite"
        private const val MODEL_LABEL_FILE = "kinetics600_label_map.txt"
        private const val MODEL_FPS = 5 // Ensure the input images are fed to the model at this fps.
        private const val MODEL_FPS_ERROR_RANGE = 0.1 // Acceptable error range in fps.
        private const val MAX_CAPTURE_FPS = 20
    }
}
