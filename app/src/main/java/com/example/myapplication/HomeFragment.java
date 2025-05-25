package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final String NEXADOMUS_SSID = "Nexadomus Home";
    
    private TextView statusText;
    private MaterialCardView lightsButton;
    private MaterialCardView acButton;
    private MaterialCardView garageButton;
    private MaterialCardView sprinklersButton;
    private MaterialCardView addDeviceButton;
    private MaterialCardView turnOffAllButton;
    private NexadomusApiClient apiClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize API client
        apiClient = NexadomusApiClient.getInstance();
        if (getContext() != null) {
            apiClient.setAppContext(getContext());
        }
        
        // Initialize views
        statusText = view.findViewById(R.id.statusText);
        lightsButton = view.findViewById(R.id.lightsButton);
        acButton = view.findViewById(R.id.acButton);
        garageButton = view.findViewById(R.id.garageButton);
        sprinklersButton = view.findViewById(R.id.sprinklersButton);
        addDeviceButton = view.findViewById(R.id.addDeviceButton);
        turnOffAllButton = view.findViewById(R.id.turnOffAllButton);
        
        // Set up button click listeners
        lightsButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_lightsFragment));
        
        acButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_acFragment));
        
        garageButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_garageFragment));
        
        sprinklersButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_sprinklersFragment));
        
        addDeviceButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.addDeviceFragment));
            
        // Set up Turn Off All button click listener
        turnOffAllButton.setOnClickListener(v -> turnOffAllDevices());
        
        // Check and update WiFi connection status
        checkAndUpdateWiFiStatus();
    }
    
    // Method to turn off all devices
    private void turnOffAllDevices() {
        // Show progress feedback to user
        Toast.makeText(getContext(), "Turning off all devices...", Toast.LENGTH_SHORT).show();
        
        // Use the API client's method to turn off all devices at once
        apiClient.turnOffAllDevices(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "All devices turned off: " + response);
                Toast.makeText(getContext(), "All devices turned off successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error turning off all devices: " + error);
                Toast.makeText(getContext(), "Failed to turn off devices. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Update WiFi status when fragment resumes
        checkAndUpdateWiFiStatus();
    }
    
    private void checkAndUpdateWiFiStatus() {
        if (getContext() == null) return;
        
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WiFi manager is null");
            updateStatusText("Cannot access WiFi services");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "WiFi is disabled");
            updateStatusText("WiFi is disabled. Please enable WiFi for local control.");
            return;
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.e(TAG, "WiFi info is null");
            updateStatusText("Not connected to WiFi");
            return;
        }

        String ssid = wifiInfo.getSSID();
        if (ssid != null) {
            ssid = ssid.replace("\"", ""); // Remove quotes
            Log.d(TAG, "Connected to WiFi: " + ssid);
            
            if (NEXADOMUS_SSID.equals(ssid)) {
                updateStatusText("Connected to Nexadomus AP - Local control");
            } else {
                updateStatusText("Connected to " + ssid + " - Remote control via ThingSpeak");
            }
        } else {
            Log.e(TAG, "SSID is null");
            updateStatusText("Cannot determine WiFi connection");
        }
    }
    
    private void updateStatusText(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }
} 