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
    private MaterialCardView hubButton;
    private MaterialCardView addDeviceButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        statusText = view.findViewById(R.id.statusText);
        lightsButton = view.findViewById(R.id.lightsButton);
        acButton = view.findViewById(R.id.acButton);
        garageButton = view.findViewById(R.id.garageButton);
        sprinklersButton = view.findViewById(R.id.sprinklersButton);
        hubButton = view.findViewById(R.id.hubButton);
        addDeviceButton = view.findViewById(R.id.addDeviceButton);
        
        // Set up button click listeners
        lightsButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_lightsFragment));
        
        acButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_acFragment));
        
        garageButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_garageFragment));
        
        sprinklersButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_sprinklersFragment));
            
        // Other buttons will be implemented when their features are added
        hubButton.setOnClickListener(v -> {
            // Future implementation will navigate to hub status screen
            checkAndUpdateWiFiStatus();
        });
        
        addDeviceButton.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.addDeviceFragment));
        
        // Check and update WiFi connection status
        checkAndUpdateWiFiStatus();
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