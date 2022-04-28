package net.ddns.anderserver.touchfadersapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MixSelectRecyclerViewAdapter extends RecyclerView.Adapter<MixSelectRecyclerViewAdapter.ViewHolder> {

    private final List<String> mixNames;
    private final List<Integer> mixColours;
    private MixButtonClickListener clickListener;

    MixSelectRecyclerViewAdapter(Context context, List<String> names, List<Integer> colours) {
        this.mixNames = names;
        this.mixColours = colours;
    }

    @NonNull
    @Override
    public MixSelectRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_mix_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MixSelectRecyclerViewAdapter.ViewHolder holder, int position) {
        holder.mixSelectButton.setText(mixNames.get(position));
        holder.mixSelectButton.setEnabled(true);
        holder.mixSelectButton.setBackgroundColor(mixColours.get(position));
        holder.position = position;
    }

    @Override
    public int getItemCount() {
        return mixNames.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements MixButtonClickListener {
        int position;

        //TextView mixTextView;
        Button mixSelectButton;

        ViewHolder(View itemView) {
            super(itemView);
            //mixTextView = itemView.findViewById(R.id.mix_select_text);
            //mixTextView.setOnClickListener(this);
            mixSelectButton = itemView.findViewById(R.id.mix_select_button);
            mixSelectButton.setOnClickListener(view -> {
                mixSelectButton.setEnabled(false);
                onItemClick(view, position);
            });
        }

        @Override
        public void onItemClick (View view, int index) {
            if (clickListener != null) clickListener.onItemClick(view, index);
        }
    }

    // convenience method for getting data at click position
    String getMixName(int id) {
        return mixNames.get(id);
    }

    // allows clicks events to be caught
    void setClickListener(MixButtonClickListener onClickListener) {
        this.clickListener = onClickListener;
    }

    // parent activity will implement this method to respond to click events
    public interface MixButtonClickListener {
        void onItemClick(View view, int index);
    }
}
