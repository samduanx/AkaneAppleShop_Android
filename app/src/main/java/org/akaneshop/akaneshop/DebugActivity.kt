package org.akaneshop.akaneshop

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_debug.*
import kotlinx.android.synthetic.main.activity_meas.*
import kotlinx.android.synthetic.main.activity_meas.camera_checkbox


class DebugActivity : AppCompatActivity() {
    private lateinit var readyButton: Button
    private lateinit var cameraCheck: CheckBox
    private lateinit var thumbnailView: ImageView
    private lateinit var tfInputNum: EditText
    private lateinit var tfOutputNum: TextView
    private lateinit var tfTestButton: Button
    private lateinit var tfRedSwitch: Switch
    private lateinit var tfRedString: TextView
    private var isRed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        readyButton = findViewById(R.id.ready_button)
        cameraCheck = findViewById(R.id.camera_checkbox)
        thumbnailView = findViewById(R.id.thumbnail_view)
        tfInputNum = findViewById(R.id.tftest_input)
        tfOutputNum = findViewById(R.id.tftest_output)
        tfTestButton = findViewById(R.id.tftest_button)
        tfRedSwitch = findViewById(R.id.is_red_switch)
        tfRedString = findViewById(R.id.is_red_string)

        cameraCheck.isEnabled = false
        cameraCheck.isChecked = false

        // Request camera permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        readyButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        tfRedSwitch.setOnCheckedChangeListener { _, isChecked ->
            isRed = isChecked
            tfRedString.text = when (isRed) {
                true -> "Yes"
                false -> "No"
            }
        }

        tfTestButton.setOnClickListener {

        }

    }

    val REQUEST_IMAGE_CAPTURE = 1

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            // display error state to the user
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            thumbnailView.setImageBitmap(imageBitmap)
            camera_checkbox.isChecked = true
        }
        else
        {
            camera_checkbox.isChecked = false
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}