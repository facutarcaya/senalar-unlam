package com.example.senalar

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.senalar.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.net.UnknownHostException
import java.util.*

@androidx.camera.core.ExperimentalGetImage
class HomeActivity : AppCompatActivity() {
    private lateinit var binding : ActivityHomeBinding

    // Server variables BORRAR
    private lateinit var client: Socket
    private lateinit var printwriter: PrintWriter
    private lateinit var inferenceQueue: Queue<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityHomeBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initializeCommToPC()

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

    private fun initializeCommToPC() {
        inferenceQueue = ArrayDeque<String>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client = Socket("172.23.128.1", 12345) //connect to server
                printwriter = PrintWriter(client.getOutputStream(), true)

                while (true) {
                    inferenceQueue.poll()?.let { newWord ->
                        printwriter.write(newWord) //write the message to output stream
                        printwriter.flush()
                    }
                }
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}