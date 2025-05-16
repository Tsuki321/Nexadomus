package com.example.myapplication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddDeviceFragment extends Fragment implements DeviceAdapter.OnDeviceConnectListener {
    private static final int PERMISSION_REQUEST_CODE = 456;
    
    // Get required permissions based on Android version
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            return new String[] {
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            };
        } else { // Android 12 and below
            return new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            };
        }
    }
    
    private RecyclerView deviceList;
    private TextView scanStatus;
    private DeviceAdapter deviceAdapter;
    private List<Device> devices;
    private WifiManager wifiManager;
    private boolean isScanning = false;
    private Button btnScan;
    
    // Permission request launcher
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    
    // Wifi scan receiver
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResult);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_device, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        deviceList = view.findViewById(R.id.deviceList);
        scanStatus = view.findViewById(R.id.scanStatus);
        btnScan = view.findViewById(R.id.btnScan);

        // Get WiFi manager
        wifiManager = (WifiManager) requireActivity().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        // Initialize RecyclerView
        devices = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(devices, this);
        deviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        deviceList.setAdapter(deviceAdapter);

        // Set button text based on permission status
        updateButtonText();

        btnScan.setOnClickListener(v -> {
            if (!isScanning) {
                checkPermissionsAndScan();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register the WiFi scan receiver
        requireActivity().registerReceiver(
                wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
        
        // Update button text in case permissions changed while fragment was not visible
        updateButtonText();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the WiFi scan receiver
        try {
            requireActivity().unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // In case receiver was not registered
        }
    }
    
    private void updateButtonText() {
        if (hasRequiredPermissions()) {
            btnScan.setText("Scan for WiFi Networks");
        } else {
            btnScan.setText("Request Permissions");
        }
    }

    private void checkPermissionsAndScan() {
        if (hasRequiredPermissions()) {
            startWifiScan();
        } else {
            requestPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldShowRequestPermissionRationale() {
        boolean shouldShowRationale = false;
        for (String permission : getRequiredPermissions()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                shouldShowRationale = true;
                break;
            }
        }
        return shouldShowRationale;
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("WiFi scanning requires location permissions to discover nearby networks.")
            .setPositiveButton("OK", (dialog, which) -> requestPermissions())
            .setNegativeButton("Cancel", (dialog, which) -> {
                scanStatus.setText("Cannot scan without required permissions");
            })
            .create()
            .show();
    }

    private void requestPermissions() {
        scanStatus.setText("Permissions needed to scan for devices");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale()) {
                showPermissionRationaleDialog();
            } else {
                // Get permissions not yet granted
                List<String> permissionsToRequest = new ArrayList<>();
                for (String permission : getRequiredPermissions()) {
                    if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                            != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(permission);
                    }
                }
                
                if (!permissionsToRequest.isEmpty()) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissionsToRequest.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE
                    );
                }
            }
        } else {
            // Fallback for API < 23
            requestPermissionLauncher.launch(getRequiredPermissions());
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                startWifiScan();
            } else {
                scanStatus.setText("Cannot scan without required permissions");
                btnScan.setText("Request Permissions");
            }
        }
    }

    private void handlePermissionResult(Map<String, Boolean> permissions) {
        boolean allGranted = true;
        for (Boolean granted : permissions.values()) {
            allGranted = allGranted && granted;
        }
        
        if (allGranted) {
            startWifiScan();
        } else {
            Toast.makeText(getContext(), 
                    "Location permissions are needed to scan for WiFi networks", 
                    Toast.LENGTH_LONG).show();
            scanStatus.setText("Cannot scan without required permissions");
            btnScan.setText("Request Permissions");
        }
    }

    private void startWifiScan() {
        // Clear previous results
        devices.clear();
        deviceAdapter.notifyDataSetChanged();
        
        // Check if WiFi is enabled
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ we can't toggle WiFi directly, so prompt user
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
                scanStatus.setText("Please enable WiFi to scan for devices");
            } else {
                // For older versions, we can enable WiFi directly
                wifiManager.setWifiEnabled(true);
                scanStatus.setText("Enabling WiFi...");
            }
            return;
        }
        
        // Update UI
        isScanning = true;
        scanStatus.setText("Scanning for WiFi networks...");
        btnScan.setText("Scanning...");
        btnScan.setEnabled(false);
        
        // Start scan
        boolean success = wifiManager.startScan();
        if (!success) {
            scanFailure();
        }
    }

    private void scanSuccess() {
        List<ScanResult> scanResults = wifiManager.getScanResults();
        devices.clear();
        
        boolean foundNexadomus = false;
        
        // Process scan results
        for (ScanResult result : scanResults) {
            if (result.SSID == null || result.SSID.isEmpty()) {
                continue; // Skip empty SSIDs
            }
            
            // Create device from scan result
            boolean isSecured = !result.capabilities.contains("Open");
            Device wifiDevice = new Device(result.SSID, result.level, isSecured);
            
            // Check if this is our Nexadomus device
            if (result.SSID.equals("Nexadomus Prototype")) {
                foundNexadomus = true;
                // Add Nexadomus device first in the list
                devices.add(0, wifiDevice);
            } else {
                devices.add(wifiDevice);
            }
        }
        
        // Update UI
        deviceAdapter.notifyDataSetChanged();
        isScanning = false;
        btnScan.setText("Scan Again");
        btnScan.setEnabled(true);
        
        if (devices.isEmpty()) {
            scanStatus.setText("No WiFi networks found");
        } else if (foundNexadomus) {
            scanStatus.setText("Nexadomus device found! " + devices.size() + " total networks found");
        } else {
            scanStatus.setText(devices.size() + " WiFi networks found");
        }
    }

    private void scanFailure() {
        // Update UI
        isScanning = false;
        btnScan.setText("Scan Again");
        btnScan.setEnabled(true);
        
        // Show last results if available
        if (wifiManager != null) {
            List<ScanResult> lastResults = wifiManager.getScanResults();
            if (lastResults != null && !lastResults.isEmpty()) {
                devices.clear();
                for (ScanResult result : lastResults) {
                    if (result.SSID != null && !result.SSID.isEmpty()) {
                        boolean isSecured = !result.capabilities.contains("Open");
                        devices.add(new Device(result.SSID, result.level, isSecured));
                    }
                }
                deviceAdapter.notifyDataSetChanged();
                scanStatus.setText("Using cached results: " + devices.size() + " networks");
            } else {
                // Only show the "Scan failed" message if there are no cached results either
                scanStatus.setText("Scan failed. Please try again.");
            }
        } else {
            // Only show the failure message if wifiManager is null
            scanStatus.setText("Scan failed. Please try again.");
        }
    }
    
    @Override
    public void onDeviceConnect(Device device) {
        if (device.isNexadomusDevice()) {
            connectToWifi(device.getName());
        } else {
            Toast.makeText(getContext(), 
                    "Please connect to the Nexadomus Prototype network",
                    Toast.LENGTH_LONG).show();
        }
    }
    
    private void connectToWifi(String ssid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we can't directly connect to WiFi, so show instructions
            Toast.makeText(getContext(), 
                    "Please manually connect to " + ssid + " WiFi in your device settings", 
                    Toast.LENGTH_LONG).show();
            
            // Open WiFi settings
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            startActivity(panelIntent);
        } else {
            // For older Android, we can try to connect programmatically
            try {
                String networkSSID = ssid;
                String networkPass = "smartHome1234"; // Password from the ESP32 code
                
                WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + networkSSID + "\"";
                conf.preSharedKey = "\"" + networkPass + "\"";
                
                int netId = wifiManager.addNetwork(conf);
                wifiManager.disconnect();
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
                
                Toast.makeText(getContext(), 
                        "Connecting to " + ssid + "...", 
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), 
                        "Failed to connect: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
            }
        }
    }
} 