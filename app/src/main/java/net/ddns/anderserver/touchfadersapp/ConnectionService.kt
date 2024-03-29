package net.ddns.anderserver.touchfadersapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.illposed.osc.MessageSelector
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.OSCSerializerAndParserBuilder
import com.illposed.osc.argument.handler.Activator
import com.illposed.osc.transport.OSCPortIn
import com.illposed.osc.transport.OSCPortOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

@Suppress("DeferredResultUnused", "FunctionName")
class ConnectionService : Service() {

    enum class States {
        WAITING,
        CONNECTED,
        RUNNING
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "connection"
        const val ONGOING_NOTIFICATION_ID = 1

        var DEVICE_IP: InetAddress? = null
        var DEVICE_NAME: String? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Connection service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationService.createNotificationChannel(channel)
        }

        val notification = buildNotification("Pending connection")
        startForeground(ONGOING_NOTIFICATION_ID, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildNotification(
        text: String,
        intentClass: Class<*> = MixSelectActivity::class.java
    ): Notification {
        val notificationIntent = Intent(this, intentClass)
//            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent: PendingIntent = notificationIntent.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("TouchFaders connection")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    private val binder = ConnectionBinder()

    inner class ConnectionBinder : Binder() {
        // returns this instance so that clients can call public methods
        fun getService(): ConnectionService = this@ConnectionService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun Connect(address: InetAddress, name: String? = null) {
        DEVICE_IP = address
        DEVICE_NAME = name
        CoroutineScope(Dispatchers.Default).launch {
            async(Dispatchers.IO) {
                // connects via TCP
                val socketAddress = InetSocketAddress(address, 8878)
                val socket = Socket()
                try {
                    socket.connect(socketAddress, 100)
                } catch (e: IOException) {
                    failed(name, address)
                    return@async
                }
                socket.soTimeout = 100
                val byteArraySend = Build.MODEL.encodeToByteArray()
                socket.getOutputStream().write(byteArraySend)
                val byteArrayReceive = ByteArray(socket.receiveBufferSize)
                val bytesRead: Int
                try {
                    bytesRead =
                        socket.getInputStream().read(byteArrayReceive, 0, socket.receiveBufferSize)
                } catch (e: IOException) {
                    failed(name, address)
                    return@async
                } finally {
                    socket.close()
                }

                if (bytesRead >= 4) {
                    var index = 0
                    // OSC port to recieve on
                    receivePort = 9000 + byteArrayReceive[index++].toInt()
                    // OSC port to send on
                    sendPort = 8000 + byteArrayReceive[index++].toInt()
                    // number of channels
                    channels = byteArrayReceive[index++].toInt()
                    // get channel colours
                    for (i: Int in 0 until channels()) {
                        channelColours().add(byteArrayReceive[index++].toInt())
                    }
                    // offset mixes by number of channels
                    // number of mixes
                    mixes = byteArrayReceive[index++].toInt()
                    // get mix colours
                    for (i: Int in 0 until mixes()) {
                        mixColours().add(byteArrayReceive[index++].toInt())
                    }
                    for (i: Int in 0 until mixes()) {
                        var mixName = ""
                        for (c: Int in 0 until 6) {
                            val byte = byteArrayReceive[index + (i * 6) + c]
                            if (byte != 0.toByte()) {
                                mixName += byte.toInt().toChar()
                            }
                        }
                        mixNames().add(mixName)
                    }

                    state = States.CONNECTED

                    launch(Dispatchers.Default) {
                        val newNotification = buildNotification(
                            "Connected to ${
                                DEVICE_NAME ?: DEVICE_IP.toString().trim('/')
                            }"
                        )
                        updateNotification(newNotification)
                        val startMixIntent = Intent(StartupActivity.START_MIX_ACTIVITY)
                        startMixIntent.apply {
                            `package` = applicationContext.packageName
                        }
                        sendBroadcast(startMixIntent)
                    }
                    // SUCCESS!
                    return@async
                }
                failed(name, address)
                return@async
            }
        }
    }

    private fun failed(name: String?, ip: InetAddress) {
        DEVICE_IP = null
        DEVICE_NAME = null
        val connectionFailedIntent = Intent(StartupActivity.CONNECTION_FAILED)
        connectionFailedIntent.putExtra(StartupActivity.DEVICE_NAME, name)
        connectionFailedIntent.apply {
            `package` = applicationContext.packageName
        }
        sendBroadcast(connectionFailedIntent)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, "Failed to connect to ${name ?: ip.toString().removePrefix("/")}", Toast.LENGTH_SHORT).show()
        }
    }

