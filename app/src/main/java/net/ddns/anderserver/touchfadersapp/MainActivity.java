package net.ddns.anderserver.touchfadersapp;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.illposed.osc.OSCBadDataEvent;
import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCPacketEvent;
import com.illposed.osc.OSCPacketListener;
import com.illposed.osc.transport.udp.OSCPortIn;
import com.illposed.osc.transport.udp.OSCPortOut;
import com.lukelorusso.verticalseekbar.VerticalSeekBar;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import kotlin.Unit;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

	SharedPreferences sharedPreferences;

	OSCPortOut oscPortOut;
	OSCPortIn oscPortIn;
	ArrayList<VerticalSeekBar> faders = new ArrayList<>();

	OSCPacketListener packetListener = new OSCPacketListener() {
		@Override
		public void handlePacket (OSCPacketEvent event) {
			OSCPacket packet = event.getPacket();
			if (packet instanceof OSCMessage) {
				OSCMessage message = (OSCMessage) packet;
				if (message.getAddress().contains("/mix1/fader")) {
					String[] segments = message.getAddress().split("/");
					int faderIndex = Integer.parseInt(segments[2].replaceAll("\\D+", "")) - 1; // extract only digits via RegEx
					if (0 <= faderIndex && faderIndex < 16) {
						VerticalSeekBar fader = faders.get(faderIndex);
						fader.setOnProgressChangeListener((level) -> null);
						fader.setProgress((int) message.getArguments().get(0));
						fader.setOnProgressChangeListener((level) -> faderProgressChangeListener(fader, level));
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
		public void handleBadData (OSCBadDataEvent event) {
			Log.d("OSC message", "Bad packet received");
		}
	};

	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//BasicConfigurator.configure();

		setContentView(R.layout.main);

		AsyncTask.execute(this::OpenOSCPortIn);

		AsyncTask.execute(this::OpenOSCPortOut);

		AsyncTask.execute(this::setupFaders);

		Button mix1_button = findViewById(R.id.mix1_button);
		mix1_button.setOnClickListener(v -> SendOSCGetMix(1));
	}

	@Override
	protected void onResume () {
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

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
			frameLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
				LinearLayout faderLayout = findViewById(R.id.faderLayout);
				ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) faderLayout.getLayoutParams();
				assert cutout != null;
				if (cutout.getSafeInsetLeft() == layoutParams.leftMargin) return;
				layoutParams.leftMargin = cutout.getSafeInsetLeft();
				layoutParams.rightMargin = cutout.getSafeInsetRight();
				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(() -> faderLayout.setLayoutParams(layoutParams));
				//Log.i("CUTOUT", "safeLeft: " + cutout.getSafeInsetLeft() + "  safeRight: " + cutout.getSafeInsetRight());
			});
		}
	}

	private String GetLocalIP () {
		try {
			String localAddress = "";
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
				NetworkInterface networkInterface = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLinkLocalAddress()) {
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

	private void OpenOSCPortIn () {
		Handler handler = new Handler(Looper.getMainLooper());
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		String localAddress = GetLocalIP();
		int oscReceivePort = sharedPreferences.getInt("receivePort", 9001);
		SocketAddress receiveSocket = new InetSocketAddress(localAddress, oscReceivePort);

		try {
			oscPortIn = new OSCPortIn(receiveSocket);
		} catch (BindException e) {
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

	private void OpenOSCPortOut () {
		Handler handler = new Handler(Looper.getMainLooper());
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		String oscIP = sharedPreferences.getString("ipAddress", "192.168.1.2");
		int oscSendPort = sharedPreferences.getInt("sendPort", 8001);
		SocketAddress sendSocket = new InetSocketAddress(oscIP, oscSendPort);

		try {
			oscPortOut = new OSCPortOut(sendSocket);
		} catch (Exception e) {
			handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show());
		}
	}

	public void setupFaders () {
		faders.add(findViewById(R.id.fader1));
		faders.add(findViewById(R.id.fader2));
		faders.add(findViewById(R.id.fader3));
		faders.add(findViewById(R.id.fader4));
		faders.add(findViewById(R.id.fader5));
		faders.add(findViewById(R.id.fader6));
		faders.add(findViewById(R.id.fader7));
		faders.add(findViewById(R.id.fader8));
		faders.add(findViewById(R.id.fader9));
		faders.add(findViewById(R.id.fader10));
		faders.add(findViewById(R.id.fader11));
		faders.add(findViewById(R.id.fader12));
		faders.add(findViewById(R.id.fader13));
		faders.add(findViewById(R.id.fader14));
		faders.add(findViewById(R.id.fader15));
		faders.add(findViewById(R.id.fader16));


		for (VerticalSeekBar fader : faders) {
			fader.setOnProgressChangeListener(level -> faderProgressChangeListener(fader, level));
		}
	}

	Unit faderProgressChangeListener (VerticalSeekBar fader, int level) {
		SendOSCFaderValue(faders.indexOf(fader) + 1, level);
		return null;
	}

	public void SendOSCFaderValue (int fader, int faderValue) {
		AsyncTask.execute(() -> {
			Handler handler = new Handler(Looper.getMainLooper());

			if (oscPortOut != null) {
				ArrayList<Object> arguments = new ArrayList<>();
				arguments.add(faderValue);
				OSCMessage message = new OSCMessage("/mix1/fader" + fader, arguments);

				try {
					oscPortOut.send(message);
					Thread.sleep(5);
					//Log.i("OSC message", message.getAddress() + " : " + message.getArguments().get(0).toString());
				} catch (Exception e) {
					handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show());
				}
			}
		});
	}

	public void SendOSCGetMix (int mix) {
		AsyncTask.execute(() -> {
			Handler handler = new Handler(Looper.getMainLooper());

			if (oscPortOut != null) {
				ArrayList<Object> arguments = new ArrayList<>();
				arguments.add(1);
				OSCMessage message = new OSCMessage("/mix" + mix, arguments);

				try {
					oscPortOut.send(message);
					//Log.i("OSC message", message.getAddress() + " : " + message.getArguments().get(0).toString());
				} catch (Exception e) {
					handler.post(() -> Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show());
				}
			}
		});
	}

}