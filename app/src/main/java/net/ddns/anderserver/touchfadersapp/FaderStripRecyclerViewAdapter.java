package net.ddns.anderserver.touchfadersapp;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class FaderStripRecyclerViewAdapter extends RecyclerView.Adapter<FaderStripRecyclerViewAdapter.FaderStripViewHolder> {

    private final Context context;
    public static Activity getActivity(Context context) {
        if (context == null) return null;
        if (context instanceof Activity) return (Activity) context;
        if (context instanceof ContextWrapper) return getActivity(((ContextWrapper)context).getBaseContext());
        return null;
    }

    private final ArrayList<Integer> faderLevels = new ArrayList<>();
    private final ArrayList<String> channelNames = new ArrayList<>();
    private final ArrayList<Integer> faderColours = new ArrayList<>();
    private final ArrayList<Integer> faderColoursLighter = new ArrayList<>();
    private final ArrayList<String> channelPatchIn = new ArrayList<>();
    private FaderValueChangedListener faderValueChangedListener;

    private boolean wide;

    int[] colourArray;
    int[] colourArrayLighter;

    public FaderStripRecyclerViewAdapter(Context context, int numChannels, ArrayList<Integer> channelColours, boolean wide) {
        this.context = context;
        colourArray = context.getResources().getIntArray(R.array.mixer_colours);
        colourArrayLighter = context.getResources().getIntArray(R.array.mixer_colours_lighter);
        this.wide = wide;
        TypedArray array = context.obtainStyledAttributes(R.style.Widget_Theme_TouchFaders_BoxedVerticalSeekBar, new int[]{R.attr.startValue});
        for (int channel = 0; channel < numChannels; channel++ ){
            faderLevels.add(array.getInt(0, 623));
            channelNames.add("CH " + (channel + 1));
            faderColours.add(colourArray[channelColours.get(channel)]);
            faderColoursLighter.add(colourArrayLighter[channelColours.get(channel)]);
            channelPatchIn.add(String.format("IN %02d", channel + 1));
        }
        array.recycle();
    }

    @NonNull
    @Override
    public FaderStripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_fader_strip, parent, false);
        return new FaderStripViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    // Gets called every time a ViewHolder is reused (with a new position)
    @Override
    public void onBindViewHolder(@NonNull FaderStripViewHolder holder, int position) {
        holder.position = holder.getAdapterPosition();
        holder.fader.setValue(faderLevels.get(holder.getAdapterPosition()));
        holder.fader.setGradientEnd(faderColours.get(holder.getAdapterPosition()));
        holder.fader.setGradientStart(faderColoursLighter.get(holder.getAdapterPosition()));
        final float scale = context.getResources().getDisplayMetrics().density;
        int pixels = (int) (35 * scale + 0.5f);
        ViewGroup.LayoutParams faderParams = holder.fader.getLayoutParams();
        faderParams.width = wide ? pixels * 2 : pixels;
        holder.fader.setLayoutParams(faderParams);
        String number = String.valueOf((holder.getAdapterPosition() + 1));
        holder.channelNumber.setText(number);
        holder.channelPatch.setText(channelPatchIn.get(holder.getAdapterPosition()));
        holder.channelName.setText(channelNames.get(holder.getAdapterPosition()));
        holder.channelName.setBackgroundColor(faderColours.get(holder.getAdapterPosition()));
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

//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
//            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
//            if (holder.getAdapterPosition() == faderLevels.size() - 1) {
//                DisplayCutout cutout = getActivity(context).getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
//                if (cutout != null) marginLayoutParams.rightMargin = cutout.getSafeInsetRight();
//            } else {
//                marginLayoutParams.rightMargin = 0;
//            }
//            holder.itemView.setLayoutParams(marginLayoutParams);
//        }
    }

    @Override
    public int getItemCount() {
        return faderLevels.size();
    }

    public class FaderStripViewHolder extends RecyclerView.ViewHolder implements FaderStripRecyclerViewAdapter.FaderValueChangedListener {

        int position;
        ConstraintLayout faderBackground;
        ConstraintLayout channelBackground;
        BoxedVertical fader;
        TextView channelNumber;
        TextView channelPatch;
        TextView channelName;

        FaderStripViewHolder(View itemView) {
            super(itemView);
            faderBackground = itemView.findViewById(R.id.faderBackground);
            channelBackground = itemView.findViewById(R.id.stripLayout);
            fader = itemView.findViewById(R.id.fader);
            fader.setOnBoxedPointsChangeListener((boxedPoints, points) -> {
                faderLevels.set(position, points);
                faderValueChangedListener.onValueChanged(boxedPoints.getRootView(), position, boxedPoints, points);
            });
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelPatch = itemView.findViewById(R.id.channelPatch);
            channelName = itemView.findViewById(R.id.channelName);
        }

        @Override
        public void onValueChanged(View view, int index, BoxedVertical boxedVertical, int points) {
            if (faderValueChangedListener != null) faderValueChangedListener.onValueChanged(view, index, boxedVertical, points);
        }
    }

    void setFaderLevel (int index, int level) {
        faderLevels.set(index, level);
    }

    void setChannelPatchIn (int index, String patchIn) {
        channelPatchIn.set(index, patchIn);
    }

    void setChannelName (int index, String name) {
        channelNames.set(index, name);
    }

    void setValuesChangeListener (FaderValueChangedListener listener) {
        faderValueChangedListener = listener;
    }

    public interface FaderValueChangedListener {
        void onValueChanged(View view, int index, BoxedVertical boxedVertical, int points);
    }
}
