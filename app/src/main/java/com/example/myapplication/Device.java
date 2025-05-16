package com.example.myapplication;

public class Device {
    private String name;
    private String type;
    private int iconResId;
    private boolean isNexadomusDevice;

    // Constructor for simulated devices
    public Device(String name, String type, int iconResId) {
        this.name = name;
        this.type = type;
        this.iconResId = iconResId;
        this.isNexadomusDevice = name.contains("Nexadomus");
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
    
    public boolean isNexadomusDevice() {
        return isNexadomusDevice;
    }
} 