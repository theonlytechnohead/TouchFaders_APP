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

public class ChannelsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ItemMoveCallback.ItemTouchHelperContract {

    private final Context context;
    private final RecyclerView recyclerView;

    private final ArrayList<ChannelStrip> channels = new ArrayList<>();
    private final HashMap<Integer, ChannelStrip> hiddenChannels = new HashMap<>();
    private boolean hideUnusedChannelstrips;
    private final float width;

    private FaderValueChangedListener faderValueChangedListener;
    private ChannelMuteListener channelMuteListener;
    private final ItemMoveCallback.StartDragListener startDragListener;

    final int[] colourArray;
    final int[] colourArrayLighter;
    final int[] colourArrayDarker;
    final int darkGreyColour;
    final int greyColour;
    final int whiteColour;

    int groups = 0;
    private final HashMap<Integer, ArrayList<ChannelStrip>> groupedChannels = new HashMap<>();

    enum ViewType {
        NONE,
        CHANNEL,
        GROUP
    }

    public ChannelsRecyclerViewAdapter(ItemMoveCallback.StartDragListener startDragListener, Context context, RecyclerView recyclerView, int numChannels, HashMap<Integer, Integer> channelLayer, HashMap<Integer, Object> layout, ArrayList<Integer> channelColours, float width) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.setHasStableIds(true);
        this.startDragListener = startDragListener;
        this.colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        this.colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        this.colourArrayDarker = context.getResources().getIntArray(R.array.mixer_colours_darker);
        this.darkGreyColour = context.getColor(R.color.dark_grey);
        this.greyColour = context.getColor(R.color.grey);
        this.whiteColour = context.getColor(R.color.white);
        this.hideUnusedChannelstrips = false;
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ViewType.CHANNEL.ordinal()) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_channel_strip, parent, false);
            return new ChannelStripViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.group, parent, false);
            return new SubChannelsViewHolder(view);
        }
    }

    // https://stackoverflow.com/questions/5300962/getviewtypecount-and-getitemviewtype-methods-of-arrayadapter
    @Override
    public int getItemViewType(int position) {
        ChannelStrip channel = channels.get(position);
        if (channel.groupIndex != -1) return ViewType.GROUP.ordinal();
        return ViewType.CHANNEL.ordinal();
    }

    // Gets called every time a ViewHolder is reused (with a new position)
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder instanceof ChannelStripViewHolder holder) {
            ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
            holder.fader.setValue(channelStrip.level);
            holder.fader.setGradientEnd(channelStrip.colour);
            holder.fader.setGradientStart(channelStrip.colourLighter);
            holder.fader.setMute(channelStrip.sendMuted);
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
                // whole channel is muted
                holder.channelNumber.setTextColor(greyColour);
                holder.channelPatch.setTextColor(whiteColour);
                holder.channelBackground.setBackgroundColor(darkGreyColour);
            } else {
                // whole channel is on
                holder.channelNumber.setTextColor(channelStrip.colour);
                holder.channelPatch.setTextColor(channelStrip.colourDarker);
                holder.channelBackground.setBackgroundColor(channelStrip.colourLighter);
            }
            setBackgroundColour(holder);
        }
        if (viewHolder instanceof SubChannelsViewHolder subChannelsHolder) {
            ChannelStrip subChannels = channels.get(viewHolder.getAdapterPosition());
            subChannelsHolder.adapter.setColourByIndex(subChannels.colourIndex);
            ArrayList<ChannelStrip> grouped = groupedChannels.get(subChannels.groupIndex);
            if (!subChannels.hide) {
                subChannelsHolder.adapter.setChannels(grouped);
                subChannelsHolder.adapter.setFaderValueChangedListener((index, points) -> {
                    if (grouped != null) {
                        ChannelStrip group = getGroup(subChannels.groupIndex);
                        group.level = grouped.stream().mapToInt(channel -> channel.level).filter(channel -> channel >= 0).max().orElse(823);
                        ChannelStripViewHolder groupViewHolder = (ChannelStripViewHolder) recyclerView.findViewHolderForAdapterPosition(viewHolder.getAdapterPosition() - 1);
                        if (groupViewHolder != null) {
                            groupViewHolder.fader.setValue(group.level);
                        }
                        subChannels.level = group.level;
                    }
                });
            } else subChannelsHolder.adapter.setChannels(null);
        }
    }

    private void setBackgroundColour(ChannelStripViewHolder holder) {
        ChannelStrip channelStrip = channels.get(holder.getAdapterPosition());
        if (channelStrip.group) {
            // channel is a group master fader
            if (channelStrip.hide) {
                // the subchannels are hidden, don't highlight
                holder.faderBackground.setBackgroundColor(channelStrip.colourDarker);
            } else {
                // the subchannels are shown, do highlight
                holder.faderBackground.setBackgroundColor(channelStrip.colour);
            }
        } else if (channelStrip.groupIndex != -1) {
            // TODO: this doesn't seem to ever execute?
            // channel is part of a group, use the group colour
            ChannelStrip group = getGroup(channelStrip.groupIndex);
            if (channelStrip.sendMuted) {
                holder.faderBackground.setBackgroundColor(group.colour);
            } else {
                holder.faderBackground.setBackgroundColor(group.colourDarker);
            }
        } else {
            // channel is not part of a group
            if (channelStrip.sendMuted) {
                // channel send is muted
                holder.faderBackground.setBackgroundColor(greyColour);
            } else {
                // channel send is on
                holder.faderBackground.setBackgroundColor(channelStrip.colourDarker);
            }
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    @Override
    public long getItemId(int position) {
        return channels.get(position).stableID();
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
                }
                // move group
                swapChannel(i, i + 1);
                notifyItemMoved(i, i + 1);
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
                } else {
                    swapChannel(i, i - 1);
                    notifyItemMoved(i, i - 1);
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
        if (viewHolder instanceof ChannelStripViewHolder holder)
            holder.faderBackground.setBackgroundColor(channels.get(viewHolder.getAdapterPosition()).colourLighter);
    }

    @Override
    public void onChannelClear(RecyclerView.ViewHolder viewHolder) {
        setBackgroundColour((ChannelStripViewHolder) viewHolder);
        ChannelStrip moving = channels.get(viewHolder.getAdapterPosition());
        if (moving.group)
            moveSubchannelsToGroup(-moving.index);
    }

    public class ChannelStripViewHolder extends RecyclerView.ViewHolder implements ChannelsRecyclerViewAdapter.FaderValueChangedListener {

        final ConstraintLayout faderBackground;
        final ConstraintLayout channelBackground;
        final BoxedVertical fader;
        final TextView channelNumber;
        final TextView channelPatch;
        final TextView channelName;

        @SuppressLint("ClickableViewAccessibility")
        ChannelStripViewHolder(View itemView) {
            super(itemView);
            ChannelStripViewHolder holder = this;
            faderBackground = itemView.findViewById(R.id.faderBackground);
            final float scale = context.getResources().getDisplayMetrics().density;
            int pixels = (int) (width * scale + 0.5f);
            ViewGroup.LayoutParams faderParams = holder.faderBackground.getLayoutParams();
            faderParams.width = pixels;
            holder.faderBackground.setLayoutParams(faderParams);
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
                    // get the actual subchannel view holder
                    SubChannelsViewHolder subChannelHolder = (SubChannelsViewHolder) recyclerView.findViewHolderForAdapterPosition(holder.getAdapterPosition() + 1);
                    // use the subchannel holder to update subchannels
                    if (subChannelHolder != null) {
                        subChannelHolder.updateSubChannels(change);
                    }
                }
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
            fader.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
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
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
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
                    public void onLongPress(@NonNull MotionEvent e) {
                        startDragListener.requestDrag(holder);
                        super.onLongPress(e);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        // if this isn't a group viewholder, ignore
                        if (!channels.get(holder.getAdapterPosition()).group)
                            return super.onSingleTapConfirmed(e);
                        // else, setup and spawn an edit dialog
                        ChannelStrip groupChannelStrip = channels.get(holder.getAdapterPosition());
                        GroupEditDialog editDialog = new GroupEditDialog(groupChannelStrip.index, groupChannelStrip.name, groupChannelStrip.colourIndex, ungroupedChannels(), groupedChannels(groupChannelStrip.index));
                        editDialog.setResultListener((dialogInterface, i) -> {
                            if (!Objects.equals(groupChannelStrip.name, editDialog.name)) {
                                // update group viewholder
                                groupChannelStrip.name = editDialog.name;
                                notifyItemChanged(holder.getAdapterPosition());
                            }
                            if (groupChannelStrip.colourIndex != editDialog.colour) {
                                // update group viewholder
                                groupChannelStrip.colourIndex = editDialog.colour;
                                groupChannelStrip.colour = colourArray[groupChannelStrip.colourIndex];
                                groupChannelStrip.colourLighter = colourArrayLighter[groupChannelStrip.colourIndex];
                                groupChannelStrip.colourDarker = colourArrayDarker[groupChannelStrip.colourIndex];
                                notifyItemChanged(holder.getAdapterPosition());
                                // update subchannels viewholder
                                channels.get(holder.getAdapterPosition() + 1).colourIndex = editDialog.colour;
                                notifyItemChanged(holder.getAdapterPosition() + 1);
                            }
                            // update which channels are in the group
                            updateGroup(holder.getAdapterPosition(), -groupChannelStrip.index, editDialog.addedChannels, editDialog.removedChannels);
                            ArrayList<ChannelStrip> subchannels = groupedChannels.get(-groupChannelStrip.index);
                            if (subchannels != null) {
                                groupChannelStrip.level = subchannels.stream().mapToInt(channel -> channel.level).filter(channel -> channel >= 0).max().orElse(823);
                                fader.setValue(groupChannelStrip.level);
                                channels.get(holder.getAdapterPosition() + 1).level = groupChannelStrip.level;
                            }
                        });
                        FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
                        editDialog.show(fragmentManager, "Group edit dialog");
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

    public class SubChannelsViewHolder extends RecyclerView.ViewHolder {

        final GroupRecyclerViewAdapter adapter;
        final RecyclerView subChannelsRecyclerView;
        ItemTouchHelper touchHelper;

        public SubChannelsViewHolder(@NonNull View itemView) {
            super(itemView);
            adapter = new GroupRecyclerViewAdapter(context, width, viewHolder -> touchHelper.startDrag(viewHolder));
            subChannelsRecyclerView = itemView.findViewById(R.id.groupRecyclerView);
            ItemTouchHelper.Callback callback = new ItemMoveCallback(adapter);
            touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(subChannelsRecyclerView);
            subChannelsRecyclerView.setAdapter(adapter);
        }

        public void updateSubChannels(int change) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                GroupRecyclerViewAdapter.ChannelViewHolder channelViewHolder = (GroupRecyclerViewAdapter.ChannelViewHolder) subChannelsRecyclerView.findViewHolderForAdapterPosition(i);
                if (channelViewHolder != null) {
                    channelViewHolder.fader.setValue(channelViewHolder.fader.getValue() + change);
                }
            }
        }
    }

    public void toggleChannelHide() {
        hideUnusedChannelstrips = !hideUnusedChannelstrips;
        if (hideUnusedChannelstrips) {
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
        groupChannel.name = "GROUP" + groups;
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

    public boolean getHideUnusedChannelstrips() {
        return hideUnusedChannelstrips;
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
