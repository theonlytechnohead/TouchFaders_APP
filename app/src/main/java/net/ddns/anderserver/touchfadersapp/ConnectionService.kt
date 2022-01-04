package net.ddns.anderserver.touchfadersapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.*

class ConnectionService : Service() {

    enum class states {
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
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Connection service",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(channel)

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
            PendingIntent.getActivity(this, 0, it, 0)
        }

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
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
                val socket = Socket();
                socket.connect(socketAddress, 100);
                socket.soTimeout = 100;
                var byteArraySend = InetAddress.getByName(StartupActivity.getLocalIP()).address
                byteArraySend += Build.MODEL.encodeToByteArray()
                socket.getOutputStream().write(byteArraySend)
                val byteArrayReceive = ByteArray(socket.receiveBufferSize)
                val bytesRead =
                    socket.getInputStream().read(byteArrayReceive, 0, socket.receiveBufferSize)
                //Log.i("TCP", byteArrayReceive.toHexString(bytesRead))
                socket.close()

                state = states.CONNECTED

                receivePort = byteArrayReceive[0].toInt()
                sendPort = byteArrayReceive[1].toInt()
                channels = byteArrayReceive[2].toInt()
                mixes = byteArrayReceive[(3)].toInt()

                launch(Dispatchers.Default) {
                    val newNotification = buildNotification(
                        "Connected to ${
                            DEVICE_NAME ?: DEVICE_IP.toString().trim('/')
                        }"
                    )
                    updateNotification(newNotification)
                    sendBroadcast(Intent(StartupActivity.START_MIX_ACTIVITY))
                }
            }
        }
    }

    var state = states.WAITING
    fun state(): states = state

    fun address(): String = DEVICE_IP.toString().trim('/')

    private var receivePort: Int? = null
    fun receivePort(): Int = receivePort ?: 0

    private var sendPort: Int? = null
    fun sendPort(): Int = sendPort ?: 0

    private var channels: Int? = null
    fun channels(): Int = channels ?: 0

    private var mixes: Int? = null
    fun mixes(): Int = mixes ?: 0

    private var selectedMix: Int? = null
    fun selectedMix(): Int = selectedMix ?: 0
    fun selectedMix(index: Int) {
        state = states.RUNNING
        selectedMix = index
        updateNotification(
            buildNotification(
                "Tweaking mix ${selectedMix ?: "null"}",
                MainActivity::class.java
            )
        )
    }

    fun deselectMix() {
        state = states.CONNECTED
        selectedMix = null
        updateNotification(
            buildNotification(
                "Connected to ${
                    DEVICE_NAME ?: DEVICE_IP.toString().trim('/')
                }"
            )
        )
    }

    fun Disconnect() {
        // disconnects via TCP
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
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Disconnect()
    }
}