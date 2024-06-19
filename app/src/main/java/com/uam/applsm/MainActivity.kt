package com.uam.applsm

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.Button
import android.widget.Toast

class MainActivity : ComponentActivity() {
    //Elementos de la interfaz de usuario
    private lateinit var btnCapture: Button
    private lateinit var btnEndCapture: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        btnEndCapture = findViewById(R.id.btnEndCapture)

        btnCapture.setOnClickListener {
            Toast.makeText(this, "Capturar fotogramas", Toast.LENGTH_SHORT).show()
        }

        btnEndCapture.setOnClickListener {
            Toast.makeText(this, "Finalizar captura", Toast.LENGTH_SHORT).show()
        }
    }
}