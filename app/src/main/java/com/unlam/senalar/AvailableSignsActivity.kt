package com.unlam.senalar

import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.unlam.senalar.databinding.ActivityAvailableSignsBinding

class AvailableSignsActivity : AppCompatActivity() {
    private lateinit var binding : ActivityAvailableSignsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityAvailableSignsBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.imageViewer.setImageDrawable(getDrawable(R.drawable.numbers_image))

        binding.btnClose.setOnClickListener {
            this.finish()
        }

        binding.btnNumbersOption.setOnClickListener {
            binding.imageViewer.setImageDrawable(getDrawable(R.drawable.numbers_image))
            binding.imageViewer.zoomTo(1f, false)
            binding.btnNumbersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.contrast_light_blue))
            binding.btnNumbersOption.setTextColor(getResources().getColor(R.color.main_light_blue))

            binding.btnLettersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnLettersOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
            binding.btnWordsOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnWordsOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
        }

        binding.btnLettersOption.setOnClickListener {
            binding.imageViewer.setImageDrawable(getDrawable(R.drawable.letters_image))
            binding.imageViewer.zoomTo(1f, false)
            binding.btnLettersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.contrast_light_blue))
            binding.btnLettersOption.setTextColor(getResources().getColor(R.color.main_light_blue))

            binding.btnNumbersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnNumbersOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
            binding.btnWordsOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnWordsOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
        }

        binding.btnWordsOption.setOnClickListener {
            binding.imageViewer.setImageDrawable(getDrawable(R.drawable.signs))
            binding.imageViewer.zoomTo(1f, false)
            binding.btnWordsOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.contrast_light_blue))
            binding.btnWordsOption.setTextColor(getResources().getColor(R.color.main_light_blue))

            binding.btnLettersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnLettersOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
            binding.btnNumbersOption.backgroundTintList = ColorStateList.valueOf(getResources().getColor(R.color.main_light_blue))
            binding.btnNumbersOption.setTextColor(getResources().getColor(R.color.contrast_light_blue))
        }
    }
}