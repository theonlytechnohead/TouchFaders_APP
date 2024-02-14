package net.ddns.anderserver.touchfadersapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ddns.anderserver.touchfadersapp.databinding.StartupBinding
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext

class StartupActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: StartupBinding
    private lateinit var adapter: DeviceSelectRecyclerViewAdapter
    private var deviceNames: MutableList<String> = mutableListOf()
    private val devices: HashMap<String, InetAddress> = hashMapOf()

    var sharedPreferences: SharedPreferences? = null

    private var listenUDP = true

    lateinit var connectionService: ConnectionService
    var bound = false

    private var broadcastReceiver: BroadcastReceiver? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StartupBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val decor = window.decorView
        decor.setOnSystemUiVisibilityChangeListener { hideUI() }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences?.run {
//            this.edit().putString(resources.getString(R.string.setting_fader_width), "52.5").apply();
        }

        /*
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end,
                                       android.text.Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart)
                            + source.subSequence(start, end)
                            + destTxt.substring(dend);
                    if (!resultingTxt
                            .matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
                        return "";
                    } else {
                        String[] splits = resultingTxt.split("\\.");
                        for (int i = 0; i < splits.length; i++) {
                            if (Integer.valueOf(splits[i]) > 255) {
                                return "";
                            }
                        }
                    }
                }
                return null;
            }

        };
         */
        binding.helpButton.setOnClickListener {
            val intent = Intent(it.context, HelpActivity::class.java)
            intent.apply {
                `package` = applicationContext.packageName
            }
            intent.putExtra(EXTRA_DEMO_MODE, true)
            startActivity(intent)
        }

        binding.demoButton.setOnClickListener {
            val intent = Intent(it.context, MainActivity::class.java)
            intent.apply {
                `package` = applicationContext.packageName
            }
            intent.putExtra(EXTRA_DEMO_MODE, true)
            startActivity(intent)
        }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(it.context, SettingsActivity::class.java)
            intent.apply {
                `package` = applicationContext.packageName
            }
            startActivity(intent)
        }

        binding.ipEditText.setText(sharedPreferences?.getString("ipAddress", "192.168.1.2"))
        binding.ipEditText.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val currentView = this.currentFocus
                if (currentView != null) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(currentView.windowToken, 0)
                }
                val handler = Handler(Looper.getMainLooper())
                handler.post { binding.startButton.performClick() }
                return@setOnEditorActionListener true
            }
            false
        }
        binding.ipEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                sharedPreferences?.edit()?.putString(IP_ADDRESS_PREFERENCES, s.toString())?.apply()
            }
        })

        adapter = DeviceSelectRecyclerViewAdapter(applicationContext, binding.deviceRecyclerView, deviceNames)
        adapter.setClickListener(clickListener)
        binding.deviceRecyclerView.adapter = adapter

        // Start connection service
        val serviceIntent = Intent(applicationContext, ConnectionService::class.java)
        serviceIntent.apply {
            `package` = applicationContext.packageName
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }

        // create broadcast receiver to start activity
        broadcastReceiver = Receiver()
    }

    private fun hideUI() {
        // Making it fullscreen...
        val actionBar = supportActionBar
        actionBar?.hide()
        binding.startupLayout.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        // Fullscreen done!
    }

    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == START_MIX_ACTIVITY) {
                val mixIntent = Intent(applicationContext, MixSelectActivity::class.java)
                mixIntent.apply {
                    `package` = context?.packageName
                }
                startActivity(mixIntent)
            } else if (intent?.action == CONNECTION_FAILED) {
                val name = intent.extras?.getString(DEVICE_NAME)
                val index = deviceNames.indexOf(name)
                adapter.enableDeviceButton(index)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(applicationContext, ConnectionService::class.java)
        serviceIntent.apply {
            `package` = applicationContext.packageName
        }
        bindService(serviceIntent, connection, 0)

        val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        Log.i("Notifications", notificationsEnabled.toString())
        if (!notificationsEnabled) {
            val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    Toast.makeText(applicationContext, "Thanks for enabling notifications!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "Uh oh, things might not work properly!", Toast.LENGTH_SHORT).show()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged", "UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        hideUI()
        launch(Dispatchers.IO) {
            listenUDP = true
            UDPListener()
        }
        launch(Dispatchers.IO) {
            checkNetwork()
        }
        devices.clear()
        deviceNames.clear()
        // must update whole backgrounds on whole dataset
        adapter.notifyDataSetChanged()

        val intentFilter = IntentFilter()
        intentFilter.addAction(START_MIX_ACTIVITY)
        intentFilter.addAction(CONNECTION_FAILED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        listenUDP = false
        unregisterReceiver(broadcastReceiver)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        bound = false
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(applicationContext, ConnectionService::class.java)
        serviceIntent.apply {
            `package` = applicationContext.packageName
        }
        stopService(serviceIntent)
    }

    private fun checkNetwork() {
        if (isConnected(applicationContext)) {
            binding.startButton.setOnClickListener {
                val address: InetAddress
                try {
                    address = InetAddress.getByName(binding.ipEditText.text.toString())
                } catch (e: UnknownHostException) {
                    return@setOnClickListener
                }
                connectionService.Connect(address)
                return@setOnClickListener
            }
        } else {
            binding.startButton.setOnClickListener { checkNetwork() }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "You're not connected to a network!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    @Suppress("FunctionName")
    private fun UDPListener() {
        val handler = Handler(Looper.getMainLooper())
        var socket = DatagramSocket(8877)
        socket.soTimeout = 100
        socket.broadcast = true
        while (listenUDP) {
            try {
                val recvBuf = ByteArray(socket.receiveBufferSize)
                if (socket.isClosed) {
                    socket = DatagramSocket(8877)
                    socket.soTimeout = 100
                    socket.broadcast = true
                }
                val packet = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(packet)
                packet.address.hostAddress
                val senderName = String(recvBuf.copyOf(packet.length))
                handler.post { adapter.addDevice(senderName) }
                if (!devices.containsKey(senderName)) {
                    devices[senderName] = InetAddress.getByName(packet.address.hostAddress)
                }
            } catch (e: SocketTimeoutException) {
                // Nothing really to do here
            } catch (e: IOException) {
                Log.e("UDP client has IOException", "error: ", e)
                listenUDP = false
            }
        }
        socket.close()
    }

    private val clickListener = object : DeviceSelectRecyclerViewAdapter.DeviceButtonClickListener {
        override fun onItemClick(view: View?, index: Int) {
            val name = deviceNames[index]
            val ip = devices[name]
            if (ip != null) {
                binding.ipEditText.setText(ip.toString().removePrefix("/"))
                connectionService.Connect(ip, name)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.ConnectionBinder
            connectionService = binder.getService()
            bound = true

            if (connectionService.state() == ConnectionService.States.CONNECTED) connectionService.Disconnect()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            bound = false
        }

    }

    companion object {
        const val IP_ADDRESS_PREFERENCES = "ipAddress"

        const val START_MIX_ACTIVITY = "START_MIX_ACTIVITY"
        const val CONNECTION_FAILED = "CONNECTION_FAILED"
        const val DEVICE_NAME = "DEVICE_NAME"

        const val EXTRA_DEMO_MODE = "EXTRA_DEMO_MODE"

        fun ByteArray.toHexString(length: Int): String {
            return this.joinToString("", limit = length) {
                java.lang.String.format("%02x ", it)
            }
        }

        fun isConnected(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }
    }
}