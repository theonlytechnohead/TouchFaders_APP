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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortOut;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    Thread udpListenerThread;
    boolean runUDP = true;

    RecyclerView recyclerView;
    FaderStripRecyclerViewAdapter adapter;
    Context instanceContext;
    BoxedVertical mixMeter;
    ConstraintLayout mixInfo;
    TextView mixName;
    TextView mixNumber;

    private Boolean demo;
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
                numChannels = connectionService.channels();
                channelColours = new ArrayList<>(connectionService.channelColours());
                currentMix = connectionService.selectedMix();
                mixName.setText(connectionService.mixNames().get(currentMix - 1).replace(" ", " \n"));
                mixNumber.setText("" + currentMix);
                mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[connectionService.mixColours().get(currentMix - 1)]);
                mixColour = connectionService.mixColours().get(currentMix - 1);
                mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);
            }

            boolean wide = !PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(getResources().getString(R.string.fader_width), "narrow").equals("narrow");

            adapter = new FaderStripRecyclerViewAdapter(instanceContext, numChannels, channelColours, wide);
            adapter.setValuesChangeListener((view, index, boxedVertical, points) -> SendOSCFaderValue(index + 1, points));
            recyclerView = findViewById(R.id.faderRecyclerView);
            recyclerView.setAdapter(adapter);

//            connectionService.addListener(new OSCPatternAddressMessageSelector("/tes?/*"), event -> Log.i("OSC", "Got OSC!"));
            connectionService.addListener(faderPattern, faderListener);
            connectionService.addListener(labelPattern, labelListener);
            connectionService.addListener(patchPattern, patchListener);
            connectionService.addListener(disconnectPattern, disconnectListener);
            connectionService.startListening();

            SendOSCGetMix(currentMix);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;

            connectionService.stopListening();
//            connectionService.removeListener(new OSCPatternAddressMessageSelector("/tes?/*"), event -> Log.i("OSC", "Got OSC!"));
            connectionService.removeListener(faderPattern, faderListener);
            connectionService.removeListener(labelPattern, labelListener);
            connectionService.removeListener(patchPattern, patchListener);
            connectionService.removeListener(disconnectPattern, disconnectListener);
        }
    };

    OSCPatternAddressMessageSelector faderPattern = new OSCPatternAddressMessageSelector("/mix*/fader*");
    OSCMessageListener faderListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int faderIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
            if (0 <= faderIndex && faderIndex < adapter.getItemCount()) {
                adapter.setFaderLevel(faderIndex, (int) event.getMessage().getArguments().get(0));
                Handler handler = new Handler(getMainLooper());
                handler.post(() -> adapter.notifyDataSetChanged());
            }
        }
    };

    OSCPatternAddressMessageSelector labelPattern = new OSCPatternAddressMessageSelector("/label*");
    OSCMessageListener labelListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1;
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(getMainLooper());
                adapter.setChannelName(channelIndex, (String) event.getMessage().getArguments().get(0));
                handler.post(() -> adapter.notifyDataSetChanged());
            }
        }
    };

    OSCPatternAddressMessageSelector patchPattern = new OSCPatternAddressMessageSelector("/patch*");
    OSCMessageListener patchListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1;
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(getMainLooper());
                adapter.setChannelPatchIn(channelIndex, (String) event.getMessage().getArguments().get(0));
                handler.post(() -> adapter.notifyDataSetChanged());
            }
        }
    };

    OSCPatternAddressMessageSelector disconnectPattern = new OSCPatternAddressMessageSelector("/disconnect");
    OSCMessageListener disconnectListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            connectionService.Disconnect();
            finish();
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
            boolean wide = !PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(getResources().getString(R.string.fader_width), "narrow").equals("narrow");
            adapter = new FaderStripRecyclerViewAdapter(instanceContext, numChannels, channelColours, wide);
            adapter.setValuesChangeListener((view, index, boxedVertical, points) -> {});
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

        findViewById(R.id.back_button).setOnClickListener((view) -> {
            finish();
        });

        runUDP = true;
        if (!demo) {
            if (udpListenerThread.getState() != Thread.State.NEW) {
                udpListenerThread.start();
            }
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            frameLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
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
            });
        }
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
    }

    public void SendOSCFaderValue(int fader, int faderValue) {
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(faderValue);
        OSCMessage message = new OSCMessage("/mix" + currentMix + "/fader" + fader, arguments);
        Log.i("OSC", message.getAddress() + " " + message.getArguments().get(0).toString());
        connectionService.send(message);
    }

    public void SendOSCGetMix(int mix) {
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(1);
        OSCMessage message = new OSCMessage("/mix" + mix, arguments);
        connectionService.send(message);
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