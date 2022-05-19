package net.ddns.anderserver.touchfadersapp

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ddns.anderserver.touchfadersapp.databinding.StartupBinding
import java.io.IOException
import java.net.*
import kotlin.coroutines.CoroutineContext

class StartupActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: StartupBinding
    private lateinit var adapter: DeviceSelectRecyclerViewAdapter
    private var deviceNames: MutableList<String> = mutableListOf()
    private val devices: HashMap<String, InetAddress> = hashMapOf()

    var sharedPreferences: SharedPreferences? = null

    var listenUDP = true

    lateinit var connectionService: ConnectionService
    var bound = false

    var broadcastReceiver: BroadcastReceiver? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StartupBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

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
        binding.demoButton.setOnClickListener {
            val intent = Intent(it.context, MainActivity::class.java)
            intent.putExtra(EXTRA_DEMO_MODE, true)
            startActivity(intent)
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(it.context, SettingsActivity::class.java))
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

        adapter = DeviceSelectRecyclerViewAdapter(applicationContext, deviceNames)
        adapter.setClickListener(clickListener)
        binding.deviceRecyclerView.adapter = adapter

        // Start connection service
        val serviceIntent = Intent(applicationContext, ConnectionService::class.java)
        applicationContext.startForegroundService(serviceIntent)

        // create broadcast receiver to start activity
        broadcastReceiver = Receiver()
    }

    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == START_MIX_ACTIVITY) {
                val mixIntent = Intent(applicationContext, MixSelectActivity::class.java)
                startActivity(mixIntent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(applicationContext, ConnectionService::class.java)
        bindService(serviceIntent, connection, 0)
    }

    override fun onResume() {
        super.onResume()
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
        launch(Dispatchers.IO) {
            listenUDP = true
            UDPListener();
        }
        launch(Dispatchers.IO) {
            checkNetwork()
        }
        devices.clear()
        deviceNames.clear()
        adapter.notifyDataSetChanged()

        registerReceiver(broadcastReceiver, IntentFilter(START_MIX_ACTIVITY))
    }

    override fun onPause() {
        super.onPause()
        listenUDP = false;
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

    private fun UDPListener() {
        val handler = Handler(Looper.getMainLooper())
        var socket = DatagramSocket(8877)
        socket.soTimeout = 100;
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
                val length = packet.length
                val senderIP = packet.address.hostAddress;
                val senderName = String(recvBuf.copyOfRange(3, length - 1))
//                Log.i("UDP", senderName)
                handler.post { adapter.addDevice(senderName) }
                if (!devices.containsKey(senderName)) {
                    devices[senderName] = InetAddress.getByName(senderIP)
                }
                //Log.i("UDP", senderIP)
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
                //Toast.makeText(applicationContext, "Connecting to " + name + " at " + ip.toString(), Toast.LENGTH_SHORT).show()
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

            if (connectionService.state() == ConnectionService.states.CONNECTED) connectionService.Disconnect()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            bound = false
        }

    }

    companion object {
        const val IP_ADDRESS_PREFERENCES = "ipAddress"

        const val START_MIX_ACTIVITY = "START_MIX_ACTIVITY"

        const val EXTRA_DEMO_MODE = "EXTRA_DEMO_MODE"

        fun ByteArray.toHexString(length: Int): String {
            return this.joinToString("", limit = length) {
                java.lang.String.format("%02x ", it)
            }
        }

        fun getLocalIP(): String? {
            return try {
                var localAddress = ""
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val networkInterface = en.nextElement()
                    val enumIpAddr = networkInterface.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLinkLocalAddress) {
                            if (!inetAddress.toString().contains(':'))
                                localAddress = inetAddress.hostAddress
                        }
                    }
                }
                localAddress
            } catch (e: SocketException) {
                e.printStackTrace()
                null
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