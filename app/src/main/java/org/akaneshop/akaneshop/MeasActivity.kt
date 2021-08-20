package org.akaneshop.akaneshop

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_meas.*

class MeasActivity : AppCompatActivity() {
    private lateinit var readyButton: Button
    private lateinit var cameraCheck: CheckBox
//    private lateinit var thumbnailView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meas)

        readyButton = findViewById(R.id.ready_button)
        cameraCheck = findViewById(R.id.camera_checkbox)
        isRedSwitch = findViewById(R.id.is_red_switch_meas)
        measStatus = findViewById(R.id.meas_status)

        cameraCheck.isEnabled = false
        readyButton.isEnabled = false
        isRedSwitch.isChecked = true

        try {
            var inputStreamRed: InputStream = assets.open("red_model.pmml")
            var inputStreamYellow: InputStream = assets.open("yellow_model.pmml")

            redModel = Model.fromInputStream(inputStreamRed)
            yellowModel = Model.fromInputStream(inputStreamYellow)

        // Request camera permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        readyButton.setOnClickListener {
            dispatchTakePictureIntent()
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
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
//            val imageBitmap = data?.extras?.get("data") as Bitmap
//            thumbnailView.setImageBitmap(imageBitmap)
            BleManager.getInstance().write(
                btDevice,
                "0000ffe0-0000-1000-8000-00805f9b34fb",
                "0000ffe1-0000-1000-8000-00805f9b34fb",
                "MEAS".toByteArray(),
                object : BleWriteCallback() {
                    override fun onWriteSuccess(
                        current: Int,
                        total: Int,
                        justWrite: ByteArray
                    ) {
                        measStatus.text = "Send success!"// 发送数据到设备成功
                        predictBrix()
                    }

                    override fun onWriteFailure(exception: BleException) {
                        measStatus.text = "Send failed!"// 发送数据到设备失败
                    }
                })
        }
    }

    fun predictBrix() {
        BleManager.getInstance().notify(
            btDevice,
            "0000ffe0-0000-1000-8000-00805f9b34fb",
            "0000ffe1-0000-1000-8000-00805f9b34fb",
            object : BleNotifyCallback() {
                override fun onNotifySuccess() {
                    measStatus.text = "Notify started!"// 打开通知操作成功
                }

                override fun onNotifyFailure(exception: BleException) {
                    measStatus.text = "Notify failed!"// 打开通知操作失败
                }

                override fun onCharacteristicChanged(dataOrig: ByteArray) {
                    val data = dataOrig.decodeToString().split(",").toTypedArray()
                    if (!isRed) {
                        val result: Map<*, *> =
                            yellowModel.predict(object : HashMap<String?, Any?>() {
                                init {
                                    put("x1", (data[0].toDouble()))
                                    put("x2", (data[1].toDouble()))
                                    put("x3", (data[2].toDouble()))
                                    put("x4", (data[3].toDouble()))
                                    put("x5", (data[4].toDouble()))
                                    put("x6", (data[5].toDouble()))
                                }
                            })
                        var resultList = result.values.toMutableList()
                        var resultNum = resultList[0].toString().toDouble().roundToDecimals(1)
                        measStatus.text = resultNum.toString() + "% Brix"
                    } else {
                        val result: Map<*, *> =
                            redModel.predict(object : HashMap<String?, Any?>() {
                                init {
                                    put("x1", (data[0].toDouble()))
                                    put("x2", (data[1].toDouble()))
                                    put("x3", (data[2].toDouble()))
                                    put("x4", (data[3].toDouble()))
                                    put("x5", (data[4].toDouble()))
                                    put("x6", (data[5].toDouble()))


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }



}