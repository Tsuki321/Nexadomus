package com.example.myapplication;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class AboutDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_about, null);
        
        TextView developersText = view.findViewById(R.id.developersText);
        String developers = "Developed by:\n\n" +
                "• Ezekiel Herbert Magleo\n" +
                "• Cirilo M. Enriquez III\n" +
                "• Jerome Almedierre";
        developersText.setText(developers);
        
        return new AlertDialog.Builder(requireContext())
                .setTitle("About Nexadomus")
                .setView(view)
                .setPositiveButton("Close", (dialog, which) -> dismiss())
                .create();
    }
} 