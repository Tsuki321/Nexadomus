package com.example.myapplication;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NexadomusApiClient {
    private static final String TAG = "NexadomusApiClient";
    private static final String ESP_IP_ADDRESS = "192.168.4.1"; // Default AP IP
    private static final String BASE_URL = "http://" + ESP_IP_ADDRESS;
    private static final String NEXADOMUS_SSID = "Nexadomus Prototype";
    
    private static NexadomusApiClient instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private ThingSpeakClient thingSpeakClient;

    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    private NexadomusApiClient() {
        // Private constructor for singleton
        thingSpeakClient = ThingSpeakClient.getInstance();
    }

    public static synchronized NexadomusApiClient getInstance() {
        if (instance == null) {
            instance = new NexadomusApiClient();
        }
        return instance;
    }
    
    public void setAppContext(Context context) {
        this.appContext = context.getApplicationContext();
        thingSpeakClient.setAppContext(context);
    }

    // Check if we have internet connectivity (not just Nexadomus AP)
    private boolean hasInternetAccess() {
        if (appContext == null) {
            return false;
        }
        
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // Check if we're connected to Nexadomus network
    private boolean isConnectedToNexadomus() {
        if (appContext == null) {
            Log.e(TAG, "App context is null, cannot check WiFi connection");
            return false;
        }
        
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WiFi manager is null, cannot check WiFi connection");
            return false;
        }
        
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.e(TAG, "WiFi info is null, cannot check WiFi connection");
            return false;
        }
        
        String ssid = wifiInfo.getSSID();
        if (ssid != null) {
            // Remove quotes if present
            ssid = ssid.replace("\"", "");
            Log.d(TAG, "Current SSID: " + ssid + ", Looking for: " + NEXADOMUS_SSID);
            boolean isConnected = NEXADOMUS_SSID.equals(ssid);
            Log.d(TAG, "Connected to Nexadomus AP: " + isConnected);
            return isConnected;
        }
        
        Log.e(TAG, "SSID is null, cannot check WiFi connection");
        return false;
    }

    // Control the garage door - will use ThingSpeak if no local connection available
    public void controlGarage(String action, ApiCallback callback) {
        boolean connected = isConnectedToNexadomus();
        Log.d(TAG, "Control Garage: Connected to Nexadomus? " + connected);
        
        // Check if we're connected to Nexadomus network
        if (connected) {
            // Use direct control via local network
            String endpoint = BASE_URL + "/garage?state=" + action;
            Log.d(TAG, "Sending local garage command to: " + endpoint);
            executeRequest(endpoint, callback);
        } else if (hasInternetAccess()) {
            // Use ThingSpeak for remote control
            Log.d(TAG, "Using ThingSpeak for remote garage control: " + action);
            boolean open = action.equals("open") || action.equals("toggle");
            thingSpeakClient.controlGarageRemote(open, new ThingSpeakClient.ThingSpeakCallback() {
                @Override
                public void onSuccess(String response) {
                    callback.onSuccess("Remote command sent: " + action);
                }

                @Override
                public void onError(String error) {
                    callback.onError("Remote command failed: " + error);
                }
            });
        } else {
            // No connectivity at all
            Log.e(TAG, "No connectivity for garage control");
            mainHandler.post(() -> callback.onError("No connectivity. Connect to Nexadomus AP or ensure internet access."));
        }
    }

    // Control the lights - will use ThingSpeak if no local connection available
    public void controlLights(int brightness, ApiCallback callback) {
        if (isConnectedToNexadomus()) {
            // Use direct control via local network
            String endpoint = BASE_URL + "/lights?brightness=" + brightness;
            executeRequest(endpoint, callback);
        } else if (hasInternetAccess()) {
            // Map brightness to command
            String brightnessLevel;
            if (brightness == 0) {
                brightnessLevel = "off";
            } else if (brightness <= 85) {
                brightnessLevel = "low";
            } else if (brightness <= 170) {
                brightnessLevel = "medium";
            } else {
                brightnessLevel = "high";
            }
            
            // Use ThingSpeak for remote control
            thingSpeakClient.controlLightsRemote(brightnessLevel, new ThingSpeakClient.ThingSpeakCallback() {
                @Override
                public void onSuccess(String response) {
                    callback.onSuccess("Remote command sent: lights_" + brightnessLevel);
                }

                @Override
                public void onError(String error) {
                    callback.onError("Remote command failed: " + error);
                }
            });
        } else {
            // No connectivity at all
            mainHandler.post(() -> callback.onError("No connectivity. Connect to Nexadomus AP or ensure internet access."));
        }
    }

    /**
     * Control the sprinklers (on/off with duration)
     *
     * @param action    Command string (on, off, on&duration_seconds=X) 
     * @param callback  Callback for success/error
     */
    public void controlSprinklers(String action, ApiCallback callback) {
        boolean connected = isConnectedToNexadomus();
        Log.d(TAG, "Control Sprinklers: Connected to Nexadomus? " + connected);
        
        if (connected) {
            // Use direct control via local network
            String endpoint = BASE_URL + "/sprinklers?state=" + action;
            Log.d(TAG, "Sending local sprinkler command to: " + endpoint);
            executeRequest(endpoint, callback);
        } else if (hasInternetAccess()) {
            Log.d(TAG, "Using ThingSpeak for remote sprinkler control: " + action);
            // Use ThingSpeak for remote control
            if (action.equals("off")) {
                // Send direct off command
                sendCustomCommand("sprinkler_off", callback);
            } else if (action.startsWith("on&duration_seconds=")) {
                // Extract duration in seconds
                String durationStr = action.substring("on&duration_seconds=".length());
                try {
                    int durationSeconds = Integer.parseInt(durationStr);
                    sendCustomCommand("sprinkler_on_" + durationSeconds, callback);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid duration format: " + durationStr);
                    callback.onError("Invalid duration format");
                }
            } else if (action.startsWith("on&duration=")) {
                // Handle duration in minutes (original format)
                String durationStr = action.substring("on&duration=".length());
                try {
                    int duration = Integer.parseInt(durationStr);
                    // Convert minutes to seconds
                    int durationSeconds = duration * 60;
                    sendCustomCommand("sprinkler_on_" + durationSeconds, callback);
                } catch (NumberFormatException e) {
                    // Fall back to simple on command if duration parsing fails
                    Log.e(TAG, "Failed to parse duration, falling back to simple on: " + e.getMessage());
                    sendCustomCommand("sprinkler_on", callback);
                }
            } else if (action.equals("on") || action.equals("toggle")) {
                // Simple on command - send directly instead of using controlSprinklersRemote
                sendCustomCommand("sprinkler_on", callback);
            } else {
                // Unknown command
                Log.e(TAG, "Unknown sprinkler command: " + action);
                callback.onError("Unknown sprinkler command: " + action);
            }
        } else {
            // No connectivity at all
            Log.e(TAG, "No connectivity for sprinkler control");
            mainHandler.post(() -> callback.onError("No connectivity. Connect to Nexadomus AP or ensure internet access."));
        }
    }

    /**
     * Helper method to create a ThingSpeak callback
     */
    private ThingSpeakClient.ThingSpeakCallback createThingSpeakCallback(ApiCallback callback, String commandName) {
        return new ThingSpeakClient.ThingSpeakCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess("Remote command sent: " + commandName);
            }

            @Override
            public void onError(String error) {
                callback.onError("Remote command failed: " + error);
            }
        };
    }

    // Send a custom command to ThingSpeak
    public void sendCustomCommand(String command, ApiCallback callback) {
        if (hasInternetAccess()) {
            // Use ThingSpeak for remote control
            thingSpeakClient.sendCustomCommand(command, new ThingSpeakClient.ThingSpeakCallback() {
                @Override
                public void onSuccess(String response) {
                    callback.onSuccess("Remote command sent: " + command);
                }

                @Override
                public void onError(String error) {
                    callback.onError("Remote command failed: " + error);
                }
            });
        } else {
            // No connectivity
            mainHandler.post(() -> callback.onError("No internet connectivity. Unable to send custom command."));
        }
    }

    // Get device status - only works on local network
    public void getStatus(ApiCallback callback) {
        // This only works when connected to the local network
        if (isConnectedToNexadomus()) {
            String endpoint = BASE_URL + "/status";
            executeRequest(endpoint, callback);
        } else {
            mainHandler.post(() -> callback.onError("Not connected to Nexadomus network. Status check requires local connection."));
        }
    }
    
    // Execute HTTP request in background thread
    private void executeRequest(final String urlString, final ApiCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    final String result = response.toString();
                    mainHandler.post(() -> callback.onSuccess(result));
                } else {
                    final String error = "HTTP Error: " + responseCode;
                    mainHandler.post(() -> callback.onError(error));
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                final String errorMsg = "Network error: " + e.getMessage();
                mainHandler.post(() -> callback.onError(errorMsg));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
} 