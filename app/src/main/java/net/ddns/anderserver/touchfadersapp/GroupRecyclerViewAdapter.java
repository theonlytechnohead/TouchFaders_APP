package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import net.ddns.anderserver.touchfadersapp.custom.BoxedVertical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class GroupRecyclerViewAdapter extends RecyclerView.Adapter<GroupRecyclerViewAdapter.ChannelViewHolder> implements ItemMoveCallback.ItemTouchHelperContract {

    private final Context context;

    private ArrayList<ChannelStrip> channels = new ArrayList<>();
    private final HashMap<Integer, ChannelStrip> hiddenChannels = new HashMap<>();
    private final float width;

    private int colourIndex;

    private ChannelsRecyclerViewAdapter.FaderValueChangedListener faderValueChangedListener;
    private ChannelsRecyclerViewAdapter.ChannelMuteListener channelMuteListener;
    private final ItemMoveCallback.StartDragListener startDragListener;

    final int[] colourArray;
    final int[] colourArrayLighter;
    final int[] colourArrayDarker;
    final int darkGreyColour;
    final int greyColour;
    final int whiteColour;

    public GroupRecyclerViewAdapter(Context context, float width, ItemMoveCallback.StartDragListener startDragListener) {
        super();
        this.context = context;
        this.width = width;
        this.startDragListener = startDragListener;

        this.colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        this.colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        this.colourArrayDarker = context.getResources().getIntArray(R.array.mixer_colours_darker);
        this.darkGreyColour = context.getColor(R.color.dark_grey);
        this.greyColour = context.getColor(R.color.grey);
        this.whiteColour = context.getColor(R.color.white);
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_channel_strip, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
        holder.position = channelStrip.index;
        holder.fader.setValue(channelStrip.level);
        holder.fader.setGradientEnd(channelStrip.colour);
        holder.fader.setGradientStart(channelStrip.colourLighter);
        holder.fader.setMute(channelStrip.sendMuted);
        String number = String.valueOf(channelStrip.index + 1);
        holder.channelNumber.setText(number);
        holder.channelName.setText(channelStrip.name);
        holder.channelPatch.setText(channelStrip.patch);
        holder.channelName.setBackgroundColor(channelStrip.colour);
        if (channelStrip.channelMuted) {
            // whole channel is muted
            holder.channelNumber.setTextColor(greyColour);
            holder.channelPatch.setTextColor(whiteColour);
            holder.channelBackground.setBackgroundColor(darkGreyColour);
        } else {
            // whole channel is on
            holder.channelNumber.setTextColor(colourArray[colourIndex]);
            holder.channelPatch.setTextColor(colourArrayDarker[colourIndex]);
            holder.channelBackground.setBackgroundColor(colourArrayLighter[colourIndex]);
        }
        if (channelStrip.sendMuted) {
            holder.faderBackground.setBackgroundColor(greyColour);
        } else {
            holder.faderBackground.setBackgroundColor(colourArray[colourIndex]);
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    @Override
    public void onChannelMoved(int from, int to) {
        // everybody do the swap!
        if (from < to) {
            for (int i = from; i < to; i++) {
                swapChannel(i, i + 1);
                notifyItemMoved(i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                swapChannel(i, i - 1);
                notifyItemMoved(i, i - 1);
            }
        }
    }

    void swapChannel(int from, int to) {
        Collections.swap(channels, from, to);
    }

    @Override
    public void onChannelSelected(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ChannelViewHolder channelViewHolder)
            channelViewHolder.faderBackground.setBackgroundColor(channels.get(viewHolder.getAdapterPosition()).colourLighter);
    }

    @Override
    public void onChannelClear(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ChannelViewHolder channelViewHolder) {
            if (channels.get(channelViewHolder.getAdapterPosition()).sendMuted) {
                channelViewHolder.faderBackground.setBackgroundColor(greyColour);
            } else {
                channelViewHolder.faderBackground.setBackgroundColor(colourArray[colourIndex]);
            }
        }
    }

    public class ChannelViewHolder extends RecyclerView.ViewHolder {

        int position;
        final ConstraintLayout faderBackground;
        final ConstraintLayout channelBackground;
        final BoxedVertical fader;
        final TextView channelNumber;
        final TextView channelPatch;
        final TextView channelName;

        @SuppressLint("ClickableViewAccessibility")
        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            ChannelViewHolder holder = this;
            faderBackground = itemView.findViewById(R.id.faderBackground);
            final float scale = context.getResources().getDisplayMetrics().density;
            int pixels = (int) (width * scale + 0.5f);
            ViewGroup.LayoutParams faderParams = holder.faderBackground.getLayoutParams();
            faderParams.width = pixels;
            holder.faderBackground.setLayoutParams(faderParams);
            channelBackground = itemView.findViewById(R.id.stripLayout);
            fader = itemView.findViewById(R.id.fader);
            fader.setOnBoxedPointsChangeListener((boxedPoints, points) -> {
                int index = channels.get(holder.getAdapterPosition()).index;
                channels.get(holder.getAdapterPosition()).level = points;
                if (faderValueChangedListener != null)
                    faderValueChangedListener.onValueChanged(index, points);
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
            channelBackground.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
                        int index = channelStrip.index;
                        channels.get(holder.getAdapterPosition()).sendMuted = !channelStrip.sendMuted;
                        fader.setMute(channelStrip.sendMuted);
                        notifyItemChanged(holder.getAdapterPosition());
                        if (!channelStrip.group) {
                            if (channelMuteListener != null)
                                channelMuteListener.onChannelMuteChange(itemView, index, channelStrip.sendMuted);
                        }
                        // TODO: mute sub-channels
                        return super.onDoubleTap(e);
                    }

                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (startDragListener != null)
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
        }
    }

    public void setColourByIndex(int index) {
        this.colourIndex = index;
        for (int i = 0; i < channels.size(); i++) {
            notifyItemChanged(i);
        }
    }

    public void setChannels(ArrayList<ChannelStrip> channels) {
        if (channels != null) {
            this.channels = new ArrayList<>(channels);
            notifyItemRangeInserted(0, channels.size());
        } else {
            notifyItemRangeRemoved(0, this.channels.size());
            this.channels.clear();
        }
    }

    public void setFaderValueChangedListener(ChannelsRecyclerViewAdapter.FaderValueChangedListener listener) {
        faderValueChangedListener = listener;
    }

}
