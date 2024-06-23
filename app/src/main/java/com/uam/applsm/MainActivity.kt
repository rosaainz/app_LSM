package com.uam.applsm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import android.os.StrictMode
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


class MainActivity : ComponentActivity() {
    private lateinit var btnCapture: Button
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private val URL = "http://192.168.0.74:4000"
    private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        previewView = findViewById(R.id.previewView)
        textView = findViewById(R.id.textView)

        textView.text = "frase"

        btnCapture.setOnClickListener {
            Toast.makeText(this, "Capturar fotogramas", Toast.LENGTH_SHORT).show()
        }

        // Permisos para la cámara
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 50)
        } else {
            startCamera()
        }

        // Permitir operaciones de red en el hilo principal
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // datos que se van a enviar
        val myData = """{"data":"eeeeee"}"""
        sendPostRequest(myData)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(this, cameraSelector, preview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendPostRequest(data: String) {
        val client = OkHttpClient()

        // Crear cuerpo de la solicitud
        val body = data.toRequestBody(JSON)

        // Crear solicitud POST
        val request = Request.Builder()
            .url(URL)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        // Enviar solicitud
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                Log.d("HTTP_POST", "Response: ${response.body?.string()}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("HTTP_POST", "Error: ${e.message}")
        }
    }
}