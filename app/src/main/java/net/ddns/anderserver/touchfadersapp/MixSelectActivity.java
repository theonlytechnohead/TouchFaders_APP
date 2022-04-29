package net.ddns.anderserver.touchfadersapp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MixSelectActivity extends AppCompatActivity implements MixSelectRecyclerViewAdapter.MixButtonClickListener {

    public static String EXTRA_MIX_INDEX = "EXTRA_MIX_INDEX";

    MixSelectRecyclerViewAdapter adapter;

    ConnectionService connectionService;
    Boolean bound = false;
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            connectionService = ((ConnectionService.ConnectionBinder) iBinder).getService();
            bound = true;

            Log.i("MIX", "Service connected");
            if (connectionService.state() == ConnectionService.states.RUNNING)
                connectionService.deselectMix();

            // Generate mix names
//            ArrayList<String> mixNames = new ArrayList<>();
//            for (int i = 1; i <= connectionService.mixes(); i++) mixNames.add("Mix " + i);

            List<String> mixNames = connectionService.mixNames();

            // Assign mix colours
            ArrayList<Integer> mixColours = new ArrayList<>();
            for (int i = 0; i < connectionService.mixes(); i++) {
                Integer mixColour = connectionService.mixColours().get(i);
                switch (mixColour) {
                    default:
                        mixColours.add(getColor(R.color.mixer_blue));
                        break;
                    case 1:
                        mixColours.add(getColor(R.color.mixer_orange));
                        break;
                    case 2:
                        mixColours.add(getColor(R.color.mixer_brown));
                        break;
                    case 3:
                        mixColours.add(getColor(R.color.mixer_purple));
                        break;
                    case 4:
                        mixColours.add(getColor(R.color.mixer_cyan));
                        break;
                    case 5:
                        mixColours.add(getColor(R.color.mixer_pink));
                        break;
                    case 6:
                        mixColours.add(getColor(R.color.mixer_bergundy));
                        break;
                    case 7:
                        mixColours.add(getColor(R.color.mixer_green));
                        break;
                }
            }

            // set up the RecyclerView
            RecyclerView recyclerView = findViewById(R.id.mix_select_recyclerview);
            adapter = new MixSelectRecyclerViewAdapter(getApplicationContext(), mixNames, mixColours);
            adapter.setClickListener(MixSelectActivity.this);
            recyclerView.setAdapter(adapter);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mix_selection);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent, connection, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Making it fullscreen...
        View mixLayout = findViewById(R.id.mix_select_layout);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mixLayout.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        // Fullscreen done!

        if (connectionService != null) {
            if (connectionService.state() == ConnectionService.states.RUNNING)
                connectionService.deselectMix();
        }
    }

    @Override
    public void onItemClick(View view, int index) {
        int mix = index + 1;
        connectionService.selectedMix(mix);
        //Toast.makeText(this, "You clicked " + adapter.getItem(index) + " which is mix " + mix, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

}
