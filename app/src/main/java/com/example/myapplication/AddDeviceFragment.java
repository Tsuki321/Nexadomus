package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AddDeviceFragment extends Fragment implements DeviceAdapter.OnDeviceConnectListener {
    private static final String TAG = "AddDeviceFragment";
    
    private RecyclerView deviceList;
    private TextView scanStatus;
    private DeviceAdapter deviceAdapter;
    private List<Device> devices;
    private Button btnDiscover;
    private boolean isDiscovering = false;
    private NexadomusApiClient apiClient;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
        apiClient = NexadomusApiClient.getInstance();
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
        btnDiscover = view.findViewById(R.id.btnScan); // Reuse the existing button

        // Initialize RecyclerView
        devices = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(devices, this);
        deviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        deviceList.setAdapter(deviceAdapter);

        // Update button text
        btnDiscover.setText("Discover Devices");

        btnDiscover.setOnClickListener(v -> {
            if (!isDiscovering) {
                discoverDevices();
            }
        });
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
    
    private void discoverDevices() {
        // Clear previous results
        devices.clear();
        deviceAdapter.notifyDataSetChanged();
        
        // Update UI
        isDiscovering = true;
        scanStatus.setText("Discovering devices...");
        btnDiscover.setText("Discovering...");
        btnDiscover.setEnabled(false);
        
        // Check if we're connected to the Nexadomus network
        boolean isConnectedToNexadomus = apiClient.isConnectedToNexadomus();
        
        // Simulate network request with delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Add simulated devices only if connected to Nexadomus
            if (isConnectedToNexadomus) {
                // If we're connected to Nexadomus, request available devices from ESP32
                apiClient.getAvailableDevices(new NexadomusApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        // Parse device list (in a real implementation)
                        // For now, we'll simulate it
                        addSimulatedDevices();
                        
                        // Update UI
                        updateDiscoveryComplete("Nexadomus Hub detected with connected devices");
                    }
                    
                    @Override
                    public void onError(String error) {
                        // Update UI with error
                        updateDiscoveryComplete("Error getting devices: " + error);
                    }
                });
            } else {
                // If not connected, show no devices
                
                // Update UI
                updateDiscoveryComplete("No Nexadomus Hub detected\nConnect to Nexadomus WiFi network to discover devices");
                
                // Show hint toast
                Toast.makeText(getContext(), 
                        "Connect to the Nexadomus WiFi to discover devices", 
                        Toast.LENGTH_LONG).show();
            }
        }, 2000); // 2 second delay to simulate discovery
    }
    
    private void addSimulatedDevices() {
        // Add the primary Nexadomus controller
        devices.add(new Device("Nexadomus Hub", "Smart Home Controller", R.drawable.ic_hub));
        
        // Add connected devices
        devices.add(new Device("Living Room Lights", "Smart Lights", R.drawable.ic_lights));
        devices.add(new Device("Garage Door", "Smart Garage", R.drawable.ic_garage));
        devices.add(new Device("Garden Sprinklers", "Irrigation System", R.drawable.ic_sprinklers));
        devices.add(new Device("Thermostat", "Climate Control", R.drawable.ic_thermostat));
        
        // Notify the adapter
        deviceAdapter.notifyDataSetChanged();
    }
    
    private void updateDiscoveryComplete(String message) {
        isDiscovering = false;
        scanStatus.setText(message);
        btnDiscover.setText("Discover Again");
        btnDiscover.setEnabled(true);
    }

    @Override
    public void onDeviceConnect(Device device) {
        // Handle device connection/addition
        Toast.makeText(getContext(), 
                "Adding " + device.getName() + " to your devices", 
                Toast.LENGTH_SHORT).show();
        
        // In a real implementation, you would add the device to the user's account
        // For now, just show a success message after a brief delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Toast.makeText(getContext(), 
                    device.getName() + " added successfully", 
                    Toast.LENGTH_SHORT).show();
        }, 1500);
    }
} 