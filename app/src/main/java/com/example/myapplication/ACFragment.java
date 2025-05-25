package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

public class ACFragment extends Fragment {
    private static final String PREFS_NAME = "NexadomusAC";
    private static final String PREF_AC_STATE = "acState";
    private static final String PREF_REMOTE_MODE = "remoteMode";
    
    private TextView statusText;
    private TextView connectionModeText;
    private boolean isACOn = false;
    private SharedPreferences sharedPreferences;
    private NexadomusApiClient apiClient;
    private Button btnOn;
    private Button btnOff;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ac, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize API client
        apiClient = NexadomusApiClient.getInstance();
        apiClient.setAppContext(requireContext());
        
        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isACOn = sharedPreferences.getBoolean(PREF_AC_STATE, false);

        statusText = view.findViewById(R.id.statusText);
        connectionModeText = view.findViewById(R.id.connectionModeText);
        btnOn = view.findViewById(R.id.btnOn);
        btnOff = view.findViewById(R.id.btnOff);
        
        // Check current status when fragment is created
        fetchCurrentStatus();
        
        // Update initial status based on stored preference
        updateStatus();

        btnOn.setOnClickListener(v -> {
            setButtonsEnabled(false);
            apiClient.controlAC("on", new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        // This was a remote command via ThingSpeak
                        showRemoteMode(true);
                        Toast.makeText(getContext(), "Remote command sent: A/C on", Toast.LENGTH_SHORT).show();
                    } else {
                        // Local command
                        showRemoteMode(false);
                        Toast.makeText(getContext(), "Manual command: A/C on", Toast.LENGTH_SHORT).show();
                    }
                    isACOn = true;
                    saveACState();
                    updateStatus();
                    setButtonsEnabled(true);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to turn on A/C: " + error);
                    setButtonsEnabled(true);
                }
            });
        });

        btnOff.setOnClickListener(v -> {
            setButtonsEnabled(false);
            apiClient.controlAC("off", new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        showRemoteMode(true);
                        Toast.makeText(getContext(), "Remote command sent: A/C off", Toast.LENGTH_SHORT).show();
                    } else {
                        showRemoteMode(false);
                        Toast.makeText(getContext(), "Manual command: A/C off", Toast.LENGTH_SHORT).show();
                    }
                    isACOn = false;
                    saveACState();
                    updateStatus();
                    setButtonsEnabled(true);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to turn off A/C: " + error);
                    setButtonsEnabled(true);
                }
            });
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // First check if we were previously in remote mode
        boolean wasInRemoteMode = sharedPreferences.getBoolean(PREF_REMOTE_MODE, false);
        if (wasInRemoteMode) {
            showRemoteMode(true);
        }
        
        // Try to refresh status when fragment becomes visible
        fetchCurrentStatus();
    }
    
    private void fetchCurrentStatus() {
        apiClient.getACStatus(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    
                    // Update A/C status
                    if (json.has("ac")) {
                        boolean acState = json.getBoolean("ac");
                        isACOn = acState;
                        saveACState();
                        updateStatus();
                    }
                    
                    // Check if we're in remote mode
                    if (json.has("operating_mode")) {
                        String mode = json.getString("operating_mode");
                        boolean isRemote = !mode.equals("local_only");
                        showRemoteMode(isRemote);
                    }
                    
                } catch (Exception e) {
                    // Log error but don't show to user to avoid confusion
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(String error) {
                // Non-fatal error, just log it
                error = "Status update failed: " + error;
                System.out.println(error);
            }
        });
    }
    
    private void saveACState() {
        sharedPreferences.edit()
            .putBoolean(PREF_AC_STATE, isACOn)
            .apply();
    }

    private void updateStatus() {
        statusText.setText("Status: " + (isACOn ? "On" : "Off"));
    }
    
    private void showRemoteMode(boolean isRemote) {
        if (connectionModeText != null) {
            if (isRemote) {
                connectionModeText.setText("Mode: Remote (ThingSpeak)");
                connectionModeText.setBackgroundResource(R.drawable.bg_remote_mode);
            } else {
                connectionModeText.setText("Mode: Direct Connection");
                connectionModeText.setBackgroundResource(R.drawable.bg_local_mode);
            }
            // Save the mode for later reference
            sharedPreferences.edit()
                .putBoolean(PREF_REMOTE_MODE, isRemote)
                .apply();
        }
    }
    
    private void setButtonsEnabled(boolean enabled) {
        if (btnOn != null) btnOn.setEnabled(enabled);
        if (btnOff != null) btnOff.setEnabled(enabled);
    }
    
    private void showError(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
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
} 