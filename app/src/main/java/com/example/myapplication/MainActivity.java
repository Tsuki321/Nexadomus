package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String NEXADOMUS_SSID = "Nexadomus Prototype";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize API clients with application context
        NexadomusApiClient.getInstance().setAppContext(getApplicationContext());
        ThingSpeakClient.getInstance().setAppContext(getApplicationContext());
        
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            
            if (item.getItemId() == R.id.nav_garage) {
                selectedFragment = new GarageFragment();
            } else if (item.getItemId() == R.id.nav_sprinklers) {
                selectedFragment = new SprinklersFragment();
            } else if (item.getItemId() == R.id.nav_lights) {
                selectedFragment = new LightsFragment();
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
                return true;
            }
            return false;
        });
        
        // Set default fragment
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, new GarageFragment())
            .commit();
        
        // Check WiFi connection status
        checkWiFiConnection();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check WiFi connection every time the app resumes
        checkWiFiConnection();
    }
    
    private void checkWiFiConnection() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WiFi manager is null");
            showToast("Cannot access WiFi services");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "WiFi is disabled");
            showToast("WiFi is disabled. Please enable WiFi for local control.");
            return;
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.e(TAG, "WiFi info is null");
            showToast("Not connected to WiFi");
            return;
        }

        String ssid = wifiInfo.getSSID();
        if (ssid != null) {
            ssid = ssid.replace("\"", ""); // Remove quotes
            Log.d(TAG, "Connected to WiFi: " + ssid);
            
            if (NEXADOMUS_SSID.equals(ssid)) {
                showToast("Connected to Nexadomus AP - Local control available");
            } else {
                showToast("Connected to " + ssid + " - Remote control via ThingSpeak");
            }
        } else {
            Log.e(TAG, "SSID is null");
            showToast("Cannot determine WiFi connection");
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}