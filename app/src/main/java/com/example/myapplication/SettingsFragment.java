package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        Button aboutButton = view.findViewById(R.id.aboutButton);
        Button helpButton = view.findViewById(R.id.helpButton);
        Button wifiSettingsButton = view.findViewById(R.id.wifiSettingsButton);
        
        aboutButton.setOnClickListener(v -> {
            AboutDialogFragment aboutDialog = new AboutDialogFragment();
            aboutDialog.show(getParentFragmentManager(), "about_dialog");
        });
        
        helpButton.setOnClickListener(v -> {
            HelpDialogUtil.showHelpDialog(requireContext(), "Need help with Nexadomus?", 
                "Nexadomus allows you to control your smart home devices remotely. " +
                "Navigate to each device control using the buttons on the Home screen.");
        });
        
        wifiSettingsButton.setOnClickListener(v -> {
            HelpDialogUtil.showConnectionHelpDialog(requireContext());
        });
    }
} 