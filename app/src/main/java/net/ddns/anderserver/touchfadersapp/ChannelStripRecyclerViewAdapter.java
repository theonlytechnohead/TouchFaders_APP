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
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class ChannelStripRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ItemMoveCallback.ItemTouchHelperContract {

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
    private final HashMap<Integer, ArrayList<ChannelStrip>> groupedChannels = new HashMap<>();

    public ChannelStripRecyclerViewAdapter(ItemMoveCallback.StartDragListener startDragListener, Context context, int numChannels, HashMap<Integer, Integer> channelLayer, HashMap<Integer, Object> layout, ArrayList<Integer> channelColours, float width) {
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
        for (Map.Entry<Integer, Object> entry : layout.entrySet()) {
//            Log.i("LAYOUT", entry.getKey() + " " + entry.getValue());
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_channel_strip, parent, false);
            return new ChannelStripViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.group, parent, false);
            return new SubChannelViewHolder(view);
        }
    }

    // https://stackoverflow.com/questions/5300962/getviewtypecount-and-getitemviewtype-methods-of-arrayadapter
    @Override
    public int getItemViewType(int position) {
        ChannelStrip channel = channels.get(position);
        if (channel.groupIndex != -1) return 2;
        return 1;
    }

    // Gets called every time a ViewHolder is reused (with a new position)
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder instanceof ChannelStripViewHolder) {
            ChannelStripViewHolder holder = (ChannelStripViewHolder) viewHolder;
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
        if (viewHolder instanceof SubChannelViewHolder) {
            SubChannelViewHolder holder = (SubChannelViewHolder) viewHolder;
            ChannelStrip subChannels = channels.get(viewHolder.getAdapterPosition());
            holder.adapter.setColourIndex(subChannels.colourIndex);
            ArrayList<ChannelStrip> grouped = groupedChannels.get(subChannels.groupIndex);
            if (!subChannels.hide) {
                holder.adapter.setChannels(grouped);
                holder.adapter.setFaderValueChangedListener((index, points) -> {
                    if (grouped != null) {
                        ChannelStrip group = getGroup(subChannels.groupIndex);
                        group.level = grouped.stream().mapToInt(channel -> channel.level).filter(channel -> channel >= 0).max().orElse(823);
                        subChannels.level = group.level;
                        notifyItemChanged(viewHolder.getAdapterPosition() - 1);
                    }
                });
            } else holder.adapter.setChannels(null);
        }
    }

    private void setBackgroundColour(ChannelStripViewHolder holder) {
        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
        if (channelStrip.group) {
            if (channelStrip.hide)
                holder.faderBackground.setBackgroundColor(channelStrip.colourDarker);
            else holder.faderBackground.setBackgroundColor(channelStrip.colour);
        } else if (channelStrip.groupIndex != -1) {
            ChannelStrip group = getGroup(channelStrip.groupIndex);
            holder.faderBackground.setBackgroundColor(group.colourDarker);
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
        ChannelStrip moving = channels.get(from);
        ChannelStrip target = channels.get(to);
        // everybody do the swap!
        if (from < to) {
            for (int i = from; i < to; i += 2) {
                if (moving.group) {
                    // move subchannels
                    ArrayList<ChannelStrip> subchannels = groupedChannels.get(-moving.index);
                    if (subchannels != null && 0 < subchannels.size() && !moving.hide) {
                        swapChannel(i + 1, i + 2);
                        notifyItemMoved(i + 1, i + 2);
                    }
                    // move group
                    swapChannel(i, i + 1);
                    notifyItemMoved(i, i + 1);
                    notifyItemChanged(i);
                } else {
                    swapChannel(i, i + 1);
                    notifyItemMoved(i, i + 1);
                    notifyItemChanged(i);
                }
                if (target.group) {
                    moveSubchannelsToGroup(-target.index);
                }
            }
        } else {
            for (int i = from; i > to; i--) {
                if (moving.group) {
                    // move group
                    swapChannel(i, i - 1);
                    notifyItemMoved(i, i - 1);
                    // move subchannels
                    ArrayList<ChannelStrip> subchannels = groupedChannels.get(-moving.index);
                    if (subchannels != null && 0 < subchannels.size() && !moving.hide) {
                        swapChannel(i + 1, i);
                        notifyItemMoved(i + 1, i);
                    }
                    notifyItemChanged(i);
                } else {
                    swapChannel(i, i - 1);
                    notifyItemMoved(i, i - 1);
                    notifyItemChanged(i);
                }
            }
        }
    }

    void swapChannel(int from, int to) {
        Collections.swap(channels, from, to);
    }

    void moveSubchannelsToGroup(int group) {
        int index = -1;
        int groupIndex = -1;
        for (int i = 0; i < channels.size(); i++) {
            ChannelStrip channel = channels.get(i);
            if (channel.group && channel.groupIndex == group) {
                index = i;
            }
            if (channel.group && channel.index == -group && channel.groupIndex == -1) {
                groupIndex = i;
            }
        }
        if (index != -1 && groupIndex != -1 && index != groupIndex + 1) {
            // move it all along?
            if (index < groupIndex) {
                for (int i = index; i < groupIndex; i++) {
                    swapChannel(i, i + 1);
                    notifyItemMoved(i, i + 1);
                }
            } else {
                for (int i = index; groupIndex + 1 < i; i--) {
                    swapChannel(i, i - 1);
                    notifyItemMoved(i, i - 1);
                }
            }
        }
    }

    @Override
    public void onChannelSelected(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ChannelStripViewHolder)
            ((ChannelStripViewHolder) viewHolder).faderBackground.setBackgroundColor(channels.get(viewHolder.getAdapterPosition()).colourLighter);
    }

    @Override
    public void onChannelClear(RecyclerView.ViewHolder viewHolder) {
        setBackgroundColour((ChannelStripViewHolder) viewHolder);
        ChannelStrip moving = channels.get(viewHolder.getAdapterPosition());
        if (moving.group)
            moveSubchannelsToGroup(-moving.index);
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
                ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
                int index = channelStrip.index;
                channelStrip.level = points;
                if (!channelStrip.group) {
                    if (faderValueChangedListener != null)
                        faderValueChangedListener.onValueChanged(index, points);
                } else {
                    int change = points - channels.get(holder.getAdapterPosition() + 1).level;
                    ArrayList<ChannelStrip> subchannels = groupedChannels.get(-channelStrip.index);
                    if (subchannels != null) {
                        for (ChannelStrip channel : subchannels) {
                            channel.level += change;
                            if (faderValueChangedListener != null)
                                faderValueChangedListener.onValueChanged(channel.index, channel.level);
                        }
                    }
                    channels.get(holder.getAdapterPosition() + 1).level = points;
                    notifyItemChanged(holder.getAdapterPosition() + 1);
                }
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
            fader.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        ChannelStrip group = channels.get(holder.getAdapterPosition());
                        channels.get(holder.getAdapterPosition()).hide = !group.hide;
                        notifyItemChanged(holder.getAdapterPosition());
                        channels.get(getSubchannelIndex(-group.index)).hide = !channels.get(getSubchannelIndex(-group.index)).hide;
                        notifyItemChanged(getSubchannelIndex(-group.index));
                        return super.onSingleTapConfirmed(e);
                    }
                });

                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (channels.get(holder.getAdapterPosition()).group) {
                        gestureDetector.onTouchEvent(motionEvent);
                    }
                    return false;
                }
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
                        if (!channelStrip.group) {
                            channelMuteListener.onChannelMuteChange(itemView, index, channelStrip.sendMuted);
                        } else {
                            ArrayList<ChannelStrip> subchannels = groupedChannels.get(-channelStrip.index);
                            if (subchannels != null) {
                                for (ChannelStrip subchannel : subchannels) {
                                    // TODO: propagate mute listening upstream via channelMuteListener
                                    subchannel.sendMuted = channels.get(holder.getAdapterPosition()).sendMuted;
                                }
                                if (!channelStrip.hide)
                                    notifyItemChanged(getSubchannelIndex(-channelStrip.index));
                            }
                        }
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
                            GroupEditDialog editDialog = new GroupEditDialog(channelStrip.index, channelStrip.name, channelStrip.colourIndex, ungroupedChannels(), groupedChannels(channelStrip.index));
                            editDialog.setResultListener((dialogInterface, i) -> {
                                if (!Objects.equals(channelStrip.name, editDialog.name)) {
                                    channelStrip.name = editDialog.name;
                                    notifyItemChanged(holder.getAdapterPosition());
                                }
                                if (channelStrip.colourIndex != editDialog.colour) {
                                    channelStrip.colourIndex = editDialog.colour;
                                    channelStrip.colour = colourArray[channelStrip.colourIndex];
                                    channelStrip.colourLighter = colourArrayLighter[channelStrip.colourIndex];
                                    channelStrip.colourDarker = colourArrayDarker[channelStrip.colourIndex];
                                    notifyItemChanged(holder.getAdapterPosition());
                                    channels.get(holder.getAdapterPosition() + 1).colourIndex = editDialog.colour;
                                    notifyItemChanged(holder.getAdapterPosition() + 1);
                                }
                                updateGroup(holder.getAdapterPosition(), -channelStrip.index, editDialog.addedChannels, editDialog.removedChannels);
                                ArrayList<ChannelStrip> subchannels = groupedChannels.get(-channelStrip.index);
                                if (subchannels != null) {
                                    channelStrip.level = subchannels.stream().mapToInt(channel -> channel.level).filter(channel -> channel >= 0).max().orElse(823);
                                    channels.get(holder.getAdapterPosition() + 1).level = channelStrip.level;
                                    notifyItemChanged(holder.getAdapterPosition());
                                }
                            });
                            FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
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
        }

        @Override
        public void onValueChanged(int index, int points) {
            if (faderValueChangedListener != null)
                faderValueChangedListener.onValueChanged(index, points);
        }
    }

    public class SubChannelViewHolder extends RecyclerView.ViewHolder {

        GroupRecyclerViewAdapter adapter;
        RecyclerView recyclerView;
        ItemTouchHelper touchHelper;

        public SubChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            SubChannelViewHolder holder = this;
            adapter = new GroupRecyclerViewAdapter(context, width, viewHolder -> touchHelper.startDrag(viewHolder));
            recyclerView = itemView.findViewById(R.id.groupRecyclerView);
            ItemTouchHelper.Callback callback = new ItemMoveCallback(adapter);
            touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
            recyclerView.setAdapter(adapter);
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

    private ArrayList<ChannelStrip> ungroupedChannels() {
        ArrayList<ChannelStrip> channelStrips = new ArrayList<>();
        for (ChannelStrip channel : channels) {
            if (!channel.group && channel.groupIndex == -1) {
                channelStrips.add(channel);
            }
        }
        return channelStrips;
    }

    private ArrayList<ChannelStrip> groupedChannels(int group) {
        ArrayList<ChannelStrip> channelStrips = groupedChannels.get(-group);
        if (channelStrips == null) channelStrips = new ArrayList<>();
        return channelStrips;
    }

    private void updateGroup(int position, int group, ArrayList<ChannelStrip> addedChannels, ArrayList<ChannelStrip> removedChannels) {
        // get channels already part of the group (or initialise)
        ArrayList<ChannelStrip> currentChannels = groupedChannels.get(group);
        if (currentChannels == null) currentChannels = new ArrayList<>();
        // add added channels to group channels and remove from regular channels
        for (ChannelStrip c : addedChannels) {
            int channelIndex = getIndex(c.index);
            if (channelIndex < 0) {
                ChannelStrip channel = hiddenChannels.get(c.index);
                if (channel != null) {
                    currentChannels.add(channel);
                    hiddenChannels.remove(c.index);
                }
            } else {
                ChannelStrip channel = channels.get(channelIndex);
                currentChannels.add(channel);
                channels.remove(channelIndex);
                notifyItemRemoved(channelIndex);
            }
        }
        // remove removed channels from group channels and add to regular channels
        Collections.reverse(removedChannels);
        for (ChannelStrip c : removedChannels) {
            channels.add(position + 2, c);
            currentChannels.remove(c);
            notifyItemInserted(position + 2);
        }
        moveSubchannelsToGroup(group);
        if (currentChannels.size() == 0) groupedChannels.remove(group);
        else groupedChannels.put(group, currentChannels);
        int subchannelsIndex = getSubchannelIndex(group);
        notifyItemChanged(subchannelsIndex);
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

    int getSubchannelIndex(int group) {
        for (int i = 0; i < channels.size(); i++) {
            ChannelStrip channel = channels.get(i);
            if (channel.group && channel.groupIndex == group) {
                return i;
            }
        }
        return -1;
    }

    ChannelStrip getGroup(int group) {
        for (ChannelStrip channel : channels) {
            if (channel.index == -group) {
                return channel;
            }
        }
        for (Map.Entry<Integer, ChannelStrip> entry : hiddenChannels.entrySet()) {
            ChannelStrip channel = entry.getValue();
            if (channel.index == -group) {
                return channel;
            }
        }
        return null;
    }

    void addGroup() {
        groups++;
        ChannelStrip groupChannel = new ChannelStrip();
        groupChannel.index = -groups;

        groupChannel.level = 823;
        groupChannel.sendMuted = false;
        groupChannel.name = "GRP " + groups;
        groupChannel.patch = "";
        groupChannel.colourIndex = groups % colourArray.length;
        groupChannel.colour = colourArray[groupChannel.colourIndex];
        groupChannel.colourLighter = colourArrayLighter[groupChannel.colourIndex];
        groupChannel.colourDarker = colourArrayDarker[groupChannel.colourIndex];
        groupChannel.group = true;

        ChannelStrip subChannels = new ChannelStrip();
        subChannels.index = -groups;
        subChannels.level = 823;
        subChannels.group = true;
        subChannels.groupIndex = groups;
        subChannels.colourIndex = groups % colourArray.length;
        channels.add(0, subChannels);
        notifyItemInserted(0);

        channels.add(0, groupChannel);
        notifyItemInserted(0);
    }

    void setChannelStrip(int index, int level, boolean sendMuted, String name, boolean channelMuted, String patch, int colourIndex) {
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

    public HashMap<Integer, Object> getLayout() {
        HashMap<Integer, Object> layout = new HashMap<>();
        for (int i = 0; i < channels.size(); i++) {
            ChannelStrip channel = channels.get(i);
            if (!channel.group) {
                layout.put(i, channel.index);
            }
            if (channel.group && channel.colour != 0) {
                Group group = new Group();
                group.index = channel.index;
                group.name = channel.name;
                group.colourIndex = channel.colourIndex;
                // add subchannels
                ArrayList<ChannelStrip> subchannels = groupedChannels.get(-channel.index);
                if (subchannels != null) {
                    for (int j = 0; j < subchannels.size(); j++) {
                        ChannelStrip subchannel = subchannels.get(j);
                        group.channels.put(j, subchannel.index);
                    }
                }
                layout.put(group.index, group.toMap());
            }
        }
        for (Map.Entry<Integer, ChannelStrip> entry : hiddenChannels.entrySet()) {
            layout.put(entry.getKey(), entry.getValue().index);
        }
        return layout;
    }

    void setValuesChangeListener(FaderValueChangedListener listener) {
        faderValueChangedListener = listener;
    }

    public interface FaderValueChangedListener {
        void onValueChanged(int index, int points);
    }

    void setFaderMuteListener(ChannelMuteListener listener) {
        channelMuteListener = listener;
    }

    public interface ChannelMuteListener {
        void onChannelMuteChange(View view, int index, boolean muted);
    }
}
