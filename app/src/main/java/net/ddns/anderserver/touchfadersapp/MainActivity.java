package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;

import net.ddns.anderserver.touchfadersapp.classses.Group;
import net.ddns.anderserver.touchfadersapp.custom.BoxedVertical;
import net.ddns.anderserver.touchfadersapp.service.ConnectionService;
import net.ddns.anderserver.touchfadersapp.startup.StartupActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity implements ItemMoveCallback.StartDragListener {

    public static final String CONNECT = "connect";
    public static final String DISCONNECT = "disconnect";

    public static final String CHANNEL = "channel";
    public static final String MIX = "mix";
    public static final String NAME = "name";
    public static final String PATCH = "patch";
    public static final String MUTE = "mute";

    public static final String COLOUR = "colour";

    public static final String JSON_CHANNEL_LAYOUT = "channel_layout";

    Thread udpListenerThread;
    boolean runUDP = true;

    RecyclerView recyclerView;
    ChannelsRecyclerViewAdapter adapter;
    ItemTouchHelper touchHelper;

    Context instanceContext;
    BoxedVertical mixMeter;
    ConstraintLayout mixInfo;
    TextView mixName;
    TextView mixNumber;

    private Boolean demo;
    private int numChannels;
    private ArrayList<Integer> channelColours;
    private HashMap<Integer, Integer> channelLayer;
    private int currentMix;
    private Integer mixColour;
    private float width;

    ConnectionService connectionService;
    Boolean bound = false;
    final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            connectionService = ((ConnectionService.ConnectionBinder) iBinder).getService();
            bound = true;

            // grab variables and setup stuffs
            if (!demo) {
                numChannels = connectionService.channels();
                channelColours = new ArrayList<>(connectionService.channelColours());
                currentMix = connectionService.selectedMix();
                mixName.setText(connectionService.mixNames().get(currentMix - 1).replace(" ", "\n \n"));
                mixNumber.setText(String.valueOf(currentMix));
                mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[connectionService.mixColours().get(currentMix - 1)]);
                mixColour = connectionService.mixColours().get(currentMix - 1);
                mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);
            }

            width = Float.parseFloat(Objects.requireNonNull(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(getResources().getString(R.string.setting_fader_width), "35")));

            channelLayer = loadMap();
            recyclerView = findViewById(R.id.faderRecyclerView);
            adapter = new ChannelsRecyclerViewAdapter(MainActivity.this, instanceContext, recyclerView, numChannels, channelLayer, loadLayout(), channelColours, width);
            adapter.setValuesChangeListener((index, points) -> SendOSCFaderValue(index + 1, points));
            adapter.setFaderMuteListener(((view, index, muted) -> SendOSCChannelMute(index + 1, muted)));
            ItemTouchHelper.Callback callback = new ItemMoveCallback(adapter);
            touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
            recyclerView.setAdapter(adapter);

//            connectionService.addListener(new OSCPatternAddressMessageSelector("/tes?/*"), event -> Log.i("OSC", "Got OSC!"));
            connectionService.addListener(sendPattern, sendListener);
            connectionService.addListener(sendMutePattern, sendMuteListener);
            connectionService.addListener(labelPattern, labelListener);
            connectionService.addListener(channelMutePattern, channelMuteListener);
            connectionService.addListener(patchPattern, patchListener);
            connectionService.addListener(colourPattern, colourListener);
            connectionService.addListener(disconnectPattern, disconnectListener);
            connectionService.startListening();

            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    SendOSCGetMix(currentMix);
                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;

            connectionService.stopListening();
