package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {
    private List<Device> devices;
    private OnDeviceConnectListener connectListener;

    public interface OnDeviceConnectListener {
        void onDeviceConnect(Device device);
    }

    public DeviceAdapter(List<Device> devices, OnDeviceConnectListener listener) {
        this.devices = devices;
        this.connectListener = listener;
    }

    // Constructor for backward compatibility
    public DeviceAdapter(List<Device> devices) {
        this.devices = devices;
        this.connectListener = null;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = devices.get(position);
        holder.deviceName.setText(device.getName());
        
        // Set device type or signal strength based on device type
        if (device.getType().equals("WiFi Network")) {
            String securityInfo = device.isSecured() ? "Secured" : "Open";
            String signalInfo = device.getSignalStrengthLabel();
            holder.deviceType.setText(String.format("%s • %s", securityInfo, signalInfo));
            
            // Highlight Nexadomus devices
            if (device.isNexadomusDevice()) {
                holder.itemView.setBackgroundResource(android.R.color.holo_green_light);
            } else {
                holder.itemView.setBackgroundResource(android.R.color.transparent);
            }
        } else {
            holder.deviceType.setText(device.getType());
            holder.itemView.setBackgroundResource(android.R.color.transparent);
        }
        
        holder.deviceIcon.setImageResource(device.getIconResId());

        holder.btnAdd.setOnClickListener(v -> {
            if (connectListener != null) {
                connectListener.onDeviceConnect(device);
            } else {
                // Fallback if no listener is set
                Toast.makeText(v.getContext(), 
                       "Connecting to " + device.getName() + "...", 
                       Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        ImageView deviceIcon;
        TextView deviceName;
        TextView deviceType;
        Button btnAdd;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceIcon = itemView.findViewById(R.id.deviceIcon);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceType = itemView.findViewById(R.id.deviceType);
            btnAdd = itemView.findViewById(R.id.btnAdd);
        }
    }
} 