    var state = States.WAITING
    fun state(): States = state

    fun address(): InetAddress? = DEVICE_IP

    private var receivePort: Int? = null
    fun receivePort(): Int = receivePort ?: 0

    private var sendPort: Int? = null
    fun sendPort(): Int = sendPort ?: 0

    private var channels: Int? = null
    fun channels(): Int = channels ?: 0

    private var channelColours: MutableList<Int> = mutableListOf()
    fun channelColours(): MutableList<Int> = channelColours

    private var mixes: Int? = null
    fun mixes(): Int = mixes ?: 0

    private var mixColours: MutableList<Int> = mutableListOf()
    fun mixColours(): MutableList<Int> = mixColours

    private var mixNames: MutableList<String> = mutableListOf()
    fun mixNames(): MutableList<String> = mixNames

    private var selectedMix: Int? = null
    fun selectedMix(): Int = selectedMix ?: 0
    fun selectedMix(index: Int) {
        state = States.RUNNING
        selectedMix = index
        updateNotification(
            buildNotification(
                "Tweaking mix ${selectedMix ?: "null"}",
                MainActivity::class.java
            )
        )
        // Open and send OSC
        CoroutineScope(Dispatchers.Default).launch {
            async(Dispatchers.IO) {
                // Workaround for java.awt.Color missing
                val serializer = OSCSerializerAndParserBuilder()
                serializer.setUsingDefaultHandlers(false)
                val defaultParserTypes = Activator.createSerializerTypes()
                defaultParserTypes.removeAt(16)
                var typeChar = 'a'
                for (argumentHandler in defaultParserTypes) {
                    serializer.registerArgumentHandler(argumentHandler, typeChar)
                    typeChar++
                }
                oscPortOut = OSCPortOut(serializer, InetSocketAddress(address(), sendPort()))
                oscPortOut.send(OSCMessage("/" + MainActivity.CONNECT, mutableListOf(1)))
                oscPortIn = OSCPortIn(InetSocketAddress("::", receivePort()))
                oscPortIn.dispatcher.isAlwaysDispatchingImmediately = true
            }
        }
    }

    lateinit var oscPortOut: OSCPortOut
    lateinit var oscPortIn: OSCPortIn

    fun send(message: OSCMessage) {
        CoroutineScope(Dispatchers.Default).launch {
            async(Dispatchers.IO) {
                try {
                    oscPortOut.send(message)
                } catch (e: Exception) {
                    // TODO: output buffer overflow
                }
            }
        }
    }

    fun addListener(messageSelector: MessageSelector, listener: OSCMessageListener) {
        oscPortIn.dispatcher.addListener(messageSelector, listener)
    }

    fun startListening() {
        oscPortIn.startListening()
    }

    fun stopListening() {
        oscPortIn.stopListening()
    }

    fun removeListener(messageSelector: MessageSelector, listener: OSCMessageListener) {
        oscPortIn.dispatcher.removeListener(messageSelector, listener)
    }

    fun deselectMix() {
        state = States.CONNECTED
        selectedMix = null
        updateNotification(
            buildNotification(
                "Connected to ${
                    DEVICE_NAME ?: DEVICE_IP.toString().trim('/')
                }"
            )
        )
        // close OSC
        CoroutineScope(Dispatchers.Default).launch {
            async(Dispatchers.IO) {
                oscPortOut.close()
                oscPortIn.close()
            }
        }
    }

    fun Disconnect() {
        state = States.WAITING
        val newNotification = buildNotification("Pending connection")
        updateNotification(newNotification)
        // disconnects via UDP
        CoroutineScope(Dispatchers.Default).launch {
            async(Dispatchers.IO) {
                val data = Build.MODEL.toByteArray()
                val packet = DatagramPacket(data, data.size, DEVICE_IP, 8878)
                try {
                    val socket = DatagramSocket()
                    socket.send(packet)
                    socket.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: NullPointerException) {
                    // Not connected? just ignore
                }
                reset()
            }
        }
    }

    private fun reset() {
        channels = 0
        channelColours = mutableListOf()
        mixes = 0
        mixColours = mutableListOf()
        mixNames = mutableListOf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Disconnect()
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.cancel(ONGOING_NOTIFICATION_ID)
    }
}