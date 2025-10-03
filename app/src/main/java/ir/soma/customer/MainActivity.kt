package ir.soma.customer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var balanceText: TextView
    private lateinit var statusText: TextView
    private lateinit var amountEditText: EditText
    private lateinit var payButton: Button
    private lateinit var connectButton: Button
    private lateinit var trxText: TextView
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private var customerBalance = 100000 // موجودی اولیه
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupBluetooth()
    }
    
    private fun setupViews() {
        balanceText = findViewById(R.id.balanceText)
        statusText = findViewById(R.id.statusText)
        amountEditText = findViewById(R.id.amountEditText)
        payButton = findViewById(R.id.payButton)
        connectButton = findViewById(R.id.connectButton)
        trxText = findViewById(R.id.trxText)
        
        updateBalanceDisplay()
        
        connectButton.setOnClickListener { connectToMerchant() }
        payButton.setOnClickListener { processPayment() }
    }
    
    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            statusText.text = "بلوتوث پشتیبانی نمی‌شود"
            payButton.isEnabled = false
            connectButton.isEnabled = false
        }
    }
    
    private fun connectToMerchant() {
        try {
            statusText.text = "در حال جستجو برای فروشنده..."
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
            
            if (pairedDevices.isEmpty()) {
                statusText.text = "دستگاه جفت شده پیدا نشد"
                return
            }
            
            var merchantDevice: BluetoothDevice? = null
            for (device in pairedDevices) {
                if (device.name.contains("فروشنده") || 
                    device.name.contains("Merchant") || 
                    device.name.contains("soma")) {
                    merchantDevice = device
                    break
                }
            }
            
            if (merchantDevice != null) {
                connectToDevice(merchantDevice)
            } else {
                statusText.text = "دستگاه فروشنده پیدا نشد"
                showDevicesList(pairedDevices)
            }
            
        } catch (e: Exception) {
            statusText.text = "خطا در اتصال: ${e.message}"
        }
    }
    
    private fun showDevicesList(devices: Set<BluetoothDevice>) {
        val deviceNames = devices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("انتخاب دستگاه فروشنده")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices.elementAt(which)
                connectToDevice(selectedDevice)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            statusText.text = "در حال اتصال به ${device.name}..."
            
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket!!.connect()
            
            inputStream = socket!!.inputStream
            outputStream = socket!!.outputStream
            
            statusText.text = "✅ اتصال امن برقرار شد"
            statusText.setTextColor(Color.GREEN)
            payButton.isEnabled = true
            connectButton.isEnabled = false
            
            // شروع گوش دادن به پیام‌های دریافتی
            startListening()
            
        } catch (e: Exception) {
            statusText.text = "❌ خطا در اتصال به دستگاه"
            statusText.setTextColor(Color.RED)
        }
    }
    
    private fun startListening() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            while (true) {
                try {
                    bytes = inputStream!!.read(buffer)
                    val message = String(buffer, 0, bytes)
                    
                    runOnUiThread {
                        when {
                            message.startsWith("RECEIPT:") -> {
                                // دریافت رسید از فروشنده
                                val receiptData = message.removePrefix("RECEIPT:")
                                trxText.text = "📋 $receiptData"
                            }
                            message == "PAYMENT_CONFIRMED" -> {
                                statusText.text = "✅ پرداخت تأیید شد"
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }
    
    private fun processPayment() {
        val amountText = amountEditText.text.toString()
        if (amountText.isEmpty()) {
            Toast.makeText(this, "لطفا مبلغ را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        
        val amount = amountText.toInt()
        if (amount > customerBalance) {
            Toast.makeText(this, "موجودی کافی نیست", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (amount <= 0) {
            Toast.makeText(this, "مبلغ باید بیشتر از صفر باشد", Toast.LENGTH_SHORT).show()
            return
        }
        
        val trxCode = "TRX${System.currentTimeMillis()}"
        val message = "PAY:$amount:$trxCode"
        
        try {
            outputStream!!.write(message.toByteArray())
            
            // کم کردن از موجودی
            customerBalance -= amount
            updateBalanceDisplay()
            
            statusText.text = "⏳ در حال پرداخت $amount تومان..."
            trxText.text = "کد تراکنش: $trxCode"
            
            // غیرفعال کردن دکمه تا تأیید دریافت شود
            payButton.isEnabled = false
            
            // بعد از 2 ثانیه دکمه را فعال کن
            payButton.postDelayed({
                payButton.isEnabled = true
                statusText.text = "✅ پرداخت موفق - کد: $trxCode"
            }, 2000)
            
        } catch (e: Exception) {
            statusText.text = "❌ خطا در پرداخت"
            statusText.setTextColor(Color.RED)
            payButton.isEnabled = true
        }
    }
    
    private fun updateBalanceDisplay() {
        balanceText.text = "موجودی: $customerBalance تومان"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
