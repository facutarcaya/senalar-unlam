package com.example.senalar.connection

import android.os.SystemClock
import android.util.Log
import com.example.senalar.CameraActivity
import java.io.*
import java.net.Socket
import java.util.*

@androidx.camera.core.ExperimentalGetImage
class ClientPC : Runnable {

    lateinit var socket: Socket
    lateinit var dataInputStream: DataInputStream
    lateinit var dataOutputStream: DataOutputStream
    lateinit var printWriter: PrintWriter
    var thread: Thread
    var ip: String
    var cameraActivty: CameraActivity
    var queue: Queue<String> = LinkedList<String>()

    constructor(cameraActivity: CameraActivity, ip: String) {
        this.cameraActivty = cameraActivity
        this.ip = ip
        this.thread = Thread(this)
        this.thread.priority = Thread.NORM_PRIORITY
        this.thread.start()
    }

    override fun run() {
        try {
            socket = Socket(ip, CONNECTION_PORT)
            this.cameraActivty.showToast("Conexión establecida")
        } catch (e: Exception) {
            this.cameraActivty.showToast("No se pudo hacer la conexión con el servidor")
            Log.e("CLIENT_ERROR", "Error: ${e.message}")
            return
        }

        try {
            dataInputStream = DataInputStream(BufferedInputStream(this.socket.getInputStream()))
            dataOutputStream = DataOutputStream(BufferedOutputStream(this.socket.getOutputStream()))
            printWriter = PrintWriter(this.socket.getOutputStream(), true)
        } catch (e: Exception) {
            Log.e("CLIENT_ERROR", "Error: ${e.message}")
            return
        }

        this.cameraActivty.changeCastState(true)

        var currentTime = SystemClock.uptimeMillis()
        var lastCheckConnection = currentTime

        while (true) {
            try {
                val diff = lastCheckConnection - currentTime
                if (diff > 5000) {
                    checkConnection()
                    currentTime = SystemClock.uptimeMillis()
                }
                lastCheckConnection = SystemClock.uptimeMillis()
                val wordToSend = queue.poll()
                if (wordToSend != null) {
                    printWriter.write(wordToSend)
                    printWriter.flush()
                }
            } catch (e: Exception) {
                Log.e("CLIENT_ERROR", "Error: ${e.message}")
                disconnect()
                return
            }
        }
    }

    private fun checkConnection() {
        if (!socket.isConnected) {
            throw Exception("Client disconnected")
        }
    }

    fun addWordToQueue(word: String) {
        queue.add(word)
    }

    fun disconnect() {
        this.printWriter.flush()
        this.printWriter.close()
        this.socket.close()
        this.cameraActivty.changeCastState(false)
    }

    companion object {
        private const val CONNECTION_PORT = 12345
    }
}