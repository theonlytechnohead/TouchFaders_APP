package net.ddns.anderserver.touchfadersapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;
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

public class ChannelStripRecyclerViewAdapter extends RecyclerView.Adapter<ChannelStripRecyclerViewAdapter.ChannelStripViewHolder> implements ItemMoveCallback.ItemTouchHelperContract {

    private final Context context;

    private final ArrayList<Integer> channels = new ArrayList<>();

    private final ArrayList<Integer> faderLevels = new ArrayList<>();
    private final ArrayList<String> channelNames = new ArrayList<>();
    private final ArrayList<Integer> faderColours = new ArrayList<>();
    private final ArrayList<Integer> faderColoursLighter = new ArrayList<>();
    private final ArrayList<Boolean> muted;
    private final ArrayList<String> channelPatchIn = new ArrayList<>();
    private FaderValueChangedListener faderValueChangedListener;
    private FaderMuteListener faderMuteListener;
    private final ItemMoveCallback.StartDragListener startDragListener;

    private final float width;

    int[] colourArray;
    int[] colourArrayLighter;

    public ChannelStripRecyclerViewAdapter(ItemMoveCallback.StartDragListener startDragListener, Context context, int numChannels, ArrayList<Integer> channelColours, ArrayList<Boolean> muted, float width) {
        this.context = context;
        this.startDragListener = startDragListener;
        colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        this.muted = muted;
        this.width = width;
        TypedArray array = context.obtainStyledAttributes(R.style.Widget_Theme_TouchFaders_BoxedVerticalSeekBar, new int[]{R.attr.startValue});
        for (int channel = 0; channel < numChannels; channel++) {
            channels.add(channel);

            faderLevels.add(array.getInt(0, 623));
            channelNames.add("CH " + (channel + 1));
            faderColours.add(colourArray[channelColours.get(channel)]);
            faderColoursLighter.add(colourArrayLighter[channelColours.get(channel)]);
            channelPatchIn.add(String.format("IN %02d", channel + 1));
            notifyItemInserted(channel);
        }
        array.recycle();
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
        holder.position = channels.get(holder.getAdapterPosition());
        holder.fader.setValue(faderLevels.get(channels.get(holder.getAdapterPosition())));
        holder.fader.setGradientEnd(faderColours.get(channels.get(holder.getAdapterPosition())));
        holder.fader.setGradientStart(faderColoursLighter.get(channels.get(holder.getAdapterPosition())));
        holder.fader.setMute(muted.get(channels.get(holder.getAdapterPosition())));
        final float scale = context.getResources().getDisplayMetrics().density;
        int pixels = (int) (width * scale + 0.5f);
        ViewGroup.LayoutParams faderParams = holder.fader.getLayoutParams();
        faderParams.width = pixels;
        holder.fader.setLayoutParams(faderParams);
        String number = String.valueOf((channels.get(holder.getAdapterPosition()) + 1));
        holder.channelNumber.setText(number);
        holder.channelPatch.setText(channelPatchIn.get(channels.get(holder.getAdapterPosition())));
        holder.channelName.setText(channelNames.get(channels.get(holder.getAdapterPosition())));
        holder.channelName.setBackgroundColor(faderColours.get(channels.get(holder.getAdapterPosition())));
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

        // Set channel name sizing, per channel name length
        if (holder.channelName.getText().length() <= 3) {
            holder.channelName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        }
        if (holder.channelName.getText().length() == 4) {
            holder.channelName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }
        if (holder.channelName.getText().length() == 5) {
            holder.channelName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        }
        if (holder.channelName.getText().length() == 6) {
            holder.channelName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        }
    }

    @Override
    public int getItemCount() {
        return faderLevels.size();
    }

    @Override
    public void onChannelMoved(int fromPosition, int toPosition) {
        // everybody do the swap!
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                swapChannel(i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                swapChannel(i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        notifyItemChanged(fromPosition);
    }

    void swapChannel(int from, int to) {
        Collections.swap(channels, from, to);
//        Log.i("SWAP", "Swapped " + from + " to " + to);
//        Collections.swap(faderLevels, from, to);
//        Collections.swap(channelNames, from, to);
//        Collections.swap(faderColours, from, to);
//        Collections.swap(faderColoursLighter, from, to);
//        Collections.swap(muted, from, to);
//        Collections.swap(channelPatchIn, from, to);
    }

    @Override
    public void onChannelSelected(ChannelStripViewHolder channelStripViewHolder) {
        channelStripViewHolder.faderBackground.setBackgroundColor(faderColours.get(channels.get(channelStripViewHolder.getAdapterPosition())));
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
                int index = channels.get(holder.getAdapterPosition());
                faderLevels.set(index, points);
                faderValueChangedListener.onValueChanged(boxedPoints.getRootView(), index, boxedPoints, index);
            });
            channelBackground.setOnTouchListener(new View.OnTouchListener() {
                private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        int index = channels.get(holder.getAdapterPosition());
                        muted.set(index, !muted.get(index));
                        fader.setMute(muted.get(index));
                        notifyItemChanged(holder.getAdapterPosition());
                        faderMuteListener.onFaderMuteChange(itemView, index, muted.get(index));
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

    void setFaderLevel(int index, int level) {
        faderLevels.set(index, level);
        notifyItemChanged(index);
    }

    void setChannelPatchIn(int index, String patchIn) {
        channelPatchIn.set(index, patchIn);
        notifyItemChanged(index);
    }

    void setChannelName(int index, String name) {
        channelNames.set(index, name);
        notifyItemChanged(index);
    }

    void setChannelMute(int index, boolean state) {
        muted.set(index, state);
        notifyItemChanged(index);
    }

    void setValuesChangeListener(FaderValueChangedListener listener) {
        faderValueChangedListener = listener;
    }

    public interface FaderValueChangedListener {
        void onValueChanged(View view, int index, BoxedVertical boxedVertical, int points);
    }

    void setFaderMuteListener(FaderMuteListener listener) {
        faderMuteListener = listener;
    }

    public interface FaderMuteListener {
        void onFaderMuteChange(View view, int index, boolean muted);
    }
}
