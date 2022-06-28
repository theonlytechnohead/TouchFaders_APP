package net.ddns.anderserver.touchfadersapp;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.ArrayList;
import java.util.TreeMap;

public class GroupSelectDialog extends AppCompatDialogFragment {

    int index;
    String name;
    ArrayList<ChannelStrip> ungrouped;
    ArrayList<ChannelStrip> grouped;
    TreeMap<Integer, ChannelStrip> allChannels;

    DialogInterface.OnClickListener resultListener;

    public GroupSelectDialog(int index, String name, ArrayList<ChannelStrip> ungroupedChannels, ArrayList<ChannelStrip> groupedChannels) {
        this.index = index;
        this.name = name;
        this.ungrouped = new ArrayList<>(ungroupedChannels);
        this.grouped = new ArrayList<>(groupedChannels);
    }

    public void setResultListener(DialogInterface.OnClickListener resultListener) {
        this.resultListener = resultListener;
    }

    private CharSequence[] getAllChannels() {
        allChannels = new TreeMap<>();
        for (int i = 0; i < ungrouped.size(); i++) {
            ChannelStrip channel = ungrouped.get(i);
            allChannels.put(channel.index, channel);
        }
        for (int i = 0; i < grouped.size(); i++) {
            ChannelStrip channel = grouped.get(i);
            allChannels.put(channel.index, channel);
        }
        CharSequence[] channelLabels = new CharSequence[ungrouped.size() + grouped.size()];
        for (int i = 0; i < allChannels.size(); i++) {
            ChannelStrip channel = allChannels.get(i);
            if (channel != null)
                channelLabels[i] = "" + (channel.index + 1) + ": " + channel.name + " (" + channel.patch + ")";
        }
        return channelLabels;
    }

    private boolean[] getSelectedChannels() {
        allChannels = new TreeMap<>();
        for (int i = 0; i < ungrouped.size(); i++) {
            ChannelStrip channel = ungrouped.get(i);
            allChannels.put(channel.index, channel);
        }
        for (int i = 0; i < grouped.size(); i++) {
            ChannelStrip channel = grouped.get(i);
            allChannels.put(channel.index, channel);
        }
        boolean[] selectedChannels = new boolean[ungrouped.size() + grouped.size()];
        for (int i = 0; i < allChannels.size(); i++) {
            ChannelStrip channel = allChannels.get(i);
            if (channel != null) {
                if (grouped.contains(channel)) {
                    selectedChannels[i] = true;
                    continue;
                }
//                selectedChannels[i] = channel.groupIndex == index;
            }
        }
        return selectedChannels;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Edit " + name + " channels");
        builder.setMultiChoiceItems(getAllChannels(), getSelectedChannels(), (dialogInterface, position, isChecked) -> {
            if (isChecked) {
                grouped.add(ungrouped.get(position));
            } else {
                grouped.removeIf(channel -> channel.index == position);
            }
        });

        builder.setPositiveButton("Done", resultListener);
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }
}
