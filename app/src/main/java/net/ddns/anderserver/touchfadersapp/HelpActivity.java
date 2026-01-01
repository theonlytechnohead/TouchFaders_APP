package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import net.ddns.anderserver.touchfadersapp.custom.BoxedVertical;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class HelpActivity extends AppCompatActivity implements ItemMoveCallback.StartDragListener {

    RecyclerView recyclerView;
    ChannelsRecyclerViewAdapter adapter;
    ItemTouchHelper touchHelper;

    Context instanceContext;
    BoxedVertical mixMeter;
    ConstraintLayout mixInfo;
    TextView mixName;
    TextView mixNumber;

    private int numChannels;
    private ArrayList<Integer> channelColours;
    private HashMap<Integer, Integer> channelLayer;
    private int currentMix;
    private Integer mixColour;

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(i -> hideUI());

        setContentView(R.layout.help);

        mixMeter = findViewById(R.id.mixMeter);
        mixMeter.setTouchDisabled(true);

        mixInfo = findViewById(R.id.mix_info_layout);
        mixName = findViewById(R.id.mix_name);
        mixNumber = findViewById(R.id.mix_number);

        instanceContext = this;

        numChannels = 1;
        channelColours = new ArrayList<>();
        channelColours.add(0);
        currentMix = 1;
        mixColour = 1;
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
    public void requestDrag(RecyclerView.ViewHolder viewHolder) {
        touchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onStart() {
        super.onStart();
        float width = 70;
        channelLayer = loadMap();
        recyclerView = findViewById(R.id.faderRecyclerView);
        adapter = new ChannelsRecyclerViewAdapter(this, instanceContext, recyclerView, numChannels, channelLayer, loadLayout(), channelColours, width);
        adapter.setValuesChangeListener((index, points) -> {
        });
        adapter.setFaderMuteListener(((view, index, muted) -> {
        }));
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

    @Override
    protected void onResume() {
        super.onResume();
        hideUI();

        mixInfo.setBackgroundColor(getResources().getIntArray(R.array.mixer_colours)[mixColour]);
        mixName.setText("MX\n \n1");
        mixNumber.setText("1");
        mixNumber.setTextColor(getResources().getIntArray(R.array.mixer_colours_lighter)[currentMix]);

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
                    int pixels = (int) (200 * scale + 0.5f);
                    Handler handler = new Handler(Looper.getMainLooper());
                    if (cutout.getSafeInsetLeft() != mixMeterParams.width + pixels) {
                        mixMeterParams.width = cutout.getSafeInsetLeft() + pixels;
                        if (mixMeterParams.width == 0) {
                            mixMeterParams.width = pixels;
                        }
                        meterInfo.setPadding(cutout.getSafeInsetLeft(), 0, 0, 0);
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

    @Override
    protected void onPause() {
        super.onPause();
        channelLayer = adapter.getChannelMap();
        saveMap(channelLayer);
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
                    json.put(inputEntry.getKey().toString(), inputEntry.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            String jsonString = json.toString();
            preferences.edit()
                    .remove("channel_layout")
                    .putString("channel_layout", jsonString)
                    .apply();
        }
    }

    private HashMap<Integer, Object> loadLayout() {
        HashMap<Integer, Object> outputMap = new HashMap<>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        try {
            if (preferences != null) {
                String jsonString = preferences.getString("channel_layout", (new JSONObject()).toString());
                JSONObject json = new JSONObject(jsonString);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = json.get(key);
                    outputMap.put(Integer.valueOf(key), value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
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