package org.akaneshop.akaneshop

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var measButton: Button
    private lateinit var debugButton: Button
    private lateinit var aboutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        measButton = findViewById(R.id.meas_button)
        debugButton = findViewById(R.id.debug_button)
        aboutButton = findViewById(R.id.about_button)

        measButton.setOnClickListener {
            val measIntent = Intent(this, MeasActivity::class.java)
            startActivity(measIntent)
        }

        debugButton.setOnClickListener {
            val debugIntent = Intent(this, DebugActivity::class.java)
            startActivity(debugIntent)
        }

        aboutButton.setOnClickListener {
            val aboutIntent = Intent(this, AboutActivity::class.java)
            startActivity(aboutIntent)
        }
    }
}