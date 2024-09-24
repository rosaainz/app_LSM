package com.uam.applsm


// Importaciones necesarias para la aplicación
import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import java.io.IOException
import android.util.Log
import android.widget.ProgressBar
import androidx.camera.core.Preview
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Clase principal de la actividad
class MainActivity : ComponentActivity() {
    // Definición de los elementos de la interfaz de usuario
    private lateinit var btnCapture: Button
    private lateinit var btnEndCapture: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView

    // Selección de la cámara frontal
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    // URL del servidor API
    private val URL = "https://lsm-server.up.railway.app:4000/"
    private val MEDIA_TYPE_JPEG = "image/jpeg".toMediaType() // Tipo de medio para imágenes JPEG

    // Variables para manejar el estado de la captura
    private var capturing = false // Indica si se está capturando imágenes
    private var captureCount = 0 // Contador de imágenes capturadas
    private var maxCaptures = 3 // Número máximo de capturas a realizar
    private var successResponseCount = 0 // Contador de respuestas exitosas del servidor (status 200)
    private val handler = Handler(Looper.getMainLooper()) // Handler para tareas en el hilo principal
    private val captureInterval = 900L // Intervalo de tiempo entre capturas en milisegundos

    // Lista para almacenar las respuestas del servidor
    private val responses: MutableList<Map<String, Any>> = mutableListOf()

    // Configuración para la generación de frases
    private var fraseParts = 3 // Número de partes en la frase generada
    private var countFrase = 0 // Contador de partes de la frase
    private var ruta = "" // Ruta temporal para procesamiento
    private var endpointTemp = "processTemporalidad" // Endpoint para procesamiento de temporalidad
    private var endpointLocalizacion = "processLocalizacion" // Endpoint para procesamiento de localización
    private var endpointIntensidad = "processIntensidad" // Endpoint para procesamiento de intensidad
    private val results = mutableListOf<String>() // Lista para almacenar resultados de las respuestas

    // Plantillas para cada categoría
    private val templates = mapOf(
        // Plantillas relacionadas con la temporalidad de los síntomas
        "temporalidad" to mapOf(
            "anio" to listOf("Este año he tenido", "A lo largo del año tuve"),
            "ayer" to listOf("Ayer comencé a tener", "Desde ayer he tenido"),
            "hoy" to listOf("Hoy tuve", "Hoy he notado que tengo"),
            "dia" to listOf("Durante el día he tenido", "Todo el día he estado con"),
            "mes" to listOf("Este mes he tenido", "Durante el mes he notado que tengo"),
            "aveces" to listOf("A veces he tenido", "A veces noto que tengo"),
            "semana" to listOf("Esta semana he tenido", "Durante la semana he tenido"),
            "ayer en la noche" to listOf("Ayer en la noche empecé con", "Desde ayer en la noche he notado que tengo"),
            "ahora" to listOf("Ahora mismo tengo", "En este momento noto que tengo"),
            "aun" to listOf("Aún tengo", "Aún experimento"),
            "anio pasado" to listOf("El año pasado tuve", "Durante el año pasado noté que tengo"),
            "antier" to listOf("Antier empecé con", "Desde antier he notado que tengo")
        ),
        // Plantillas relacionadas con la localización/síntomas de los malestares
        "localizacion" to mapOf(
            "dolor de cabeza" to listOf("dolor de cabeza", "presión en la cabeza"),
            "dolor en el pecho" to listOf("dolor en el pecho", "presión en el pecho"),
            "gripe" to listOf("síntomas de gripe", "gripe"),
            "estomago" to listOf("dolor de estómago", "malestar estomacal"),
            "garganta" to listOf("dolor de garganta", "irritación en la garganta"),
            "apendice" to listOf("dolor en el apéndice", "molestia en el apéndice"),
            "alergia" to listOf("síntomas de alergia", "reacción alérgica"),
            "flemas" to listOf("acumulación de flemas", "flemas en la garganta"),
            "vomito" to listOf("vomito", "sensación de vómito"),
            "diarrea" to listOf("diarrea", "episodios de diarrea"),
            "mareo" to listOf("mareo", "sensación de mareo"),
            "tos" to listOf("tos", "episodios de tos")
        ),
        // Plantillas relacionadas con la intensidad de los síntomas
        "intensidad" to mapOf(
            "mas o menos" to listOf("de forma más o menos intensa", "más o menos constante"),
            "constante" to listOf("de manera constante", "constantemente"),
            "si" to listOf("es bastante intenso", "definitivamente es intenso"),
            "no" to listOf("no es muy intenso", "no ha sido intenso"),
            "grave" to listOf("de manera grave", "es bastante grave"),
            "debil" to listOf("de manera débil", "es bastante débil")
        )
    )

