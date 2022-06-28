package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
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

    int[] colourArray;
    int[] colourArrayLighter;
    int[] colourArrayDarker;
    int darkGreyColour;
    int greyColour;
    int whiteColour;

    int groups = 0;

    public ChannelStripRecyclerViewAdapter(ItemMoveCallback.StartDragListener startDragListener, Context context, int numChannels, HashMap<Integer, Integer> channelLayer, ArrayList<Integer> channelColours, float width) {
        this.context = context;
        this.startDragListener = startDragListener;
        colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        colourArrayDarker = context.getResources().getIntArray(R.array.mixer_colours_darker);
        darkGreyColour = context.getColor(R.color.dark_grey);
        greyColour = context.getColor(R.color.grey);
        whiteColour = context.getColor(R.color.white);
        this.hidden = false;
        this.width = width;
        TreeMap<Integer, ChannelStrip> channelLayout = new TreeMap<>();
        for (int i = 0; i < numChannels; i++) {
            ChannelStrip channel = new ChannelStrip();
            channel.index = i;

            channel.level = 0;
            channel.sendMuted = false;
            channel.name = "";
            channel.patch = "";
            channel.colourIndex = channelColours.get(i);
            channel.colour = colourArray[channel.colourIndex];
            channel.colourLighter = colourArrayLighter[channel.colourIndex];
            channel.colourDarker = colourArrayDarker[channel.colourIndex];

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
        holder.fader.setMute(channelStrip.sendMuted);
        final float scale = context.getResources().getDisplayMetrics().density;
        int pixels = (int) (width * scale + 0.5f);
        ViewGroup.LayoutParams faderParams = holder.fader.getLayoutParams();
        faderParams.width = pixels;
        holder.fader.setLayoutParams(faderParams);
        if (channelStrip.group) {
            holder.channelNumber.setVisibility(View.INVISIBLE);
        } else {
            holder.channelNumber.setVisibility(View.VISIBLE);
            String number = String.valueOf(channelStrip.index + 1);
            holder.channelNumber.setText(number);
        }
        holder.channelName.setText(channelStrip.name);
        holder.channelPatch.setText(channelStrip.patch);
        holder.channelName.setBackgroundColor(channelStrip.colour);
        if (channelStrip.channelMuted) {
            holder.channelNumber.setTextColor(greyColour);
            holder.channelPatch.setTextColor(whiteColour);
            holder.channelBackground.setBackgroundColor(darkGreyColour);
        } else {
            holder.channelNumber.setTextColor(channelStrip.colour);
            holder.channelPatch.setTextColor(channelStrip.colourDarker);
            holder.channelBackground.setBackgroundColor(channelStrip.colourLighter);
        }
        setBackgroundColour(holder);
    }

    private void setBackgroundColour (ChannelStripViewHolder holder) {
        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
        if (channelStrip.group) {
            holder.faderBackground.setBackgroundColor(channelStrip.colourDarker);
        } else {
            int position = holder.getAdapterPosition();
            position = position - groupsBefore(position);
            if ((position / 8) % 2 == 0) {
                if (position % 2 == 0)
                    holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_even));
                else
                    holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_light_odd));
            } else {
                if (position % 2 == 0)
                    holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_even));
                else
                    holder.faderBackground.setBackgroundColor(context.getColor(R.color.fader_dark_odd));
            }
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
        setBackgroundColour(channelStripViewHolder);
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
                if (!channels.get(holder.getAdapterPosition()).group)
                    faderValueChangedListener.onValueChanged(boxedPoints.getRootView(), index, boxedPoints, points);
                // TODO: update sub-channels
            });
            channelBackground.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
                        int index = channelStrip.index;
                        channels.get(holder.getAdapterPosition()).sendMuted = !channelStrip.sendMuted;
                        fader.setMute(channelStrip.sendMuted);
                        notifyItemChanged(holder.getAdapterPosition());
                        if (!channelStrip.group)
                            channelMuteListener.onChannelMuteChange(itemView, index, channelStrip.sendMuted);
                        // TODO: mute sub-channels
                        return super.onDoubleTap(e);
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        startDragListener.requestDrag(holder);
                        super.onLongPress(e);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
                        if (channelStrip.group) {
                            // TODO: trigger edit group
                            Log.i("GRP", "onSingleTapUp: " + channelStrip.index);
                            GroupEditDialog editDialog = new GroupEditDialog(channelStrip.index, channelStrip.name, channelStrip.colourIndex);
                            editDialog.setResultListener((dialogInterface, i) -> {
                                channelStrip.name = editDialog.name;
                                channelStrip.colourIndex = editDialog.colour;
                                channelStrip.colour = colourArray[channelStrip.colourIndex];
                                channelStrip.colourLighter = colourArrayLighter[channelStrip.colourIndex];
                                channelStrip.colourDarker = colourArrayDarker[channelStrip.colourIndex];
                                notifyItemChanged(holder.getAdapterPosition());
                            });
                            FragmentManager fragmentManager = ((FragmentActivity)context).getSupportFragmentManager();
                            editDialog.show(fragmentManager, "group edit dialog");
                        }
                        return super.onSingleTapConfirmed(e);
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

        refreshAllItems();
    }

    void refreshAllItems() {
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

    private int groupsBefore(int channelIndex) {
        int output = 0;
        for (int i = 0; i < channelIndex; i++) {
            if (channels.get(i).group)
                output++;
        }
        return output;
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

    void addGroup() {
        groups++;
        ChannelStrip channel = new ChannelStrip();
        channel.index = -groups;

        channel.level = 823;
        channel.sendMuted = false;
        channel.name = "GRP " + groups;
        channel.patch = "";
        channel.colourIndex = groups % colourArray.length;
        channel.colour = colourArray[channel.colourIndex];
        channel.colourLighter = colourArrayLighter[channel.colourIndex];
        channel.colourDarker = colourArrayDarker[channel.colourIndex];
        channel.group = true;
        channels.add(0, channel);
        notifyItemInserted(0);
    }

    void setChannelStrip (int index, int level, boolean sendMuted, String name, boolean channelMuted, String patch, int colourIndex) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).level = level;
            Objects.requireNonNull(hiddenChannels.get(index)).sendMuted = sendMuted;
            Objects.requireNonNull(hiddenChannels.get(index)).name = name;
            Objects.requireNonNull(hiddenChannels.get(index)).channelMuted = channelMuted;
            Objects.requireNonNull(hiddenChannels.get(index)).patch = patch;
            Objects.requireNonNull(hiddenChannels.get(index)).colour = colourArray[colourIndex];
            Objects.requireNonNull(hiddenChannels.get(index)).colourLighter = colourArrayLighter[colourIndex];
        } else {
            channels.get(channelIndex).level = level;
            channels.get(channelIndex).sendMuted = sendMuted;
            channels.get(channelIndex).name = name;
            channels.get(channelIndex).channelMuted = channelMuted;
            channels.get(channelIndex).patch = patch;
            channels.get(channelIndex).colour = colourArray[colourIndex];
            channels.get(channelIndex).colourLighter = colourArrayLighter[colourIndex];
            notifyItemChanged(channelIndex);
        }
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

    void setSendMute(int index, boolean muted) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).sendMuted = muted;
        } else {
            channels.get(channelIndex).sendMuted = muted;
            notifyItemChanged(channelIndex);
        }
    }

    void setChannelMute(int index, boolean muted) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).channelMuted = muted;
        } else {
            channels.get(channelIndex).channelMuted = muted;
            notifyItemChanged(channelIndex);
        }
    }

    void setChannelColour(int index, int colourIndex) {
        int channelIndex = getIndex(index);
        if (channelIndex < 0) {
            Objects.requireNonNull(hiddenChannels.get(index)).colour = colourArray[colourIndex];
            Objects.requireNonNull(hiddenChannels.get(index)).colourLighter = colourArrayLighter[colourIndex];
        } else {
            channels.get(channelIndex).colour = colourArray[colourIndex];
            channels.get(channelIndex).colourLighter = colourArrayLighter[colourIndex];
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
            if (!channelStrip.group)
                allChannels.add(entry.getKey(), channelStrip);
        }
        for (int i = 0; i < allChannels.size(); i++) {
            ChannelStrip channelStrip = allChannels.get(i);
            if (!channelStrip.group)
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
