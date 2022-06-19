package com.example.senalar

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.FragmentTransaction
import com.example.senalar.databinding.ActivityHomeBinding
import com.example.senalar.handlers.HeaderDialogFragment

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
            showCreateHeaderDialog();


        }

        binding.btnHowDoesItWork.setOnClickListener {
            // Aca se debería abrir un (full screen dialog? o modal?) explicando como funciona (es más un holder para otro botón)
            // https://m3.material.io/components/dialogs/overview
        }

        binding.cameraButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    private fun showCreateHeaderDialog(){
        val fragmentManager = supportFragmentManager
        val newFragment = HeaderDialogFragment()
            // The device is smaller, so show the fragment fullscreen
            val transaction = fragmentManager.beginTransaction()
            // For a little polish, specify a transition animation
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            // To make it fullscreen, use the 'content' root view as the container
            // for the fragment, which is always the root view for the activity
            transaction
                .add(android.R.id.content, newFragment)
                .addToBackStack(null)
                .commit()

    }
}