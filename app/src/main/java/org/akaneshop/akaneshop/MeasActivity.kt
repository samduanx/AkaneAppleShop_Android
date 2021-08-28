package org.akaneshop.akaneshop

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import kotlinx.android.synthetic.main.activity_meas.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.pmml4s.data.*
import org.pmml4s.model.Model
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.roundToInt

class MeasActivity : AppCompatActivity() {
    private lateinit var lightOnButton: Button
    private lateinit var cameraButton: Button
    private lateinit var lightOffButton: Button
    private lateinit var sensorButton: Button
    private lateinit var calcButton: Button
    private lateinit var isRedSwitch: Switch
    private lateinit var measStatus: TextView
    private lateinit var redModel: Model
    private lateinit var yellowModel: Model

    private var isRed: Boolean = false
    private var btDevice: BleDevice? = null
    private var sensorData = arrayOf<String>()
    private val CAM_ACT_REQ_CODE = 0
    private var brightness = 0.00
    private var sensorDataGot: Boolean = false
    private var cameraDataGot: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meas)

        lightOnButton = findViewById(R.id.lighton_button)
        lightOffButton = findViewById(R.id.lightoff_button)
        cameraButton = findViewById(R.id.camera_button)
        sensorButton = findViewById(R.id.sensor_button)
        calcButton = findViewById(R.id.calc_button)
        isRedSwitch = findViewById(R.id.is_red_switch_meas)
        measStatus = findViewById(R.id.meas_status)

        sensorButton.isEnabled = false
        isRedSwitch.isChecked = true

        try {
            var inputStreamRed: InputStream = assets.open("red_model.pmml")
            var inputStreamYellow: InputStream = assets.open("yellow_model.pmml")

            redModel = Model.fromInputStream(inputStreamRed)
            yellowModel = Model.fromInputStream(inputStreamYellow)

        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Request permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Bluetooth Init
        BleManager.getInstance().init(getApplication())
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setOperateTimeout(5000)
        BleManager.getInstance().enableBluetooth();
        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setDeviceName(true, "HC-42") // 只扫描指定广播名的设备，可选
            .setAutoConnect(true) // 连接时的autoConnect参数，可选，默认false
            .setScanTimeOut(10000) // 扫描超时时间，可选，默认10秒
            .build()
        BleManager.getInstance().initScanRule(scanRuleConfig)

        BleManager.getInstance().scanAndConnect(object : BleScanAndConnectCallback() {
            override fun onScanStarted(success: Boolean) {
                measStatus.text = "Scan started..."
            }

            override fun onScanning(bleDevice: BleDevice?) {
                measStatus.text = "Scanning..."
                if (bleDevice != null) {
                    measStatus.text = "Device found with MAC " + bleDevice.mac
                }
            }

            override fun onScanFinished(scanResult: BleDevice) {
                if (scanResult != null) {
                    measStatus.text = "Scan finished with MAC " + scanResult.name
                    btDevice = scanResult
                } else {
                    measStatus.text = "Scan failed!"
                }
            }

            override fun onStartConnect() {
                measStatus.text = "Connecting..."
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                measStatus.text = "Connection failed " + exception.code.toString()
            }

            override fun onConnectSuccess(
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                measStatus.text = "Connected with status " + status.toString()
                sensorButton.isEnabled = true

                BleManager.getInstance()
                    .setMtu(bleDevice, 512, object : BleMtuChangedCallback() {
                        override fun onSetMTUFailure(exception: BleException) {
                            measStatus.text = "Set MTU failed!"
                        }

                        override fun onMtuChanged(mtu: Int) {
                            measStatus.text = "MTU set to 512"
                        }
                    })
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice?,
                gatt: BluetoothGatt?,
                status: Int
            ) {
                if (device != null) {
                    measStatus.text = "Disconnected from " + device.name
                }
            }
        })

        isRedSwitch.setOnCheckedChangeListener { _, isChecked ->
            isRed = isChecked
        }

        sensorButton.setOnClickListener {
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
                        getSensorData()
                    }

                    override fun onWriteFailure(exception: BleException) {
                        measStatus.text = "Send failed!"// 发送数据到设备失败
                    }
                })
        }

        // Camera Control
        lightOnButton.setOnClickListener {
            BleManager.getInstance().write(
                btDevice,
                "0000ffe0-0000-1000-8000-00805f9b34fb",
                "0000ffe1-0000-1000-8000-00805f9b34fb",
                "LO".toByteArray(),
                object : BleWriteCallback() {
                    override fun onWriteSuccess(
                        current: Int,
                        total: Int,
                        justWrite: ByteArray
                    ) {
                        measStatus.text =
                            "Send success!"// 发送数据到设备成功（分包发送的情况下，可以通过方法中返回的参数可以查看发送进度）
                        sensorButton.isEnabled = false
                    }

                    override fun onWriteFailure(exception: BleException) {
                        measStatus.text = "Send failed!"// 发送数据到设备失败
                    }
                })
        }

        cameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivityForResult(intent, CAM_ACT_REQ_CODE)
        }

        lightOffButton.setOnClickListener {
            BleManager.getInstance().write(
                btDevice,
                "0000ffe0-0000-1000-8000-00805f9b34fb",
                "0000ffe1-0000-1000-8000-00805f9b34fb",
                "LC".toByteArray(),
                object : BleWriteCallback() {
                    override fun onWriteSuccess(
                        current: Int,
                        total: Int,
                        justWrite: ByteArray
                    ) {
                        measStatus.text =
                            "Send success!"
                        sensorButton.isEnabled = true
                    }

                    override fun onWriteFailure(exception: BleException?) {
                        measStatus.text =
                            "Send failed!"
                    }
                }
            )
        }

        calcButton.setOnClickListener {
            predictBrix()
        }
    }

    private val REQUEST_IMAGE_CAPTURE = 1

    private fun Double.roundToDecimals(decimals: Int): Double {
        var dotAt = 1
        repeat(decimals) { dotAt *= 10 }
        val roundedValue = (this * dotAt).roundToInt()
        return (roundedValue / dotAt) + (roundedValue % dotAt).toDouble() / dotAt
    }

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
                    R.string.no_permission,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAM_ACT_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // Read image from mediaDir
                val camPicUri = data!!.getStringExtra("STR_URI").split("file://")
                val path = camPicUri[1]
                val im: Mat = Imgcodecs.imread(path)
                // Crop the image
                val rectCrop = Rect(1200, 2250, 960, 1590)
                val cropped = im.submat(rectCrop)
                // Greyscale
                Imgproc.cvtColor(cropped, cropped, Imgproc.COLOR_BGR2GRAY)
                // Gaussian Blur
                val gaussianIm = Mat(cropped.rows(), cropped.cols(), cropped.type())
                Imgproc.GaussianBlur(cropped, gaussianIm, Size(3.0, 3.0), 0.0)
                // Binarisation
                val im_th = Mat(gaussianIm.cols(), gaussianIm.rows(), gaussianIm.type())
                Imgproc.threshold(gaussianIm, im_th, 10.0, 255.0, Imgproc.THRESH_OTSU)
                // Get contours
                val contours: List<MatOfPoint> = ArrayList()
                val hierarchy = Mat()
                Imgproc.findContours(
                    gaussianIm,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )
                // Draw contours
                val mask = Mat()
                im.copyTo(mask)
                Imgproc.drawContours(
                    mask, contours, -1,
                    Scalar(255.0, 255.0, 255.0), 1
                )

                // Calculate average light Strength
                // Fill the contours and binarise it
                val finder = Mat()
                mask.copyTo(finder)
                Imgproc.drawContours(
                    finder, contours, -1,
                    Scalar(255.0, 255.0, 255.0), Imgproc.FILLED
                )
                Imgproc.cvtColor(finder, finder, Imgproc.COLOR_BGR2GRAY)
                Imgproc.threshold(finder, finder, 200.0, 255.0, Imgproc.THRESH_BINARY)

                // Actual calculation
                var sum = 0.00
                var counter = 0
                for (i in 0..1589) {
                    for (j in 0..959) {
                        if (finder[i, j][0] == 255.0) {
                            sum += cropped[i, j][0]
                            counter += 1
                        }
                    }
                }

                brightness = sum / counter * 500.0
                measStatus.text = "Camera Data: " + brightness.toString()

            }
        }
    }

    fun getSensorData() {
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

                override fun onCharacteristicChanged(data: ByteArray?) {
                    if (data != null) {
                        sensorData = data.decodeToString().split(",").toTypedArray()
                        measStatus.text = "Sensor: " + data.decodeToString()
                    } else {
                        measStatus.text = "Data receiving failed!"
                    }
                }
            }
        )

        val handler = Handler()
        handler.postDelayed({ // Do something after 5s = 5000ms
            BleManager.getInstance().stopNotify(
                btDevice,
                "0000ffe0-0000-1000-8000-00805f9b34fb",
                "0000ffe1-0000-1000-8000-00805f9b34fb",
            )
        }, 5000)
    }

    fun predictBrix() {
       if (isRed) {
                val result: Map<*, *> =
                    redModel.predict(object : HashMap<String?, Any?>() {
                        init {
                            put("x1", (sensorData[0].toDouble()) / 100)
                            put("x2", (sensorData[1].toDouble()) / 100)
                            put("x3", (sensorData[2].toDouble()) / 100 * 6.0)
                            put("x4", (sensorData[3].toDouble()) / 100 * 10.0)
                            put("x5", (sensorData[4].toDouble()) / 100 * 21.0)
                            put("x6", (sensorData[5].toDouble()) / 100 * 6.0)
                            put("x7", (brightness.toDouble()) / 100)
                        }
                    })
                var resultList = result.values.toMutableList()
                var resultNum = ((resultList[0].toString().toDouble()) / 10)
                    .roundToDecimals(1)
                measStatus.text = resultNum.toString() + "% Brix"
            } else {
           val result: Map<*, *> =
               yellowModel.predict(object : HashMap<String?, Any?>() {
                   init {
                       put("x1", (sensorData[0].toDouble()) / 100)
                       put("x2", (sensorData[1].toDouble()) / 100)
                       put("x3", (sensorData[2].toDouble()) / 100 * 6.0)
                       put("x4", (sensorData[3].toDouble()) / 100 * 10.0)
                       put("x5", (sensorData[4].toDouble()) / 100 * 21.0)
                       put("x6", (sensorData[5].toDouble()) / 100 * 6.0)
                       put("x7", (brightness.toDouble()) / 500)
                   }
               })
           var resultList = result.values.toMutableList()
           var resultNum = ((resultList[0].toString().toDouble()) / 10)
               .roundToDecimals(1)
           measStatus.text = resultNum.toString() + "% Brix"
            }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
