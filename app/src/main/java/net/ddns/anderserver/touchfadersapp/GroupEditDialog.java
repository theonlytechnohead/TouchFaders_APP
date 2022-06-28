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

    DialogInterface.OnClickListener resultListener;

    public GroupEditDialog(int index, String name, int colour, ArrayList<ChannelStrip> ungroupedChannels) {
        this.index = -index;
        this.name = name;
        this.colour = colour;
        this.ungroupedChannels = ungroupedChannels;
    }

    public GroupEditDialog(ArrayList<ChannelStrip> ungroupedChannels) {
        this.ungroupedChannels = ungroupedChannels;
        groupedChannels = new ArrayList<>();
    }

    public void setResultListener(DialogInterface.OnClickListener resultListener) {
        this.resultListener = resultListener;
    }

    private CharSequence[] getUngroupedChannels() {
        CharSequence[] channels = new CharSequence[ungroupedChannels.size()];
        for (int i = 0; i < ungroupedChannels.size(); i++) {
            ChannelStrip channel = ungroupedChannels.get(i);
            channels[i] = "" + (channel.index + 1) + ": " + channel.name + " (" + channel.patch + ")";
        }
        return channels;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        if (groupedChannels == null) {
            builder.setTitle("Edit group");

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
                GroupEditDialog childDialog = new GroupEditDialog(ungroupedChannels);
                childDialog.setResultListener((dialogInterface, i) -> this.groupedChannels = childDialog.groupedChannels);
                childDialog.show(requireActivity().getSupportFragmentManager(), "group channels dialog");
            });

            builder.setView(layout);
        } else {
            builder.setTitle("Edit group channels");
            builder.setMultiChoiceItems(getUngroupedChannels(), null, (dialogInterface, position, isChecked) -> {
                if (isChecked) {
                    groupedChannels.add(ungroupedChannels.get(position));
                } else {
                    groupedChannels.remove(ungroupedChannels.get(position));
                }
            });
        }

        builder.setPositiveButton("Done", resultListener);
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }
}
