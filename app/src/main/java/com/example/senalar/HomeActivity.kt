package com.example.senalar

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.senalar.databinding.ActivityHomeBinding

@androidx.camera.core.ExperimentalGetImage
class HomeActivity : AppCompatActivity() {
    private lateinit var binding : ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityHomeBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initializeButtons()
    }

    private fun initializeButtons() {
        binding.btnHowToUse.setOnClickListener {
            // Aca se debería abrir un (full screen dialog? o modal?) explicando como se usa
            // https://m3.material.io/components/dialogs/overview
        }

        binding.btnHowDoesItWork.setOnClickListener {
            // Aca se debería abrir un (full screen dialog? o modal?) explicando como funciona (es más un holder para otro botón)
            // https://m3.material.io/components/dialogs/overview
        }

        binding.cameraButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }
}