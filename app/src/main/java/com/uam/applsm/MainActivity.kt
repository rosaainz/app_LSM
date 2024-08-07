package com.uam.applsm


import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.os.Build
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
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock


class MainActivity : ComponentActivity() {
    private lateinit var btnCapture: Button
    private lateinit var btnEndCapture: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    //private val URL = "http://192.168.0.74:4000/"
    private val URL = "http://192.168.1.210:4000/"
    private val MEDIA_TYPE_JPEG = "image/jpeg".toMediaType()
    private var capturing = false
    private var captureCount = 0
    private var maxCaptures = 3 //indica el valor de capturas que se tomaran
    private var successResponseCount = 0 // Contador de respuestas exitosas
    private val handler = Handler(Looper.getMainLooper())
    private val captureInterval = 500L // milisegundos
    private val responses: MutableList<Map<String, Any>> = mutableListOf()
    private var successCount = 0
    private var fraseParts = 3 //partes de la frase
    private var countFrase = 0
    private var ruta = ""
    private var endpointTemp = "processTemporalidad"
    private var endpointLocalizacion = "processLocalizacion"
    private var endpointIntensidad = "processIntensidad"
    private val results = mutableListOf<String>()




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        btnEndCapture = findViewById(R.id.btnEndCapture)
        previewView = findViewById(R.id.previewView)
        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)

        textView.text = ""

        btnCapture.setOnClickListener {
            Toast.makeText(this, "Capturar fotogramas", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Botón 'Iniciar Captura' clickeado")
            responses.clear()
            successResponseCount = 0  // Resetear contador de éxitos
            updateProgressBar()
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
    }
    override fun onStart() {
        super.onStart()
        startCamera()
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
        runOnUiThread{
            //textView.text = result
            Log.d("countFrase", "countFrase antes del if: ${countFrase}")
            if (successResponseCount == maxCaptures) {
                countFrase++
                Log.d("countFrase", "countFrase actualizada: ${countFrase}")
                Log.d("Responses", "responses list: ${responses}")
                val result = responses
                results.add(result.toString())
                // Imprimir la lista de respuestas
                Log.d("Results", "Lista Total: ${results.joinToString("\n")}")
                updateProgressBar()
            }
        }
    }

    private fun capturePhoto() {
        if (!capturing || successResponseCount >= maxCaptures){
            stopCapturing()
            return
        }

        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageToBitmap(image)
                sendImage(bitmap)
                image.close()

                captureCount++
                Log.d("Count", "CAPTURE COUNT SUCCESS: ${captureCount}")
                // Programar la próxima captura si aún estamos capturando
                if (capturing && successResponseCount < maxCaptures) {
                    handler.postDelayed({ capturePhoto() }, captureInterval)
                }
            }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    // Intentar capturar de nuevo si hay un error, pero seguimos capturando
                    if (capturing && successResponseCount < maxCaptures) {
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
            .addFormDataPart("image", "image_$captureCount.jpg", requestFile)
            .build()

        // Solicitud POST para enviar la imagen
        when (countFrase) {
            0 -> {
                ruta = URL + endpointTemp
            }
            1 -> {
                ruta = URL + endpointLocalizacion
            }
            2 -> {
                ruta = URL + endpointIntensidad
            }
        }

        val request = Request.Builder()
            .url(ruta)
            .post(multipartBody)
            .header("Content-Type", "image/jpeg")
            .build()

        //Log.d("HTTP_POST", "Tamaño byte array: ${byteArray.size}")

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    Log.d("Response:", "response status: ${response.isSuccessful}")
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            Log.d("HTTP_POST", "Response received: $responseBody")

                            // Parsear la respuesta como un objeto JSON
                            val jsonObjectResponse = JSONObject(responseBody!!)

                            // Extraer los datos del JSON
                            val bodyLanguageClass = jsonObjectResponse.getString("body_language_class")
                            val bodyLanguageProbArray = jsonObjectResponse.getJSONArray("body_language_prob")
                            val image = jsonObjectResponse.getString("image")
                            val model = jsonObjectResponse.getString("model")

                            // Crear un mapa para almacenar los datos
                            val responseData = mapOf(
                                "body_language_class" to bodyLanguageClass,
                                "body_language_prob" to bodyLanguageProbArray,
                                "image" to image,
                                "model" to model
                            )

                            // Sincronizar el acceso a responses
                            synchronized(responses) {
                                responses.add(responseData)
                            }


                            successResponseCount++
                            Log.d("RESPONSE", "RESPONSES SIZE: ${responses.size}, SuccessResponseCount: $successResponseCount")
                            if (successResponseCount  == maxCaptures) {
                                stopCapturing()
                            }else{}
                            //if (responses.size == maxCaptures) {
                            //    stopCapturing()
                            //} else {
                           // }
                        } catch (e: JSONException) {
                            Log.e("HTTP_POST", "Error al parsear JSON como JSONArray: ${e.message}")
                        }

                    } else {
                        Log.e("HTTP_POST", "Error en el servidor: ${response.code}")
                        // Si la respuesta no es exitosa, intentar capturar de nuevo
                        if (capturing && successResponseCount < maxCaptures) {
                            handler.postDelayed({ capturePhoto() }, captureInterval)
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    Log.e("HTTP_POST", "Error al hacer la solicitud al servidor: ${e.message}")
                    // Si falla la solicitud, intentar capturar de nuevo
                    if (capturing && successResponseCount < maxCaptures) {
                        handler.postDelayed({ capturePhoto() }, captureInterval)
                    }
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("HTTP_POST", "Error: ${e.message}")
            // Si hay un error en la solicitud, intentar capturar de nuevo
            if (capturing && successResponseCount < maxCaptures) {
                handler.postDelayed({ capturePhoto() }, captureInterval)
            }
        }
    }
    private fun updateProgressBar() {
        runOnUiThread {
            // Actualiza la barra de progreso basado en el número de respuestas exitosas
            val progress = (countFrase.toFloat() / fraseParts.toFloat() * 100).toInt()
            Log.d("updateProgressBar", "progress : ${progress}")
            progressBar.progress = progress
        }
    }

    private fun processResponses(responses: List<JSONObject>): String {
        Log.d(
            "RESPONSES LIST",
            "responses list in processResponses: ${responses}"
        )
        val classCount = mutableMapOf<String, Int>()
        val classProbs = mutableMapOf<String, Double>()

        // body_language_class más frecuentes
        for (response in responses) {
            val bodyLanguageClass = response.getString("body_language_class")
            val bodyLanguageProbArray = response.getJSONArray("body_language_prob")
            val bodyLanguageProb = bodyLanguageProbArray.getDouble(0)

            classCount[bodyLanguageClass] = (classCount[bodyLanguageClass] ?: 0) + 1
            classProbs[bodyLanguageClass] =
                maxOf(classProbs[bodyLanguageClass] ?: 0.0, bodyLanguageProb)
        }
        val maxClasses = classCount
            .filter { it.value == classCount.values.maxOrNull() }
            .keys.toList()

        val sortedMaxClasses = maxClasses.sortedByDescending { classProbs[it] }
        return sortedMaxClasses.firstOrNull() ?: "Unknown"
    }
}