    // Método que se llama al crear la actividad (interfaz de usuario e interacción)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Establece el layout de la actividad

        // Inicializa los componentes de la interfaz
        btnCapture = findViewById(R.id.btnCapture)
        btnEndCapture = findViewById(R.id.btnEndCapture)
        previewView = findViewById(R.id.previewView)
        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)

        textView.text = "" // Limpia el TextView al iniciar

        // Configura el botón de captura
        btnCapture.setOnClickListener {
            Toast.makeText(this, "Capturar fotogramas", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Botón 'Iniciar Captura' clickeado")
            responses.clear() // Limpia respuestas previas
            successResponseCount = 0  // Reinicia contador de respuestas exitosas
            updateProgressBar(results) // Actualiza la barra de progreso
            startCapturing() // Inicia la captura de imágenes
        }

        // Configura el botón para finalizar la captura
        btnEndCapture.setOnClickListener{
            Toast.makeText(this,"Finalizar captura", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Botón 'Finalizar Captura' clickeado")
            stopCapturing() // Detiene la captura de imágenes
        }

        // Verifica permisos de cámara
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 50) // Solicita permiso si no se tiene
        } else {
            startCamera() // Inicia la cámara si se tiene permiso
        }
    }

    // Método llamado al iniciar la actividad para iniciar los recursos de la cámara
    override fun onStart() {
        super.onStart()
        startCamera() // Inicia la cámara al comenzar
    }

    // Método llamado al detener la actividad para detener la cámara
    override fun onStop() {
        super.onStop()
        cameraProvider?.unbindAll() // Libera los recursos de la cámara
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    // Método para iniciar la cámara
    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get() // Obtiene el proveedor de cámara

            // Configura la vista previa
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider) // Asigna la vista de superficie para la vista previa
                }

            // Configura la captura de imágenes
            imageCapture = ImageCapture.Builder()
                .build()

            try {
                cameraProvider?.unbindAll() // Desvincula cualquier cámara previa
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)  // Vincula la cámara al ciclo de vida de la actividad
            } catch (e: Exception) {
                Log.e(TAG, "Error al configurar la cámara: ${e.message}")  // Maneja errores de configuración
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this)) // Ejecuta en el hilo principal
    }

    // Método para iniciar la captura de imágenes
    private fun startCapturing() {
        captureCount = 0 // Reinicia el contador de capturas
        capturing = true // Marca que estamos en proceso de captura
        capturePhoto() // Llama a la función para capturar una foto
    }

    // Método para detener la captura de imágenes
    private fun stopCapturing() {
        capturing = false // Marca que hemos detenido la captura
        handler.removeCallbacksAndMessages(null) // Elimina cualquier callback pendiente
        runOnUiThread{
            //textView.text = result
            Log.d("countFrase", "countFrase antes del if: ${countFrase}")
            if (successResponseCount == maxCaptures) { // Si se alcanzaron las capturas máximas
                countFrase++ // Incrementa el contador de frases
                Log.d("countFrase", "countFrase actualizada: ${countFrase}")
                Log.d("Responses", "responses list: ${responses}")
                val result = responses // Almacena las respuestas
                results.add(result.toString()) // Agrega el resultado a la lista de resultados
                Log.d("Results", "Lista Total: ${results.joinToString("\n")}") // Imprime la lista de resultados
                updateProgressBar(results) // Actualiza la barra de progreso
            }
        }
    }

    // Método para capturar una foto
    private fun capturePhoto() {
        if (!capturing || successResponseCount >= maxCaptures){ // Verifica si se debe detener la captura
            stopCapturing()
            return
        }

        val imageCapture = imageCapture ?: return // Verifica si la configuración de captura es válida

        // Captura una imagen y maneja la respuesta
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageToBitmap(image) // Convierte la imagen a bitmap
                sendImage(bitmap) // Envía la imagen para procesamiento
                image.close() // Cierra la imagen

                captureCount++ // Incrementa el contador de capturas
                Log.d("Count", "CAPTURE COUNT SUCCESS: ${captureCount}")
                // Programa la próxima captura si aún se está capturando
                if (capturing && successResponseCount < maxCaptures) {
                    handler.postDelayed({ capturePhoto() }, captureInterval)
                }
            }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()  // Maneja errores de captura
                    // Intenta capturar de nuevo si hay un error
                    if (capturing && successResponseCount < maxCaptures) {
                        handler.postDelayed({ capturePhoto() }, captureInterval)
                    }
                }
            })
    }
    // Método para convertir ImageProxy a Bitmap
    private fun imageToBitmap(image: ImageProxy): Bitmap { //?
        val buffer = image.planes[0].buffer // Obtiene el buffer de la imagen
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes) // Lee los bytes del buffer
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null) // Decodifica los bytes a Bitmap
    }

    // Método para enviar la imagen al servidor
    private fun sendImage(bitmap: Bitmap) {
        val client = OkHttpClient() // Crea un cliente HTTP

        // Convierte el bitmap a un array de bytes en formato JPEG
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray() // Obtiene el byte array

        // Crea el cuerpo de la solicitud para la imagen
        val requestFile = byteArray.toRequestBody(MEDIA_TYPE_JPEG)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image_$captureCount.jpg", requestFile)
            .build()

        // Define la URL dependiendo de la categoría
        when (countFrase) {
            0 -> {
                ruta = URL + endpointTemp // URL para temporalidad
            }
            1 -> {
                ruta = URL + endpointLocalizacion // URL para localización
            }
            2 -> {
                ruta = URL + endpointIntensidad // URL para intensidad
            }
        }

        // Crea la solicitud POST para enviar la imagen
        val request = Request.Builder()
            .url(ruta)
            .post(multipartBody)
            .header("Content-Type", "image/jpeg")
            .build()

        try {
            client.newCall(request).enqueue(object : Callback { // Envia la solicitud asíncronamente
                override fun onResponse(call: Call, response: Response) {
                    Log.d("Response:", "response status: ${response.isSuccessful}")
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() // Obtiene la respuesta del servidor
                            Log.d("HTTP_POST", "Response received: $responseBody")

                            // Parsea la respuesta JSON
                            val jsonObjectResponse = JSONObject(responseBody!!)

                            // Extrae los datos de la respuesta
                            val bodyLanguageClass = jsonObjectResponse.getString("body_language_class")
                            val bodyLanguageProbArray = jsonObjectResponse.getJSONArray("body_language_prob")
                            val image = jsonObjectResponse.getString("image")
                            val model = jsonObjectResponse.getString("model")

                            // Crea un mapa para almacenar los datos
                            val responseData = mapOf(
                                "body_language_class" to bodyLanguageClass,
                                "body_language_prob" to bodyLanguageProbArray,
                                "image" to image,
                                "model" to model
                            )

                            synchronized(responses) {
                                responses.add(responseData) // Agrega la respuesta al mapa de respuestas
                            }

                            successResponseCount++ // Incrementa el contador de respuestas exitosas
                            Log.d("RESPONSE", "RESPONSES SIZE: ${responses.size}, SuccessResponseCount: $successResponseCount")
                            if (successResponseCount  == maxCaptures) {
                                stopCapturing() // Detiene la captura si se alcanzó el número máximo de capturas
                            }
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
                    Log.e("HTTP_POST", "Error al hacer la solicitud al servidor: ${e.message}")
                    // Si falla la solicitud, intenta capturar de nuevo
                    if (capturing && successResponseCount < maxCaptures) {
                        handler.postDelayed({ capturePhoto() }, captureInterval)
                    }
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("HTTP_POST", "Error: ${e.message}")
            // Si hay un error en la solicitud, intenta capturar de nuevo
            if (capturing && successResponseCount < maxCaptures) {
                handler.postDelayed({ capturePhoto() }, captureInterval)
            }
        }
    }

    // Método que genera una frase basada en los datos recibidos.
    private fun generatePhrases(data: List<Map<String, Any>>): String {
        val phraseParts = mutableListOf<String>() //  Lista mutable para almacenar partes de frases.

        for (item in data) {
            val bodyClass = item["body_language_class"] as String
            val model = (item["model"] as String).split('_')[0] //Obtiene la categoría del modelo, dividiendo la cadena.

            if (model in templates && bodyClass in templates[model]!!) { // Verifica si el modelo y la clase están en las plantillas definidas.
                val templateList = templates[model]!![bodyClass]!!
                phraseParts.add(templateList.random()) // Agrega una frase aleatoria de la lista de plantillas a la lista de partes de frase.
            }
        }
        return phraseParts.joinToString(" ").trim() // Une todas las partes en una sola frase y la devuelve.
    }

    // Método que actualiza la barra de progreso de la interfaz de usuario.
    private fun updateProgressBar(results: MutableList<String>) {
        runOnUiThread { // Asegura que la actualización de la UI se realice en el hilo principal.
            val progress = (countFrase.toFloat() / fraseParts.toFloat() * 100).toInt() // Calcula el porcentaje de progreso.

            if (progress == 100) { // Si se ha completado el proceso, se procesan los resultados.
                val finalResults = processResponses(results)  // Procesar las respuestas y obtener los resultados finales

                Log.d("FINAL", "Resultados Finales: ${finalResults}")  // Mostrar en un TextView
                Log.d("FRASE", "frase generada: ${generatePhrases(finalResults)}")
                textView.text = generatePhrases(finalResults) // Muestra la frase generada en el TextView

                results.clear()  // Limpiar los resultados
                countFrase = 0 // Reiniciar el contador
            }
            Log.d("updateProgressBar", "progress : ${progress}")
            progressBar.progress = progress // Actualiza la barra de progreso
        }
    }

    // Método que procesa las respuestas recibidas para regresar la clase con mayor probabilidad y frecuencia de cada modelo
    private fun processResponses(results: MutableList<String>): List<Map<String, Any>> {
        // Crea mapas para almacenar clases y probabilidades.
        val temporalidadModels = mutableMapOf<String, Pair<String, Double>>()
        val localizacionModels = mutableMapOf<String, Pair<String, Double>>()
        val intensidadModels = mutableMapOf<String, Pair<String, Double>>()

        for (responseJsonString in results) { // Iterar sobre cada respuesta en la lista de resultados
            try {
                val responseArray = JSONArray(responseJsonString) // Parsear la respuesta como un arreglo de JSONObjects

                for (i in 0 until responseArray.length()) { // Iterar sobre cada JSONObject en el arreglo
                    val jsonObjectResponse = responseArray.getJSONObject(i)
                    // Extraer los datos del JSONObject
                    val bodyLanguageClass = jsonObjectResponse.getString("body_language_class")
                    val bodyLanguageProbArray = jsonObjectResponse.getJSONArray("body_language_prob")
                    val model = jsonObjectResponse.getString("model")

                    when {  // Verificar el tipo de modelo y actualizar el mapa correspondiente
                        model.startsWith("temporalidad_") -> {
                            if (temporalidadModels.containsKey(bodyLanguageClass)) {
                                val currentProb = temporalidadModels[bodyLanguageClass]!!.second
                                if (bodyLanguageProbArray.getDouble(0) > currentProb) {
                                    temporalidadModels[bodyLanguageClass] =
                                        Pair(model, bodyLanguageProbArray.getDouble(0))
                                }
                            } else {
                                temporalidadModels[bodyLanguageClass] =
                                    Pair(model, bodyLanguageProbArray.getDouble(0))
                            }
                        }

                        model.startsWith("localizacion_") -> {
                            if (localizacionModels.containsKey(bodyLanguageClass)) {
                                val currentProb = localizacionModels[bodyLanguageClass]!!.second
                                if (bodyLanguageProbArray.getDouble(0) > currentProb) {
                                    localizacionModels[bodyLanguageClass] =
                                        Pair(model, bodyLanguageProbArray.getDouble(0))
                                }
                            } else {
                                localizacionModels[bodyLanguageClass] =
                                    Pair(model, bodyLanguageProbArray.getDouble(0))
                            }
                        }

                        model.startsWith("intensidad_") -> {
                            if (intensidadModels.containsKey(bodyLanguageClass)) {
                                val currentProb = intensidadModels[bodyLanguageClass]!!.second
                                if (bodyLanguageProbArray.getDouble(0) > currentProb) {
                                    intensidadModels[bodyLanguageClass] =
                                        Pair(model, bodyLanguageProbArray.getDouble(0))
                                }
                            } else {
                                intensidadModels[bodyLanguageClass] =
                                    Pair(model, bodyLanguageProbArray.getDouble(0))
                            }
                        }
                    }
                }
            } catch (e: JSONException) {
                Log.e("processResponses", "Error al parsear JSON: ${e.message}")
            }
        }

        // Encontrar la clase más frecuente con la probabilidad más alta para cada tipo de modelo
        val result = mutableListOf<Map<String, Any>>()
        // Función para encontrar la clase más frecuente con la probabilidad más alta para cada tipo de modelo
        fun findMax(map: MutableMap<String, Pair<String, Double>>): Map<String, Any> {
            var maxClass = ""
            var maxModel = ""
            var maxProb = Double.MIN_VALUE
            for ((key, value) in map) {
                if (value.second > maxProb) {
                    maxProb = value.second
                    maxClass = key
                    maxModel = value.first
                }
            }
            return mapOf(
                "body_language_class" to maxClass,
                "model" to maxModel,
                "body_language_prob" to listOf(maxProb)
            )
        }

        result.add(findMax(temporalidadModels))
        result.add(findMax(localizacionModels))
        result.add(findMax(intensidadModels))

        return result // Devuelve los resultados procesados
    }
}
