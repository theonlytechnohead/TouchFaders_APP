package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.illposed.osc.OSCBadDataEvent;
import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCPacketEvent;
import com.illposed.osc.OSCPacketListener;
import com.illposed.osc.transport.udp.OSCPortIn;
import com.illposed.osc.transport.udp.OSCPortOut;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    SharedPreferences sharedPreferences;

    OSCPortOut oscPortOut;
    OSCPortIn oscPortIn;
    Thread udpListenerThread;
    boolean runUDP = true;

    ArrayList<BoxedVertical> faders = new ArrayList<>();
    RecyclerView recyclerView;
    FaderStripRecyclerViewAdapter adapter;
    Context instanceContext;
    BoxedVertical mixMeter;
    ConstraintLayout mixInfo;
    TextView mixName;
    TextView mixNumber;

    private Boolean demo;
    private String ipAddress;
    private int receivePort;
    private int sendPort;
    private int numChannels;
    private ArrayList<Integer> channelColours;
    private int currentMix;
    private Integer mixColour;

    ConnectionService connectionService;
    Boolean bound = false;
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            connectionService = ((ConnectionService.ConnectionBinder) iBinder).getService();
            bound = true;

            // grab variables and setup stuffs
            if (!demo) {
                ipAddress = connectionService.address();
                receivePort = connectionService.receivePort();
                sendPort = connectionService.sendPort();
                numChannels = connectionService.channels();
                channelColours = new ArrayList<>(connectionService.channelColours());
                currentMix = connectionService.selectedMix();
                mixName.setText(connectionService.mixNames().get(currentMix - 1).replace(" ", " \n"));
                mixNumber.setText("" + currentMix);
                mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[connectionService.mixColours().get(currentMix - 1)]);
                mixColour = connectionService.mixColours().get(currentMix - 1);
                mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);

                AsyncTask.execute(MainActivity.this::OpenOSCPortIn);
                AsyncTask.execute(MainActivity.this::OpenOSCPortOut);
            }

            adapter = new FaderStripRecyclerViewAdapter(instanceContext, numChannels, channelColours);
            adapter.setValuesChangeListener((view, index, boxedVertical, points) -> SendOSCFaderValue(index + 1, points));
            recyclerView = findViewById(R.id.faderRecyclerView);
            recyclerView.setAdapter(adapter);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
        }
    };

    OSCPacketListener packetListener = new OSCPacketListener() {
        @Override
        public void handlePacket(OSCPacketEvent event) {
            OSCPacket packet = event.getPacket();
            if (packet instanceof OSCMessage) {
                OSCMessage message = (OSCMessage) packet;
                if (message.getAddress().contains("/mix" + currentMix + "/fader")) {
                    String[] segments = message.getAddress().split("/");
                    int faderIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
                    if (0 <= faderIndex && faderIndex < adapter.getItemCount()) {
                        adapter.setFaderLevel(faderIndex, (int) message.getArguments().get(0));
                        Handler handler = new Handler(getMainLooper());
                        handler.post(() -> adapter.notifyDataSetChanged());
                    }
                }
                if (message.getAddress().contains("/label")) {
                    String[] segments = message.getAddress().split("/");
                    int channelIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1;
                    if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                        Handler handler = new Handler(getMainLooper());
                        adapter.setChannelName(channelIndex, (String) message.getArguments().get(0));
                        handler.post(() -> {
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
                if (message.getAddress().contains("/patch")) {
                    String[] segments = message.getAddress().split("/");
                    int channelIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1;
                    if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                        Handler handler = new Handler(getMainLooper());
                        adapter.setChannelPatchIn(channelIndex, (String) message.getArguments().get(0));
                        handler.post(() -> {
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            }
            if (packet instanceof OSCBundle) {
                OSCBundle bundle = (OSCBundle) packet;
                List<OSCPacket> packetList = bundle.getPackets();
                for (OSCPacket pack : packetList) {
                    OSCMessage message = (OSCMessage) pack;
                    Log.i("OSC message", message.getAddress() + " : " + message.getArguments().get(0).toString());
                }
            }
        }

        @Override
        public void handleBadData(OSCBadDataEvent event) {
            Log.d("OSC message", "Bad packet received");
        }
    };

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //BasicConfigurator.configure();

        demo = getIntent().getBooleanExtra(StartupActivity.EXTRA_DEMO_MODE, false);

        setContentView(R.layout.main);

        mixMeter = findViewById(R.id.mixMeter);
        mixMeter.setValue(0);

        mixInfo = findViewById(R.id.mix_info_layout);
        mixName = findViewById(R.id.mix_name);
        mixNumber = findViewById(R.id.mix_number);

        instanceContext = this;

        if (!demo) {
            udpListenerThread = new Thread(new ClientListen());
        } else {
            numChannels = 32;
            channelColours = new ArrayList<>();
            channelColours.add(7);
            channelColours.add(7);
            channelColours.add(7);
            channelColours.add(7);
            channelColours.add(7);
            channelColours.add(7);
            channelColours.add(6);
            channelColours.add(2);
            channelColours.add(2);
            channelColours.add(5);
            channelColours.add(3);
            channelColours.add(3);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(6);
            channelColours.add(6);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(5);
            channelColours.add(4);
            channelColours.add(1);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            channelColours.add(0);
            currentMix = 1;
            mixColour = 1;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!demo) {
            Intent serviceIntent = new Intent(this, ConnectionService.class);
            bindService(serviceIntent, connection, 0);
        } else {
            adapter = new FaderStripRecyclerViewAdapter(instanceContext, numChannels, channelColours);
            adapter.setValuesChangeListener((view, index, boxedVertical, points) -> SendOSCFaderValue(index + 1, points));
            recyclerView = findViewById(R.id.faderRecyclerView);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Making it fullscreen...
        View frameLayout = findViewById(R.id.fullscreen_frame);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        frameLayout.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        // Fullscreen done!

        if (demo) {
            mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);
            mixName.setText("MIX \n1");
            mixNumber.setText("1");
            mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[currentMix]);
        }

        runUDP = true;
        if (!demo) {
            if (udpListenerThread.getState() != Thread.State.NEW) {
                udpListenerThread.start();
            }
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            frameLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
                //RecyclerView faderLayoutView = findViewById(R.id.faderRecyclerView);
                //RecyclerView faderLayoutView = recyclerView;
                BoxedVertical meter = findViewById(R.id.mixMeter);
                ConstraintLayout mixInfo = findViewById(R.id.mix_info_layout);
                ViewGroup.LayoutParams meterParams = meter.getLayoutParams();
                ViewGroup.LayoutParams mixInfoParams = mixInfo.getLayoutParams();
                if (cutout != null) {
                    final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
                    int pixels = (int) (35 * scale + 0.5f);
                    Handler handler = new Handler(Looper.getMainLooper());
                    if (cutout.getSafeInsetLeft() != meterParams.width) {
                        meterParams.width = cutout.getSafeInsetLeft();
                        if (meterParams.width == 0) {
                            meterParams.width = pixels;
                        }
                        handler.post(() -> meter.setLayoutParams(meterParams));
                    }
                    if (cutout.getSafeInsetRight() != mixInfoParams.width + pixels) {
                        mixInfoParams.width = cutout.getSafeInsetRight() + pixels;
                        if (mixInfoParams.width == 0) {
                            mixInfoParams.width = pixels;
                        }
                        mixInfo.setPadding(0, 0, cutout.getSafeInsetRight(), 0);
                        handler.post(() -> mixInfo.setLayoutParams(mixInfoParams));
                    }
                }

//                Log.i("CUTOUT", "safeLeft: " + cutout.getSafeInsetLeft() + "  safeRight: " + cutout.getSafeInsetRight());
            });
        }
        AsyncTask.execute(() -> {
            boolean ready = false;
            while (!ready) {
                if (oscPortIn != null && oscPortOut != null) {
                    ready = oscPortIn.isListening() && oscPortOut.isConnected();
                } else {
                    ready = false;
                }
            }
            SendOSCGetMix(currentMix);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        runUDP = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound)
            unbindService(connection);
        try {
            if (oscPortIn != null) oscPortIn.close();
            if (oscPortOut != null) oscPortOut.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private String GetLocalIP() {
        try {
            String localAddress = "";
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLinkLocalAddress()) {
                        if (!inetAddress.toString().contains(":"))
                            localAddress = inetAddress.getHostAddress();
                    }
                }
            }
            return localAddress;
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void OpenOSCPortIn() {
        try {
            oscPortIn = new OSCPortIn(new InetSocketAddress(GetLocalIP(), 9000 + receivePort));
        } catch (BindException e) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> Toast.makeText(getApplicationContext(), "Failed to bind IP to OSC!", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true) {
            if (oscPortIn != null) {
                oscPortIn.getDispatcher().setAlwaysDispatchingImmediately(true);
                oscPortIn.addPacketListener(packetListener);
                break;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
        oscPortIn.startListening();
    }

    private void OpenOSCPortOut() {
        try {
            oscPortOut = new OSCPortOut(new InetSocketAddress(InetAddress.getByName(ipAddress), 8000 + sendPort));
            oscPortOut.connect();
        } catch (Exception e) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show());
        }
    }

    public void SendOSCFaderValue(int fader, int faderValue) {
        AsyncTask.execute(() -> {
            Handler handler = new Handler(Looper.getMainLooper());

            if (oscPortOut != null) {
                ArrayList<Object> arguments = new ArrayList<>();
                arguments.add(faderValue);
                OSCMessage message = new OSCMessage("/mix" + currentMix + "/fader" + fader, arguments);

                try {
                    oscPortOut.send(message);
                    Thread.sleep(5);
                    handler.post(() -> Log.i("OSC message", message.getAddress() + " : " + message.getArguments().get(0).toString()));
                } catch (Exception e) {
                    handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    public void SendOSCGetMix(int mix) {
        AsyncTask.execute(() -> {
            Handler handler = new Handler(Looper.getMainLooper());

            if (oscPortOut != null) {
                ArrayList<Object> arguments = new ArrayList<>();
                arguments.add(1);
                OSCMessage message = new OSCMessage("/mix" + mix, arguments);

                try {
                    oscPortOut.send(message);
                    //handler.post(() -> Log.i("OSC message", ipAddress.toString() + ":" + sendPort + " " + message.getAddress() + " : " + message.getArguments().get(0).toString()));
                } catch (Exception e) {
                    //handler.post(() -> Log.e("OSC mix error", e.toString()));
                    handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    public class ClientListen implements Runnable {
        @Override
        public void run() {
            Handler handler = new Handler(Looper.getMainLooper());
            DatagramSocket socket = null;

            while (runUDP) {
                try {
                    byte[] recvBuf = new byte[16];
                    if (socket == null) {
                        socket = new DatagramSocket(8879);
                        socket.setSoTimeout(100);
                        socket.setBroadcast(true);
                    }
                    if (socket.isClosed()) {
                        socket = new DatagramSocket(8879);
                        socket.setSoTimeout(100);
                        socket.setBroadcast(true);
                    }
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(packet);
                    //String senderIP = packet.getAddress().getHostAddress();

                    byte[] meteringData = packet.getData();
                    byte meter = meteringData[currentMix - 1];
                    handler.post(() -> mixMeter.setValue(meter));
                } catch (SocketTimeoutException e) {
                    // Nothing really to do here
                } catch (IOException e) {
                    Log.e("UDP client has IOException", "error: ", e);
                    runUDP = false;
                }
            }
            if (socket != null) socket.close();
        }
    }

}