package org.akaneshop.akaneshop

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs.imread
import org.opencv.imgproc.Imgproc.*
import org.pmml4s.model.Model
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.roundToInt


class DebugActivity : AppCompatActivity() {
    private lateinit var bleStatus: TextView
    private lateinit var bleButton: Button

    private lateinit var cameraButton: Button
    private lateinit var cameraCheck: CheckBox
    private lateinit var thumbnailView: ImageView
    private lateinit var lightOnButton: Button
    private lateinit var lightOffButton: Button

    private lateinit var tfInputNum: EditText
    private lateinit var tfOutputNum: TextView
    private lateinit var tfTestButton: Button
    private lateinit var tfRedSwitch: Switch
    private lateinit var tfRedString: TextView
    private var isRed: Boolean = false
    private lateinit var redModel: Model
    private lateinit var yellowModel: Model

    private lateinit var bleSendButton: Button
    private lateinit var cameraStatus: TextView
    private val CAM_ACT_REQ_CODE = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        var btDevice: BleDevice? = null

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        bleStatus = findViewById(R.id.ble_status)
        bleButton = findViewById(R.id.ble_scan_button)

        lightOnButton = findViewById(R.id.lighton_button)
        lightOffButton = findViewById(R.id.lightoff_button)
        cameraButton = findViewById(R.id.camera_button)
        cameraCheck = findViewById(R.id.camera_checkbox)
        cameraStatus = findViewById(R.id.camera_status)
        thumbnailView = findViewById(R.id.thumbnail_view)

        tfInputNum = findViewById(R.id.tftest_input)
        tfOutputNum = findViewById(R.id.tftest_output)
        tfTestButton = findViewById(R.id.tftest_button)
        tfRedSwitch = findViewById(R.id.is_red_switch)
        tfRedString = findViewById(R.id.is_red_string)

        bleSendButton = findViewById(R.id.ble_send_button)

        bleSendButton.isEnabled = false
        cameraCheck.isEnabled = false
        cameraCheck.isChecked = false
        tfRedSwitch.isChecked = true

        try {
            var inputStreamRed: InputStream = assets.open("red_model.pmml")
            var inputStreamYellow: InputStream = assets.open("yellow_model.pmml")

            redModel = Model.fromInputStream(inputStreamRed)
            yellowModel = Model.fromInputStream(inputStreamYellow)

        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Request camera permissions
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

        bleButton.setOnClickListener {
            BleManager.getInstance().scanAndConnect(object : BleScanAndConnectCallback() {
                override fun onScanStarted(success: Boolean) {
                    bleStatus.text = "Scan started..."
                }

                override fun onScanning(bleDevice: BleDevice?) {
                    bleStatus.text = "Scanning..."
                    if (bleDevice != null) {
                        bleStatus.text = "Device found with MAC " + bleDevice.mac
                    }
                }

                override fun onScanFinished(scanResult: BleDevice) {
                    if (scanResult != null) {
                        bleStatus.text = "Scan finished with MAC " + scanResult.name
                        btDevice = scanResult
                    } else {
                        bleStatus.text = "Scan failed!"
                    }
                }

                override fun onStartConnect() {
                    bleStatus.text = "Connecting..."
                }

                override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                    bleStatus.text = "Connection failed " + exception.code.toString()
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice,
                    gatt: BluetoothGatt,
                    status: Int
                ) {
                    bleStatus.text = "Connected with status " + status.toString()
                    bleSendButton.isEnabled = true

                    BleManager.getInstance()
                        .setMtu(bleDevice, 512, object : BleMtuChangedCallback() {
                            override fun onSetMTUFailure(exception: BleException) {
                                bleStatus.text = "Set MTU failed!"
                            }

                            override fun onMtuChanged(mtu: Int) {}
                        })
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice,
                    gatt: BluetoothGatt,
                    status: Int
                ) {
                    bleStatus.text = "Disconnected from " + device.name
                    bleSendButton.isEnabled = false
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
                        bleStatus.text =
                            "Send success!"// 发送数据到设备成功（分包发送的情况下，可以通过方法中返回的参数可以查看发送进度）
                        bleSendButton.isEnabled = false
                    }

                    override fun onWriteFailure(exception: BleException) {
                        bleStatus.text = "Send failed!"// 发送数据到设备失败
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
                        bleStatus.text =
                            "Send success!"
                        bleSendButton.isEnabled = true
                    }

                    override fun onWriteFailure(exception: BleException?) {
                        bleStatus.text =
                            "Send failed!"
                    }
                }
            )
        }

        // Model testing
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAM_ACT_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // Read image from mediaDir
                val camPicUri = data!!.getStringExtra("STR_URI").split("file://")
                cameraStatus.text = camPicUri[1]
                cameraCheck.isChecked = true
                val path = camPicUri[1]
                val im: Mat = imread(path)
                cameraStatus.text = "Height: " + im.height() + " Width: " + im.width()
                // Crop the image
                val rectCrop = Rect(1200, 2250, 960, 1590)
                val cropped = im.submat(rectCrop)
                // Greyscale
                cvtColor(cropped, cropped, COLOR_BGR2GRAY)
                // Gaussian Blur
                val gaussianIm = Mat(cropped.rows(), cropped.cols(), cropped.type())
                GaussianBlur(cropped, gaussianIm, Size(3.0, 3.0), 0.0)
                // Binarisation
                val im_th = Mat(gaussianIm.cols(), gaussianIm.rows(), gaussianIm.type())
                threshold(gaussianIm, im_th, 10.0, 255.0, THRESH_OTSU)
                // Get contours
                val contours: List<MatOfPoint> = ArrayList()
                val hierarchy = Mat()
                findContours(gaussianIm, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)
                // Draw contours
                val mask = Mat()
                im.copyTo(mask)
                drawContours(
                    mask, contours, -1,
                    Scalar(255.0, 255.0, 255.0), 1
                )

                // Calculate average light Strength
                // Fill the contours and binarise it
                val finder = Mat()
                mask.copyTo(finder)
                drawContours(
                    finder, contours, -1,
                    Scalar(255.0, 255.0, 255.0), FILLED
                )
                cvtColor(finder, finder, COLOR_BGR2GRAY)
                threshold(finder, finder, 200.0, 255.0, THRESH_BINARY)

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

                val brightness = sum / counter * 500.0

                val greyBitmap = Bitmap.createBitmap(
                    gaussianIm.cols(),
                    gaussianIm.rows(),
                    Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(gaussianIm, greyBitmap)
                thumbnailView.setImageBitmap(
                    createScaledBitmap(
                        greyBitmap,
                        180,
                        320,
                        false
                    )
                )

                cameraStatus.text = brightness.toString()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        BleManager.getInstance().disconnectAllDevice()
        BleManager.getInstance().destroy()
    }

    fun Double.roundToDecimals(decimals: Int): Double {
        var dotAt = 1
        repeat(decimals) { dotAt *= 10 }
        val roundedValue = (this * dotAt).roundToInt()
        return (roundedValue / dotAt) + (roundedValue % dotAt).toDouble() / dotAt
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}