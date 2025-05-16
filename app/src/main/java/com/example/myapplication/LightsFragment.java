package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

public class LightsFragment extends Fragment {
    private static final String PREFS_NAME = "NexadomusLights";
    private static final String PREF_BRIGHTNESS = "brightness";
    
    private int brightness = 0;
    private TextView statusText;
    private TextView connectionModeText;
    private SeekBar brightnessSlider;
    private Button btnOff, btnLow, btnMedium, btnHigh;
    private NexadomusApiClient apiClient;
    private boolean remoteMode = false;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lights, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the API client
        apiClient = NexadomusApiClient.getInstance();
        apiClient.setAppContext(requireContext());
        
        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        brightness = sharedPreferences.getInt(PREF_BRIGHTNESS, 0);

        // Initialize UI components
        statusText = view.findViewById(R.id.statusText);
        connectionModeText = view.findViewById(R.id.connectionModeText);
        brightnessSlider = view.findViewById(R.id.brightnessSlider);
        btnOff = view.findViewById(R.id.btnOff);
        btnLow = view.findViewById(R.id.btnLow);
        btnMedium = view.findViewById(R.id.btnMedium);
        btnHigh = view.findViewById(R.id.btnHigh);
        
        // Update UI with saved state
        updateStatusText();
        updateBrightnessSlider();

        // Check current status
        fetchCurrentStatus();

        // Set up brightness slider
        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Just update UI, don't send command yet
                if (fromUser) {
                    brightness = progress;
                    updateStatusText();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Send the command when user releases the slider
                setBrightness(seekBar.getProgress());
            }
        });

        // Set up button click listeners
        btnOff.setOnClickListener(v -> setBrightness(0));
        btnLow.setOnClickListener(v -> setBrightness(85));
        btnMedium.setOnClickListener(v -> setBrightness(170));
        btnHigh.setOnClickListener(v -> setBrightness(255));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh status when fragment becomes visible
        fetchCurrentStatus();
    }

    private void setBrightness(int level) {
        // Show loading state
        setControlsEnabled(false);
        
        // Update our local state
        brightness = level;
        saveBrightnessState();
        
        // Set the brightness
        apiClient.controlLights(level, new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                if (response.contains("Remote command sent")) {
                    // This was a remote command via ThingSpeak
                    showRemoteMode(true);
                    Toast.makeText(getContext(), "Remote command sent successfully", Toast.LENGTH_SHORT).show();
                } else {
                    showRemoteMode(false);
                }
                
                updateStatusText();
                updateBrightnessSlider();
                setControlsEnabled(true);
            }

            @Override
            public void onError(String error) {
                showError("Failed to control lights: " + error);
                setControlsEnabled(true);
            }
        });
    }
    
    private void saveBrightnessState() {
        sharedPreferences.edit().putInt(PREF_BRIGHTNESS, brightness).apply();
    }
    
    private void fetchCurrentStatus() {
        // Show loading state
        setControlsEnabled(false);
        
        // Get current status from ESP32
        apiClient.getStatus(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    // Parse the JSON response
                    JSONObject json = new JSONObject(response);
                    
                    if (json.has("lights")) {
                        brightness = json.getInt("lights");
                        saveBrightnessState();
                    }
                    
                    showRemoteMode(false); // We're in local mode if we got status
                } catch (Exception e) {
                    e.printStackTrace();
                    // If parsing fails, use the saved brightness
                    brightness = sharedPreferences.getInt(PREF_BRIGHTNESS, 0);
                }
                
                updateStatusText();
                updateBrightnessSlider();
                setControlsEnabled(true);
            }

            @Override
            public void onError(String error) {
                if (error.contains("Not connected to Nexadomus network")) {
                    // We're in remote mode
                    showRemoteMode(true);
                } else {
                    showError("Failed to get status: " + error);
                }
                
                // Use the saved brightness
                brightness = sharedPreferences.getInt(PREF_BRIGHTNESS, 0);
                updateStatusText();
                updateBrightnessSlider();
                setControlsEnabled(true);
            }
        });
    }
    
    private void showRemoteMode(boolean isRemote) {
        remoteMode = isRemote;
        if (connectionModeText != null) {
            if (isRemote) {
                connectionModeText.setText("Mode: REMOTE (ThingSpeak)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                
                // In remote mode, disable the slider since we can only use preset levels
                if (brightnessSlider != null) {
                    brightnessSlider.setEnabled(false);
                }
            } else {
                connectionModeText.setText("Mode: LOCAL (Direct Connection)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                
                // In local mode, enable the slider for precise control
                if (brightnessSlider != null) {
                    brightnessSlider.setEnabled(true);
                }
            }
        }
    }
    
    private void updateStatusText() {
        if (statusText != null) {
            String level;
            if (brightness == 0) {
                level = "Off";
            } else if (brightness <= 85) {
                level = "Low";
            } else if (brightness <= 170) {
                level = "Medium";
            } else {
                level = "High";
            }
            statusText.setText("Light Level: " + level + " (" + brightness + ")");
        }
    }
    
    private void updateBrightnessSlider() {
        if (brightnessSlider != null) {
            brightnessSlider.setProgress(brightness);
        }
    }
    
    private void setControlsEnabled(boolean enabled) {
        if (brightnessSlider != null) {
            brightnessSlider.setEnabled(enabled && !remoteMode);
        }
        
        if (btnOff != null) {
            btnOff.setEnabled(enabled);
        }
        
        if (btnLow != null) {
            btnLow.setEnabled(enabled);
        }
        
        if (btnMedium != null) {
            btnMedium.setEnabled(enabled);
        }
        
        if (btnHigh != null) {
            btnHigh.setEnabled(enabled);
        }
    }
    
    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
} 