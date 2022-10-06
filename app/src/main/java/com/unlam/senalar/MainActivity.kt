package com.unlam.senalar

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.unlam.senalar.databinding.ActivityMainBinding

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // We hide the logo and app title to animate it later
        binding.ivLogoSenalar.alpha = ALPHA_MINIMUM
        binding.tVAppTitle.alpha = ALPHA_MINIMUM

        binding.ivLogoSenalar.animate().setDuration(DURATION).alpha(ALPHA_MAXIMUM)
        binding.tVAppTitle.animate().setDuration(DURATION).alpha(ALPHA_MAXIMUM).withEndAction {
            startHomeActivity()
        }
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        this.finish()
    }

    companion object {
        const val DURATION = 2000L

        const val ALPHA_MINIMUM = 0f
        const val ALPHA_MAXIMUM = 1f
    }
}