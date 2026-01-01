package net.ddns.anderserver.touchfadersapp;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.ArrayList;

public class GroupEditDialog extends AppCompatDialogFragment {

    final int index;
    String name;
    int colour;
    final ArrayList<ChannelStrip> ungroupedChannels;
    final ArrayList<ChannelStrip> groupedChannels;
    final ArrayList<ChannelStrip> allChannels;
    final ArrayList<ChannelStrip> addedChannels;
    final ArrayList<ChannelStrip> removedChannels;

    DialogInterface.OnClickListener resultListener;
    DialogInterface.OnClickListener removeListener;

    public GroupEditDialog(int index, String name, int colour, ArrayList<ChannelStrip> ungroupedChannels, ArrayList<ChannelStrip> groupedChannels) {
        this.index = -index;
        this.name = name;
        this.colour = colour;
        this.ungroupedChannels = new ArrayList<>(ungroupedChannels);
        this.groupedChannels = new ArrayList<>(groupedChannels);
        allChannels = new ArrayList<>();
        allChannels.addAll(ungroupedChannels);
        allChannels.addAll(groupedChannels);
        addedChannels = new ArrayList<>();
        removedChannels = new ArrayList<>();
    }

    public void setResultListener(DialogInterface.OnClickListener resultListener) {
        this.resultListener = resultListener;
    }

    public void setRemoveListener(DialogInterface.OnClickListener removeListener) {
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Edit " + name);

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View layout = inflater.inflate(R.layout.group_edit, null);

        EditText groupName = layout.findViewById(R.id.group_name);
        groupName.setText(name);
        groupName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
                name = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        Spinner colourSelection = layout.findViewById(R.id.group_colour);
        colourSelection.setSelection(colour);
        colourSelection.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                colour = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Button editChannels = layout.findViewById(R.id.group_edit);
        editChannels.setOnClickListener(view -> {
            ArrayList<ChannelStrip> ungrouped = new ArrayList<>(ungroupedChannels);
            ungrouped.removeAll(addedChannels);
            ungrouped.addAll(removedChannels);
            ArrayList<ChannelStrip> grouped = new ArrayList<>();
            grouped.addAll(groupedChannels);
            grouped.addAll(addedChannels);
            grouped.removeAll(removedChannels);
            GroupSelectDialog childDialog = new GroupSelectDialog(index, name, ungrouped, grouped);
            childDialog.setResultListener((dialogInterface, i) -> {

                // for each channel...
                for (ChannelStrip channel : allChannels) {
                    // if it was ungrouped...
                    if (ungroupedChannels.contains(channel)) {
                        // ... and is still ungrouped
                        if (!childDialog.grouped.contains(channel)) {
                            // do nothing (unmark from "added" just in case)
                            addedChannels.remove(channel);
                        }
                        // if it is now grouped, mark it as "added"
                        if (childDialog.grouped.contains(channel)) {
                            addedChannels.add(channel);
                            removedChannels.remove(channel);
                        }
                    }
                    // if it was grouped...
                    if (groupedChannels.contains(channel)) {
                        // .. and is still grouped
                        if (childDialog.grouped.contains(channel)) {
                            // do nothing (unmark from "removed" just in case)
                            removedChannels.remove(channel);
                        }
                        // if it is now ungrouped, mark it as "removed"
                        if (!childDialog.grouped.contains(channel)) {
                            removedChannels.add(channel);
                            addedChannels.remove(channel);
                        }
                    }
                }
            });
            childDialog.show(requireActivity().getSupportFragmentManager(), "Group channel selection dialog");
        });

        builder.setView(layout);

        builder.setPositiveButton("Done", resultListener);
        builder.setNeutralButton("Remove", removeListener);
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }
}
