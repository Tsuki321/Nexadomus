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

import org.json.JSONObject;

public class GarageFragment extends Fragment {
    private static final String PREFS_NAME = "NexadomusGarage";
    private static final String PREF_GARAGE_STATE = "garageState";
    
    private boolean isGarageOpen = false;
    private TextView statusText;
    private TextView connectionModeText;
    private Button btnOpenGarage;
    private Button btnCloseGarage;
    private NexadomusApiClient apiClient;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_garage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the API client
        apiClient = NexadomusApiClient.getInstance();
        apiClient.setAppContext(requireContext());
        
        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isGarageOpen = sharedPreferences.getBoolean(PREF_GARAGE_STATE, false);

        // Initialize UI components
        statusText = view.findViewById(R.id.statusText);
        connectionModeText = view.findViewById(R.id.connectionModeText);
        btnOpenGarage = view.findViewById(R.id.btnOpenGarage);
        btnCloseGarage = view.findViewById(R.id.btnCloseGarage);

        // Set initial state
        updateUIState();

        // Check current status when fragment is created
        fetchCurrentStatus();

        // Set click listeners for the buttons
        btnOpenGarage.setOnClickListener(v -> {
            if (!isGarageOpen) {
                controlGarage("open");
            } else {
                Toast.makeText(getContext(), "Garage is already open", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseGarage.setOnClickListener(v -> {
            if (isGarageOpen) {
                controlGarage("close");
            } else {
                Toast.makeText(getContext(), "Garage is already closed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void controlGarage(String action) {
        // Show loading state
        setButtonsEnabled(false);
        
        apiClient.controlGarage(action, new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                if (response.contains("Remote command sent")) {
                    // This was a remote command via ThingSpeak
                    // Update local state to reflect the expected new state after the command
                    isGarageOpen = action.equals("open");
                    saveGarageState();
                    showRemoteMode(true);
                    Toast.makeText(getContext(), "Remote command sent: Garage " + action, Toast.LENGTH_SHORT).show();
                } else {
                    // Update local state for direct control
                    isGarageOpen = action.equals("open");
                    saveGarageState();
                    showRemoteMode(false);
                    Toast.makeText(getContext(), "Garage " + action + " command sent", Toast.LENGTH_SHORT).show();
                }
                updateUIState();
                setButtonsEnabled(true);
            }

            @Override
            public void onError(String error) {
                showError("Failed to control garage: " + error);
                setButtonsEnabled(true);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh status when fragment becomes visible
        fetchCurrentStatus();
    }
    
    private void fetchCurrentStatus() {
        // Disable controls while fetching
        setButtonsEnabled(false);
        
        // Get current status from ESP32
        apiClient.getStatus(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    // Check garage status (0=closed, 90=open)
                    if (json.has("garage")) {
                        isGarageOpen = json.getInt("garage") == 90;
                        saveGarageState();
                    }
                    showRemoteMode(false); // We're in local mode if we can get status
                } catch (Exception e) {
                    e.printStackTrace();
                    // If parsing fails, show the saved state
                    isGarageOpen = sharedPreferences.getBoolean(PREF_GARAGE_STATE, false);
                }
                
                updateUIState();
                setButtonsEnabled(true);
            }

            @Override
            public void onError(String error) {
                if (error.contains("Failed to connect")) {
                    // We're in remote mode
                    showRemoteMode(true);
                } else {
                    showError("Failed to get status: " + error);
                }
                
                // Use the saved state
                isGarageOpen = sharedPreferences.getBoolean(PREF_GARAGE_STATE, false);
                updateUIState();
                setButtonsEnabled(true);
            }
        });
    }
    
    private void saveGarageState() {
        sharedPreferences.edit().putBoolean(PREF_GARAGE_STATE, isGarageOpen).apply();
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
    
    private void setButtonsEnabled(boolean enabled) {
        if (btnOpenGarage != null) {
            btnOpenGarage.setEnabled(enabled);
        }
        if (btnCloseGarage != null) {
            btnCloseGarage.setEnabled(enabled);
        }
    }
    
    private void updateUIState() {
        if (statusText != null) {
            statusText.setText("Status: " + (isGarageOpen ? "Open" : "Closed"));
        }
        
        // Update button visibility based on current state
        if (btnOpenGarage != null && btnCloseGarage != null) {
            btnOpenGarage.setEnabled(!isGarageOpen);
            btnCloseGarage.setEnabled(isGarageOpen);
        }
    }
    
    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
} 