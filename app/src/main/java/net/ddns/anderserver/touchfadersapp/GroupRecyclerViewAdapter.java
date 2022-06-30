package net.ddns.anderserver.touchfadersapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

public class GroupRecyclerViewAdapter extends RecyclerView.Adapter<GroupRecyclerViewAdapter.GroupViewHolder> {

    private final Context context;

    private ArrayList<ChannelStrip> channels = new ArrayList<>();
    private final HashMap<Integer, ChannelStrip> hiddenChannels = new HashMap<>();
    private boolean hidden;
    private final float width;

    private int colourIndex;

    private ChannelStripRecyclerViewAdapter.FaderValueChangedListener faderValueChangedListener;
    private ChannelStripRecyclerViewAdapter.ChannelMuteListener channelMuteListener;
    private final ItemMoveCallback.StartDragListener startDragListener;

    int[] colourArray;
    int[] colourArrayLighter;
    int[] colourArrayDarker;
    int darkGreyColour;
    int greyColour;
    int whiteColour;

    public GroupRecyclerViewAdapter(Context context, float width, ItemMoveCallback.StartDragListener startDragListener) {
        super();
        this.context = context;
        this.width = width;
        this.startDragListener = startDragListener;
        this.hidden = false;
//        this.group = group;

        colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        colourArrayDarker = context.getResources().getIntArray(R.array.mixer_colours_darker);
        darkGreyColour = context.getColor(R.color.dark_grey);
        greyColour = context.getColor(R.color.grey);
        whiteColour = context.getColor(R.color.white);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_channel_strip, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
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
        String number = String.valueOf(channelStrip.index + 1);
        holder.channelNumber.setText(number);
        holder.channelName.setText(channelStrip.name);
        holder.channelPatch.setText(channelStrip.patch);
        holder.channelName.setBackgroundColor(channelStrip.colour);
        if (channelStrip.channelMuted) {
            holder.channelNumber.setTextColor(greyColour);
            holder.channelPatch.setTextColor(whiteColour);
            holder.channelBackground.setBackgroundColor(darkGreyColour);
        } else {
            holder.channelNumber.setTextColor(colourArray[colourIndex]);
            holder.channelPatch.setTextColor(colourArrayDarker[colourIndex]);
            holder.channelBackground.setBackgroundColor(colourArrayLighter[colourIndex]);
        }
        holder.faderBackground.setBackgroundColor(colourArrayDarker[colourIndex]);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    public class GroupViewHolder extends RecyclerView.ViewHolder {

        int position;
        ConstraintLayout faderBackground;
        ConstraintLayout channelBackground;
        BoxedVertical fader;
        TextView channelNumber;
        TextView channelPatch;
        TextView channelName;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            GroupViewHolder holder = this;
            faderBackground = itemView.findViewById(R.id.faderBackground);
            channelBackground = itemView.findViewById(R.id.stripLayout);
            fader = itemView.findViewById(R.id.fader);
            fader.setOnBoxedPointsChangeListener((boxedPoints, points) -> {
                int index = channels.get(holder.getAdapterPosition()).index;
                channels.get(holder.getAdapterPosition()).level = points;
                faderValueChangedListener.onValueChanged(boxedPoints.getRootView(), index, boxedPoints, points);
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
        }
    }

    public void setColourIndex(int index) {
        this.colourIndex = index;
    }

    public void setChannels(ArrayList<ChannelStrip> channels) {
        // TODO: figure out correct order to add?
        this.channels = channels;
        notifyItemRangeInserted(0, channels.size());
    }

}
