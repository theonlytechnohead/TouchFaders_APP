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

    int index;
    String name;
    int colour;
    ArrayList<ChannelStrip> ungroupedChannels;
    ArrayList<ChannelStrip> groupedChannels;
    ArrayList<ChannelStrip> addedChannels;
    ArrayList<ChannelStrip> removedChannels;

    DialogInterface.OnClickListener resultListener;

    public GroupEditDialog(int index, String name, int colour, ArrayList<ChannelStrip> ungroupedChannels, ArrayList<ChannelStrip> groupedChannels) {
        this.index = -index;
        this.name = name;
        this.colour = colour;
        this.ungroupedChannels = new ArrayList<>(ungroupedChannels);
        this.groupedChannels = new ArrayList<>(groupedChannels);
        addedChannels = new ArrayList<>();
        removedChannels = new ArrayList<>();
    }

    public void setResultListener(DialogInterface.OnClickListener resultListener) {
        this.resultListener = resultListener;
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
            GroupSelectDialog childDialog = new GroupSelectDialog(index, name, ungroupedChannels, groupedChannels);
            childDialog.setResultListener((dialogInterface, i) -> {
                ArrayList<ChannelStrip> added = new ArrayList<>();
                ArrayList<ChannelStrip> removed = new ArrayList<>();
                for (ChannelStrip channel : this.ungroupedChannels) {
                    if (childDialog.grouped.contains(channel)) {
                        added.add(channel);
                    }
                }
                for (ChannelStrip channel : this.groupedChannels) {
                    if (!childDialog.grouped.contains(channel)) {
                        removed.add(channel);
                    }
                }
                this.groupedChannels.addAll(added);
                for (ChannelStrip channel : added) {
                    if (!addedChannels.contains(channel)) {
                        addedChannels.add(channel);
                        removedChannels.remove(channel);
                    }
                }
                this.groupedChannels.removeAll(removed);
                this.ungroupedChannels.addAll(removed);
                for (ChannelStrip channel : removed) {
                    if (!removedChannels.contains(channel)) {
                        removedChannels.add(channel);
                        addedChannels.remove(channel);
                    }
                }
            });
            childDialog.show(requireActivity().getSupportFragmentManager(), "group channels dialog");
        });

        builder.setView(layout);

        builder.setPositiveButton("Done", resultListener);
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }
}

