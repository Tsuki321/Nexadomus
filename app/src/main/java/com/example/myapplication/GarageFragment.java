package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
    private static final String PREF_REMOTE_MODE = "remoteMode";
    
    private boolean isGarageOpen = false;
    private TextView statusText;
    private TextView connectionModeText;
    private Button btnOpenGarage;
    private Button btnCloseGarage;
    private NexadomusApiClient apiClient;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_garage, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_common, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_help) {
            HelpDialogUtil.showConnectionHelpDialog(requireContext());
            return true;
        }
        return super.onOptionsItemSelected(item);
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
            controlGarage("open");
        });

        btnCloseGarage.setOnClickListener(v -> {
            controlGarage("close");
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
        
        // First check if we were previously in remote mode
        boolean wasInRemoteMode = sharedPreferences.getBoolean(PREF_REMOTE_MODE, false);
        if (wasInRemoteMode) {
            showRemoteMode(true);
        }
        
        // Still try to refresh status when fragment becomes visible
        // If we're in remote mode, this will just reconfirm that status
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
        // Save the mode to preferences for persistence
        sharedPreferences.edit().putBoolean(PREF_REMOTE_MODE, isRemote).apply();
        
        if (connectionModeText != null) {
            if (isRemote) {
                connectionModeText.setText("Mode: Remote (ThingSpeak)");
                connectionModeText.setBackgroundResource(R.drawable.bg_remote_mode);
            } else {
                connectionModeText.setText("Mode: Direct Connection");
                connectionModeText.setBackgroundResource(R.drawable.bg_local_mode);
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
        
        // Keep both buttons enabled regardless of state
        if (btnOpenGarage != null && btnCloseGarage != null) {
            btnOpenGarage.setEnabled(true);
            btnCloseGarage.setEnabled(true);
        }
    }
    
    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
} 