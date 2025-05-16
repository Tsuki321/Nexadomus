package com.example.myapplication;

import android.content.Context;
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

public class ThingSpeakClient {
    private static final String TAG = "ThingSpeakClient";
    
    // ThingSpeak configuration 
    private static final String THINGSPEAK_URL = "https://api.thingspeak.com/update";
    private static final String WRITE_API_KEY = "8SN8YGHKGHD9Q29Q"; // Your ThingSpeak Write API key
    private static final String CHANNEL_ID = "2938898"; // Your ThingSpeak Channel ID
    
    // ThingSpeak field numbers
    private static final int COMMAND_FIELD = 3; // Field for sending commands
    
    private static ThingSpeakClient instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;

    public interface ThingSpeakCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    private ThingSpeakClient() {
        // Private constructor for singleton
    }

    public static synchronized ThingSpeakClient getInstance() {
        if (instance == null) {
            instance = new ThingSpeakClient();
        }
        return instance;
    }
    
    public void setAppContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // Send command to control the garage remotely
    public void controlGarageRemote(boolean open, ThingSpeakCallback callback) {
        String command = open ? "garage_open" : "garage_close";
        sendCommand(command, callback);
    }

    // Send command to control the lights remotely
    public void controlLightsRemote(String brightness, ThingSpeakCallback callback) {
        String command = "lights_" + brightness.toLowerCase();
        sendCommand(command, callback);
    }
    
    // Send command to control the sprinklers remotely
    public void controlSprinklersRemote(boolean on, ThingSpeakCallback callback) {
        String command = on ? "sprinkler_on" : "sprinkler_off";
        sendCommand(command, callback);
    }

    // Send a custom command to ThingSpeak
    public void sendCustomCommand(String command, ThingSpeakCallback callback) {
        sendCommand(command, callback);
    }

    // Send command to ThingSpeak
    private void sendCommand(String command, ThingSpeakCallback callback) {
        try {
            // URL encode the command to handle special characters
            String encodedCommand = java.net.URLEncoder.encode(command, "UTF-8");
            String endpoint = THINGSPEAK_URL + "?api_key=" + WRITE_API_KEY + "&field" + COMMAND_FIELD + "=" + encodedCommand;
            
            // Log command for debugging
            Log.d(TAG, "Sending command to ThingSpeak: " + command);
            Log.d(TAG, "Complete endpoint: " + endpoint);
            
            executeRequest(endpoint, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding command: " + e.getMessage());
            callback.onError("Failed to encode command: " + e.getMessage());
        }
    }

    // Execute HTTP request in background thread
    private void executeRequest(final String urlString, final ThingSpeakCallback callback) {
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