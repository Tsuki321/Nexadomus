package com.example.myapplication;

public class Device {
    private String name; // Will store SSID for WiFi networks
    private String type;
    private int iconResId;
    private int signalStrength; // For WiFi signal strength (in dBm)
    private boolean isSecured; // Whether the network is password protected
    private boolean isNexadomusDevice; // Flag for our Nexadomus WiFi network

    // Constructor for WiFi networks
    public Device(String ssid, int signalStrength, boolean isSecured) {
        this.name = ssid;
        this.type = "WiFi Network";
        this.signalStrength = signalStrength;
        this.isSecured = isSecured;
        this.isNexadomusDevice = ssid.contains("Nexadomus");
        this.iconResId = isNexadomusDevice ? R.drawable.ic_wifi_nexadomus : R.drawable.ic_wifi;
    }

    // Original constructor (kept for backward compatibility)
    public Device(String name, String type, int iconResId) {
        this.name = name;
        this.type = type;
        this.iconResId = iconResId;
        this.signalStrength = 0;
        this.isSecured = false;
        this.isNexadomusDevice = false;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getIconResId() {
        return iconResId;
    }
    
    public int getSignalStrength() {
        return signalStrength;
    }
    
    public boolean isSecured() {
        return isSecured;
    }
    
    public boolean isNexadomusDevice() {
        return isNexadomusDevice;
    }
    
    // Convert signal strength (dBm) to a user-friendly display string
    public String getSignalStrengthLabel() {
        if (signalStrength > -50) {
            return "Excellent";
        } else if (signalStrength > -60) {
            return "Good";
        } else if (signalStrength > -70) {
            return "Fair";
        } else {
            return "Weak";
        }
    }
} 