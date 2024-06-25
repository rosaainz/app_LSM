package com.uam.applsm


import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    private lateinit var btnCapture: Button
    private lateinit var btnEndCapture: Button
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val URL = "http://192.168.0.74:4000/predict"
    private val MEDIA_TYPE_JPEG = "image/jpeg".toMediaType()
    private var capturing = false
    private var captureCount = 0
    private var maxCaptures = 3
    private val handler = Handler(Looper.getMainLooper())
    private val captureInterval = 1000L // Intervalo de captura en milisegundos (3 segundos)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        btnEndCapture = findViewById(R.id.btnEndCapture)
        previewView = findViewById(R.id.previewView)
        textView = findViewById(R.id.textView)

        textView.text = "frase"

        btnCapture.setOnClickListener {
            Toast.makeText(this, "Capturar fotogramas", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Botón 'Iniciar Captura' clickeado")
            //capturePhoto()
            startCapturing()
        }

        btnEndCapture.setOnClickListener{
            Toast.makeText(this,"Finalizar captura", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Botón 'Finalizar Captura' clickeado")
            stopCapturing()
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
        //val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        //StrictMode.setThreadPolicy(policy)
    }

    override fun onStop() {
        super.onStop()
        cameraProvider?.unbindAll()
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Configurar preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Captura de imagen
            imageCapture = ImageCapture.Builder()
                .build()

            try {
                // Desvincular todas las cámaras previas antes de volver a unir
                cameraProvider?.unbindAll()

                // Vincular cámaras al ciclo de vida de la actividad
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            } catch (e: Exception) {
                Log.e(TAG, "Error al configurar la cámara: ${e.message}")
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun startCapturing() {
        captureCount = 0
        capturing = true
        capturePhoto()
    }

    private fun stopCapturing() {
        capturing = false
        handler.removeCallbacksAndMessages(null) // Elimina todas las callbacks pendientes
    }

    private fun capturePhoto() {
        if (!capturing || captureCount >= maxCaptures){
            stopCapturing()
            return
        }

        val imageCapture = imageCapture ?: return

        Log.d("Count", "${captureCount}")

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageToBitmap(image)
                sendImage(bitmap)
                image.close()

                captureCount++
                Log.d("Count", "Count success: ${captureCount}")
                // Programar la próxima captura si aún estamos capturando
                if (capturing && captureCount < maxCaptures) {
                    handler.postDelayed({ capturePhoto() }, captureInterval)
                }
            }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    // Intentar capturar de nuevo si hay un error, pero seguimos capturando
                    if (capturing && captureCount < maxCaptures) {
                        handler.postDelayed({ capturePhoto() }, captureInterval)
                    }
                }
            })
    }
    private fun imageToBitmap(image: ImageProxy): Bitmap { //?
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
    }
    fun generateUniqueFileName(): String {
        val uuid = UUID.randomUUID().toString()
        return "image_$uuid.jpg"
    }

    private fun sendImage(bitmap: Bitmap) {
        val client = OkHttpClient()

        // Convertir bitmap a un array de bytes en formato JPEG
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray()

        // Rquest body de la imagen
        val requestFile = byteArray.toRequestBody(MEDIA_TYPE_JPEG)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", requestFile)
            .build()

        // Solicitud POST para enviar la imagen
        val request = Request.Builder()
            .url(URL)
            .post(multipartBody)
            .header("Content-Type", "image/jpeg")
            .build()

        //Log.d("HTTP_POST", "Tamaño byte array: ${byteArray.size}")

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("HTTP_POST", "Response: $responseBody")

                    } else {
                        Log.e("HTTP_POST", "Error en el servidor: ${response.code}")
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    Log.e("HTTP_POST", "Error al hacer la solicitud al servidor: ${e.message}")
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("HTTP_POST", "Error: ${e.message}")
        }
    }
//    private fun capturePhoto() {
//        val imageCapture = imageCapture ?: return
//
//        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
//            override fun onCaptureSuccess(image: ImageProxy) {
//                val bitmap = imageToBitmap(image)
//               // saveImage(bitmap)
//                sendImage(bitmap)
//                image.close()
//            }
//            override fun onError(exception: ImageCaptureException) {
//                exception.printStackTrace()
//            }
//        })
//    }
    private fun saveImage(bitmap: Bitmap) {
        // Crear carpeta si no existe
        val folder = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CapturedImages")
        if (!folder.exists()) {
            folder.mkdirs()
        }

        // Guardar imagen en la carpeta
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val file = File(folder, fileName)
        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            Log.d("MainActivity", "Imagen guardada: ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("MainActivity", "Error al guardar la imagen: ${e.message}")
        }
    }
}