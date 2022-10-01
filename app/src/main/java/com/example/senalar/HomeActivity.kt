package com.example.senalar

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.senalar.databinding.ActivityHomeBinding
import com.example.senalar.helpers.LanguageHelper
import com.example.senalar.helpers.PreferencesHelper
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@androidx.camera.core.ExperimentalGetImage
class HomeActivity : AppCompatActivity() {
    private lateinit var binding : ActivityHomeBinding
    lateinit var mAdView : AdView
    private lateinit var auth: FirebaseAuth

    //Preferences variables
    private lateinit var preferencesHelper: PreferencesHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityHomeBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Initialize preferences
        preferencesHelper = PreferencesHelper(this.applicationContext)

        auth = Firebase.auth

        initializeButtons()
        MobileAds.initialize(this) {}

        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

    }

    override fun onResume() {
        super.onResume()

        if (auth.currentUser != null) {
            binding.btnUserLogin.setImageDrawable(getDrawable(R.drawable.ic_baseline_person_24))
        } else {
            binding.btnUserLogin.setImageDrawable(getDrawable(R.drawable.ic_baseline_person_outline_24))
        }
    }

    private fun initializeButtons() {
        updateLanguageSelected()

        if (auth.currentUser != null) {
            binding.btnUserLogin.setImageDrawable(getDrawable(R.drawable.ic_baseline_person_24))
        } else {
            binding.btnUserLogin.setImageDrawable(getDrawable(R.drawable.ic_baseline_person_outline_24))
        }

        binding.btnUserLogin.setOnClickListener {
            startActivity(Intent(this, StartLoginActivity::class.java))
        }

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

        binding.btnChooseLanguage.setOnClickListener {
            val items = arrayOf(LanguageHelper.SPANISH_NAME, LanguageHelper.ENGLISH_NAME, LanguageHelper.PORTUGUESE_NAME)

            var selected = 0
            var selectedLanguageTranslation = LanguageHelper.SPANISH_LANGUAGE
            var selectedLanguageCountry = LanguageHelper.SPANISH_COUNTRY

            when (preferencesHelper.getStringPreference(PreferencesHelper.LANGUAGE_TRANSLATION)) {
                LanguageHelper.ENGLISH_LANGUAGE -> {
                    selected = 1
                }
                LanguageHelper.PORTUGUESE_LANGUAGE -> {
                    selected = 2
                }
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.choose_language))
                .setSingleChoiceItems(items, selected) { dialog, which ->
                    when (items[which]) {
                        LanguageHelper.SPANISH_NAME -> {
                            selectedLanguageTranslation = LanguageHelper.SPANISH_LANGUAGE
                            selectedLanguageCountry = LanguageHelper.SPANISH_COUNTRY
                        }
                        LanguageHelper.ENGLISH_NAME -> {
                            selectedLanguageTranslation = LanguageHelper.ENGLISH_LANGUAGE
                            selectedLanguageCountry = LanguageHelper.ENGLISH_COUNTRY
                        }
                        LanguageHelper.PORTUGUESE_NAME -> {
                            selectedLanguageTranslation = LanguageHelper.PORTUGUESE_LANGUAGE
                            selectedLanguageCountry = LanguageHelper.PORTUGUESE_COUNTRY
                        }
                    }
                }
                .setNegativeButton(resources.getString(R.string.cancel)) { dialog, which ->

                }
                .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->
                    preferencesHelper.setStringPreference(PreferencesHelper.LANGUAGE_TRANSLATION, selectedLanguageTranslation)
                    preferencesHelper.setStringPreference(PreferencesHelper.COUNTRY_TRANSLATION, selectedLanguageCountry)
                    updateLanguageSelected()
                }.show()
        }
    }

    private fun updateLanguageSelected() {
        val languageName = when (preferencesHelper.getStringPreference(PreferencesHelper.LANGUAGE_TRANSLATION)) {
            LanguageHelper.ENGLISH_LANGUAGE -> {
                LanguageHelper.ENGLISH_NAME
            }
            LanguageHelper.PORTUGUESE_LANGUAGE -> {
                LanguageHelper.PORTUGUESE_NAME
            }
            else -> {LanguageHelper.SPANISH_NAME}
        }
        binding.btnChooseLanguage.text = "${resources.getString(R.string.choose_language)}: ${languageName}"

    }
}