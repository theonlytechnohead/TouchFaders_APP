package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class ChannelStripRecyclerViewAdapter extends RecyclerView.Adapter<ChannelStripRecyclerViewAdapter.ChannelStripViewHolder> implements ItemMoveCallback.ItemTouchHelperContract {

    private final Context context;

    private final ArrayList<ChannelStrip> channels = new ArrayList<>();
    private final HashMap<Integer, ChannelStrip> hiddenChannels = new HashMap<>();
    private boolean hidden;
    private final float width;

    private FaderValueChangedListener faderValueChangedListener;
    private ChannelMuteListener channelMuteListener;
    private final ItemMoveCallback.StartDragListener startDragListener;


    public ChannelStripRecyclerViewAdapter(ItemMoveCallback.StartDragListener startDragListener, Context context, int numChannels, HashMap<Integer, Integer> channelLayer, ArrayList<Integer> channelColours, float width) {
        this.context = context;
        this.startDragListener = startDragListener;
        int[] colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        int[] colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        this.hidden = false;
        this.width = width;
        TreeMap<Integer, ChannelStrip> channelLayout = new TreeMap<>();
        for (int i = 0; i < numChannels; i++) {
            ChannelStrip channel = new ChannelStrip();
            channel.index = i;

            channel.level = 623;
            channel.muted = false;
            channel.name = "CH " + (i + 1);
            channel.patch = "IN " + (i + 1);
            channel.colour = colourArray[channelColours.get(i)];
            channel.colourLighter = colourArrayLighter[channelColours.get(i)];

            channelLayout.put(channelLayer.getOrDefault(i, i), channel);
        }
        for (Map.Entry<Integer, ChannelStrip> entry : channelLayout.entrySet()) {
            channels.add(entry.getValue());
            notifyItemInserted(entry.getKey());
        }
    }

    @NonNull
    @Override
    public ChannelStripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_channel_strip, parent, false);
        return new ChannelStripViewHolder(view);
    }

    // https://stackoverflow.com/questions/5300962/getviewtypecount-and-getitemviewtype-methods-of-arrayadapter
    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    // Gets called every time a ViewHolder is reused (with a new position)
    @Override
    public void onBindViewHolder(@NonNull ChannelStripViewHolder holder, int position) {
        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
        holder.position = channelStrip.index;
        holder.fader.setValue(channelStrip.level);
        holder.fader.setGradientEnd(channelStrip.colour);
        holder.fader.setGradientStart(channelStrip.colourLighter);
        holder.fader.setMute(channelStrip.muted);
        final float scale = context.getResources().getDisplayMetrics().density;
        int pixels = (int) (width * scale + 0.5f);
        ViewGroup.LayoutParams faderParams = holder.fader.getLayoutParams();
        faderParams.width = pixels;
        holder.fader.setLayoutParams(faderParams);
        String number = String.valueOf(channelStrip.index + 1);
        holder.channelNumber.setText(number);
        holder.channelName.setText(channelStrip.name);
        holder.channelPatch.setText(channelStrip.patch);
        holder.channelName.setBackgroundColor(channelStrip.colour);
        if ((holder.getAdapterPosition() / 8) % 2 == 0) {
            if (holder.getAdapterPosition() % 2 == 0)
                holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_even));
            else
                holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_odd));
        } else {
            if (holder.getAdapterPosition() % 2 == 0)
                holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_even));
            else
                holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_odd));
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    @Override
    public void onChannelMoved(int from, int to) {
        // everybody do the swap!
        if (from < to) {
            for (int i = from; i < to; i++) {
                swapChannel(i, i + 1);
                notifyItemMoved(i, i + 1);
                notifyItemChanged(i);
            }
        } else {
            for (int i = from; i > to; i--) {
                swapChannel(i, i - 1);
                notifyItemMoved(i, i - 1);
                notifyItemChanged(i);
            }
        }
    }

    void swapChannel(int from, int to) {
        Collections.swap(channels, from, to);
    }

    @Override
    public void onChannelSelected(ChannelStripViewHolder channelStripViewHolder) {
        channelStripViewHolder.faderBackground.setBackgroundColor(channels.get(channelStripViewHolder.getAdapterPosition()).colourLighter);
    }

    @Override
    public void onChannelClear(ChannelStripViewHolder channelStripViewHolder) {
        if ((channelStripViewHolder.getAdapterPosition() / 8) % 2 == 0) {
            if (channelStripViewHolder.getAdapterPosition() % 2 == 0)
                channelStripViewHolder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_even));
            else
                channelStripViewHolder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_odd));
        } else {
            if (channelStripViewHolder.getAdapterPosition() % 2 == 0)
                channelStripViewHolder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_even));
            else
                channelStripViewHolder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_odd));
        }
    }

    public class ChannelStripViewHolder extends RecyclerView.ViewHolder implements ChannelStripRecyclerViewAdapter.FaderValueChangedListener {

        int position;
        ConstraintLayout faderBackground;
        ConstraintLayout channelBackground;
        BoxedVertical fader;
        TextView channelNumber;
        TextView channelPatch;
        TextView channelName;

        @SuppressLint("ClickableViewAccessibility")
        ChannelStripViewHolder(View itemView) {
            super(itemView);
            ChannelStripViewHolder holder = this;
            faderBackground = itemView.findViewById(R.id.faderBackground);
            channelBackground = itemView.findViewById(R.id.stripLayout);
            fader = itemView.findViewById(R.id.fader);
            fader.setOnBoxedPointsChangeListener((boxedPoints, points) -> {
                int index = channels.get(holder.getAdapterPosition()).index;
                channels.get(holder.getAdapterPosition()).level = points;
                faderValueChangedListener.onValueChanged(boxedPoints.getRootView(), index, boxedPoints, points);
            });
            channelBackground.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
                        int index = channelStrip.index;
                        channels.get(holder.getAdapterPosition()).muted = !channelStrip.muted;
                        fader.setMute(channelStrip.muted);
                        notifyItemChanged(holder.getAdapterPosition());
                        channelMuteListener.onChannelMuteChange(itemView, index, channelStrip.muted);
                        return super.onDoubleTap(e);
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        startDragListener.requestDrag(holder);
                        super.onLongPress(e);
                    }
                });

                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    gestureDetector.onTouchEvent(motionEvent);
                    return true;
                }
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
        }

        @Override
        public void onValueChanged(View view, int index, BoxedVertical boxedVertical, int points) {
            if (faderValueChangedListener != null)
                faderValueChangedListener.onValueChanged(view, index, boxedVertical, points);
        }
    }

    public void toggleChannelHide() {
        hidden = !hidden;
        if (hidden) {
            for (int i = channels.size() - 1; 0 <= i; i--) {
                ChannelStrip channelStrip = channels.get(i);
                if (channelStrip.level == 0) {
                    hiddenChannels.put(i, channelStrip);
                    channels.remove(i);
                    notifyItemRemoved(i);
                }
            }
        } else {
            if (hiddenChannels.size() > 0) {
                for (Map.Entry<Integer, ChannelStrip> entry : hiddenChannels.entrySet()) {
                    int i = entry.getKey();
                    ChannelStrip channelStrip = entry.getValue();
                    channels.add(i, channelStrip);
                    notifyItemInserted(channels.indexOf(channelStrip));
                }
                hiddenChannels.clear();
            }
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < channels.size(); i++) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int index = i;
                handler.post(() -> notifyItemChanged(index));
            }
        });
    }

    int getIndex(int channelIndex) {
        if (hiddenChannels.containsKey(channelIndex)) {
            return -channelIndex;
        }
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).index == channelIndex) {
                return i;
            }
        }
        return 0;
    }

    void setFaderLevel(int index, int level) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).level = level;
        } else {
            channels.get(channelIndex).level = level;
            notifyItemChanged(channelIndex);
        }
    }

    void setChannelPatchIn(int index, String patchIn) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).patch = patchIn;
        } else {
            channels.get(channelIndex).patch = patchIn;
            notifyItemChanged(channelIndex);
        }
    }

    void setChannelName(int index, String name) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).name = name;
        } else {
            channels.get(channelIndex).name = name;
            notifyItemChanged(channelIndex);
        }
    }

    void setChannelMute(int index, boolean muted) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).muted = muted;
        } else {
            channels.get(channelIndex).muted = muted;
            notifyItemChanged(channelIndex);
        }
    }

    public boolean getHidden() {
        return hidden;
    }

    public HashMap<Integer, Integer> getChannelMap() {
        HashMap<Integer, Integer> channelMap = new HashMap<>();
        ArrayList<ChannelStrip> allChannels = channels;
        for (Map.Entry<Integer, ChannelStrip> entry : hiddenChannels.entrySet()) {
            ChannelStrip channelStrip = entry.getValue();
            allChannels.add(entry.getKey(), channelStrip);
        }
        for (int i = 0; i < allChannels.size(); i++) {
            ChannelStrip channelStrip = allChannels.get(i);
            channelMap.put(channelStrip.index, i);
        }
        return channelMap;
    }

    void setValuesChangeListener(FaderValueChangedListener listener) {
        faderValueChangedListener = listener;
    }

    public interface FaderValueChangedListener {
        void onValueChanged(View view, int index, BoxedVertical boxedVertical, int points);
    }

    void setFaderMuteListener(ChannelMuteListener listener) {
        channelMuteListener = listener;
    }

    public interface ChannelMuteListener {
        void onChannelMuteChange(View view, int index, boolean muted);
    }
}