//            connectionService.removeListener(new OSCPatternAddressMessageSelector("/tes?/*"), event -> Log.i("OSC", "Got OSC!"));
            connectionService.removeListener(sendPattern, sendListener);
            connectionService.removeListener(sendMutePattern, sendMuteListener);
            connectionService.removeListener(labelPattern, labelListener);
            connectionService.removeListener(channelMutePattern, channelMuteListener);
            connectionService.removeListener(patchPattern, patchListener);
            connectionService.removeListener(colourPattern, colourListener);
            connectionService.removeListener(disconnectPattern, disconnectListener);
        }
    };

    final OSCPatternAddressMessageSelector sendPattern = new OSCPatternAddressMessageSelector("/" + MIX + "*/" + CHANNEL + "*");
    final OSCMessageListener sendListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
            if (event.getMessage().getArguments().size() == 1) {
                Handler handler = new Handler(Looper.getMainLooper());
                if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                    handler.post(() -> adapter.setFaderLevel(channelIndex, (int) event.getMessage().getArguments().get(0)));
                }
            } else if (event.getMessage().getArguments().size() == 6) {
                if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    int level = (int) event.getMessage().getArguments().get(0);
                    boolean sendMuted = (boolean) event.getMessage().getArguments().get(1);
                    String name = (String) event.getMessage().getArguments().get(2);
                    boolean channelMuted = (boolean) event.getMessage().getArguments().get(3);
                    String patch = (String) event.getMessage().getArguments().get(4);
                    int colourIndex = (int) event.getMessage().getArguments().get(5);
                    handler.post(() -> adapter.setChannelStrip(channelIndex, level, sendMuted, name, channelMuted, patch, colourIndex));
                }
            }
        }
    };

    final OSCPatternAddressMessageSelector sendMutePattern = new OSCPatternAddressMessageSelector("/" + MIX + "*/" + CHANNEL + "*/" + MUTE);
    final OSCMessageListener sendMuteListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(Looper.getMainLooper());
                boolean muted = (int) event.getMessage().getArguments().get(0) == 1;
                handler.post(() -> adapter.setSendMute(channelIndex, muted));
            }
        }
    };

    final OSCPatternAddressMessageSelector labelPattern = new OSCPatternAddressMessageSelector("/" + NAME + "*");
    final OSCMessageListener labelListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1;
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> adapter.setChannelName(channelIndex, (String) event.getMessage().getArguments().get(0)));
            }
        }
    };

    final OSCPatternAddressMessageSelector channelMutePattern = new OSCPatternAddressMessageSelector("/" + CHANNEL + "*/" + MUTE);
    final OSCMessageListener channelMuteListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(Looper.getMainLooper());
                boolean muted = (boolean) event.getMessage().getArguments().get(0);
                handler.post(() -> adapter.setChannelMute(channelIndex, muted));
            }
        }
    };

    final OSCPatternAddressMessageSelector patchPattern = new OSCPatternAddressMessageSelector("/" + PATCH + "*");
    final OSCMessageListener patchListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1;
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> adapter.setChannelPatchIn(channelIndex, (String) event.getMessage().getArguments().get(0)));
            }
        }
    };

    final OSCPatternAddressMessageSelector colourPattern = new OSCPatternAddressMessageSelector("/" + CHANNEL + "*/" + COLOUR);
    final OSCMessageListener colourListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            String[] segments = event.getMessage().getAddress().split("/");
            int channelIndex = Integer.parseInt(segments[1].replaceAll("\\D+", "")) - 1;
            if (0 <= channelIndex && channelIndex < adapter.getItemCount()) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> adapter.setChannelColour(channelIndex, (int) event.getMessage().getArguments().get(0)));
            }
        }
    };

    final OSCPatternAddressMessageSelector disconnectPattern = new OSCPatternAddressMessageSelector("/" + DISCONNECT);
    final OSCMessageListener disconnectListener = new OSCMessageListener() {
        @Override
        public void acceptMessage(OSCMessageEvent event) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                connectionService.Disconnect();
                finish();
            });
        }
    };

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(i -> hideUI());

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
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        touchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!demo) {
            Intent serviceIntent = new Intent(this, ConnectionService.class);
            bindService(serviceIntent, connection, 0);
        } else {
            recyclerView = findViewById(R.id.faderRecyclerView);
            width = Float.parseFloat(Objects.requireNonNull(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(getResources().getString(R.string.setting_fader_width), "45")));
            channelLayer = loadMap();
            adapter = new ChannelsRecyclerViewAdapter(this, instanceContext, recyclerView, numChannels, channelLayer, loadLayout(), channelColours, width);
            adapter.setValuesChangeListener((index, points) -> {
            });
            adapter.setFaderMuteListener(((view, index, muted) -> {
            }));
            recyclerView.setHasFixedSize(true);
            recyclerView.setItemViewCacheSize(numChannels);
            recyclerView.getRecycledViewPool().setMaxRecycledViews(1, numChannels);
            ItemTouchHelper.Callback callback = new ItemMoveCallback(adapter);
            touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
            recyclerView.setAdapter(adapter);
            for (int i = 0; i < adapter.getItemCount(); i++) {
                adapter.setFaderLevel(i, 623);
                adapter.setChannelName(i, "ch " + (i + 1));
                adapter.setChannelPatchIn(i, String.valueOf(i + 1));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideUI();

        if (demo) {
            mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);
            mixName.setText("MX\n \n1");
            mixNumber.setText("1");
            mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[currentMix]);
        }

        findViewById(R.id.hide_button).setOnClickListener((view -> {
            adapter.toggleChannelHide();
            if (adapter.getHideUnusedChannelstrips()) {
                view.setBackgroundColor(getColor(R.color.grey));
            } else {
                view.setBackgroundColor(getColor(R.color.dark_grey));
            }
        }));

        findViewById(R.id.add_group_button).setOnClickListener(view -> {
            adapter.addGroup();
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPosition(0);
            }
        });

        findViewById(R.id.back_button).setOnClickListener((view) -> finish());

        if (!demo) {
            runUDP = true;
            if (udpListenerThread.getState() != Thread.State.NEW) {
                udpListenerThread.start();
            }
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            View frameLayout = findViewById(R.id.fullscreen_frame);
            frameLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
                ConstraintLayout meterInfo = findViewById(R.id.mix_meter_layout);
                ConstraintLayout mixInfo = findViewById(R.id.mix_info_layout);
                ViewGroup.LayoutParams mixMeterParams = meterInfo.getLayoutParams();
                ViewGroup.LayoutParams mixInfoParams = mixInfo.getLayoutParams();
                if (cutout != null) {
                    final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
                    int pixels = (int) (width * scale + 0.5f);
                    Handler handler = new Handler(Looper.getMainLooper());
                    if (cutout.getSafeInsetLeft() != mixMeterParams.width) {
                        mixMeterParams.width = cutout.getSafeInsetLeft();
                        if (mixMeterParams.width == 0) {
                            mixMeterParams.width = pixels;
                        }
                        handler.post(() -> meterInfo.setLayoutParams(mixMeterParams));
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

    private void hideUI() {
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        runUDP = false;
        channelLayer = adapter.getChannelMap();
        saveMap(channelLayer);
        saveLayout(adapter.getLayout());
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
        OSCMessage message = new OSCMessage("/" + MIX + currentMix + "/" + CHANNEL + fader, arguments);
        Log.d("OSC", message.getAddress() + " " + message.getArguments().get(0).toString());
        connectionService.send(message);
    }

    public void SendOSCChannelMute(int channel, boolean muted) {
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(muted ? 1 : 0);
        OSCMessage message = new OSCMessage("/" + MIX + currentMix + "/" + CHANNEL + channel + "/" + MUTE, arguments);
        Log.d("OSC", message.getAddress() + " " + message.getArguments().get(0).toString());
        connectionService.send(message);
    }

    public void SendOSCGetMix(int mix) {
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(1);
        OSCMessage message = new OSCMessage("/" + MIX + mix, arguments);
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

    private void saveMap(HashMap<Integer, Integer> inputMap) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences != null) {
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<Integer, Integer> inputEntry : inputMap.entrySet()) {
                try {
                    jsonObject.put(inputEntry.getKey().toString(), inputEntry.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            String jsonString = jsonObject.toString();
            preferences.edit()
                    .remove("channel_layer")
                    .putString("channel_layer", jsonString)
                    .apply();
        }
    }

    private void saveLayout(HashMap<Integer, Object> inputLayout) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences != null) {
            JSONObject json = new JSONObject();
            for (Map.Entry<Integer, Object> inputEntry : inputLayout.entrySet()) {
                try {
                    if (inputEntry.getValue() instanceof Group group) {
                        json.put(inputEntry.getKey().toString(), new JSONObject(group.toMap()));
                    } else {
                        json.put(inputEntry.getKey().toString(), inputEntry.getValue());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            String jsonString = json.toString();
            preferences.edit()
                    .remove(JSON_CHANNEL_LAYOUT)
                    .putString(JSON_CHANNEL_LAYOUT, jsonString)
                    .apply();
        }
    }

    private HashMap<Integer, Object> loadLayout() {
        HashMap<Integer, Object> outputMap = new HashMap<>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences != null) {
            String jsonString = preferences.getString(JSON_CHANNEL_LAYOUT, (new JSONObject()).toString());
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
            Type mapType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> map = gson.fromJson(jsonString, mapType);
            for (String key : map.keySet()) {
                Object value = map.get(key);
                outputMap.put(Integer.valueOf(key), value);
            }
        }
        return outputMap;
    }

    private HashMap<Integer, Integer> loadMap() {
        HashMap<Integer, Integer> outputMap = new HashMap<>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        try {
            if (preferences != null) {
                String jsonString = preferences.getString("channel_layer", (new JSONObject()).toString());
                JSONObject jsonObject = new JSONObject(jsonString);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Integer value = jsonObject.getInt(key);
                    outputMap.put(Integer.valueOf(key), value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return outputMap;
    }

}