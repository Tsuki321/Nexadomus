package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ACFragment extends Fragment {
    private static final String PREFS_NAME = "NexadomusAC";
    private static final String PREF_AC_STATE = "acState";
    
    private TextView statusText;
    private TextView connectionModeText;
    private boolean isACOn = false;
    private SharedPreferences sharedPreferences;
    private NexadomusApiClient apiClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ac, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize API client
        apiClient = NexadomusApiClient.getInstance();
        apiClient.setAppContext(requireContext());
        
        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isACOn = sharedPreferences.getBoolean(PREF_AC_STATE, false);

        statusText = view.findViewById(R.id.statusText);
        connectionModeText = view.findViewById(R.id.connectionModeText);
        Button btnOn = view.findViewById(R.id.btnOn);
        Button btnOff = view.findViewById(R.id.btnOff);
        
        // Update initial status
        updateStatus();

        btnOn.setOnClickListener(v -> {
            isACOn = true;
            saveACState();
            updateStatus();
            // TODO: Implement AC power on logic
            // For now, just show a toast message
            Toast.makeText(getContext(), "AC On command sent", Toast.LENGTH_SHORT).show();
        });

        btnOff.setOnClickListener(v -> {
            isACOn = false;
            saveACState();
            updateStatus();
            // TODO: Implement AC power off logic
            // For now, just show a toast message
            Toast.makeText(getContext(), "AC Off command sent", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh from saved state when fragment becomes visible
        isACOn = sharedPreferences.getBoolean(PREF_AC_STATE, false);
        updateStatus();
    }
    
    private void saveACState() {
        sharedPreferences.edit().putBoolean(PREF_AC_STATE, isACOn).apply();
    }

    private void updateStatus() {
        statusText.setText("Status: " + (isACOn ? "On" : "Off"));
    }
    
    private void showRemoteMode(boolean isRemote) {
        if (connectionModeText != null) {
            if (isRemote) {
                connectionModeText.setText("Mode: REMOTE (ThingSpeak)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else {
                connectionModeText.setText("Mode: LOCAL (Direct Connection)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        }
    }
} 