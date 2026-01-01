package net.ddns.anderserver.touchfadersapp.startup

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
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import net.ddns.anderserver.touchfadersapp.service.ConnectionService
import net.ddns.anderserver.touchfadersapp.main.MainActivity
import net.ddns.anderserver.touchfadersapp.mix.MixSelectActivity
import net.ddns.anderserver.touchfadersapp.settings.SettingsActivity
import net.ddns.anderserver.touchfadersapp.databinding.StartupBinding
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext

class StartupActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: StartupBinding
    private lateinit var adapter: DeviceSelectRecyclerViewAdapter
    private var deviceNames: MutableList<String> = mutableListOf()
    private val devices: HashMap<String, InetAddress> = hashMapOf()

    var sharedPreferences: SharedPreferences? = null

    private lateinit var nsdManager: NsdManager
    private lateinit var discoveryListener: NsdManager.DiscoveryListener
    private lateinit var serviceInfoCallback: NsdManager.ServiceInfoCallback
    private lateinit var resolveListener: NsdManager.ResolveListener

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

        adapter = DeviceSelectRecyclerViewAdapter(
            applicationContext,
            binding.deviceRecyclerView,
            deviceNames
        )
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

        // listen for host applications
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfoCallback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.w("DNS", "Couldn't register, code: $errorCode")
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    Log.i("DNS", "Service info for ${serviceInfo.serviceName} resolves ${serviceInfo.hostAddresses.size} addresses")
                    serviceInfo.hostAddresses.forEach {
                        Log.i("DNS", "\t$it")
                    }
                    Handler(Looper.getMainLooper()).post {
                        adapter.addDevice(serviceInfo.serviceName)
                    }
                    launch(Dispatchers.IO) {
                        val address = InetAddress.getByAddress(InetAddress.getByName(serviceInfo.serviceName).address)
                        Log.i("DNS", "Found address for ${serviceInfo.serviceName}: $address")
                        devices[serviceInfo.serviceName] = address
                    }
                }

                override fun onServiceLost() {
                    Log.i("DNS", "Lost a service")
                }

                override fun onServiceInfoCallbackUnregistered() {
                    Log.i("DNS", "Service info callback was unregistered")
                }

            }
        } else {
            resolveListener = object : NsdManager.ResolveListener {
                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.i("DNS", "Resolved: ${serviceInfo!!.serviceName} to ${serviceInfo.host}")
                    Handler(Looper.getMainLooper()).post {
                        adapter.addDevice(serviceInfo.serviceName)
                    }
                    if (!devices.containsKey(serviceInfo.serviceName)) {
                        val address = InetAddress.getByName(serviceInfo.serviceName)
                        Log.i("DNS", "Found address for ${serviceInfo.serviceName}: $address")
                        devices[serviceInfo.serviceName] = address
                    }
                }

                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    // we don't really care
                }
            }
        }
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.i("DNS", "Started listening for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w("DNS", "Failed to listen for $serviceType, code: $errorCode")
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                Log.i("DNS", "Found: ${service!!.serviceName}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    nsdManager.registerServiceInfoCallback(service, mainExecutor, serviceInfoCallback)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nsdManager.resolveService(service, mainExecutor, resolveListener)
                } else {
                    nsdManager.resolveService(service, resolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                Log.i("DNS", "Lost: ${service!!.serviceName}")
                Handler(Looper.getMainLooper()).post {
                    adapter.removeDevice(service.serviceName)
                }
                devices.remove(service.serviceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
                    } catch (e: IllegalArgumentException) {
                        // we don't care
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.i("DNS", "Stopped listening for $serviceType")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
                    } catch (e: IllegalArgumentException) {
                        // we don't care
                    }
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.i("DNS", "Failed to stop for $serviceType, code: $errorCode")
            }
        }
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
            checkNetwork()
        }
        nsdManager.discoverServices("_touchfaders._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
            } catch (e: IllegalArgumentException) {
                // we don't care
            }
        }
        nsdManager.stopServiceDiscovery(discoveryListener)
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