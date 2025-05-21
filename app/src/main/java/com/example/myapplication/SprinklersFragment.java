package com.example.myapplication;

import android.app.AlertDialog;
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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SprinklersFragment extends Fragment {
    private static final String PREFS_NAME = "NexadomusSprinklers";
    private static final String PREF_SCHEDULE_DAYS = "scheduleDays";
    private static final String PREF_SCHEDULE_DURATION = "scheduleDuration";
    private static final String PREF_SCHEDULE_DURATION_SECONDS = "scheduleDurationSeconds";
    private static final String PREF_START_TIME_HOUR = "startTimeHour";
    private static final String PREF_START_TIME_MINUTE = "startTimeMinute";
    private static final String PREF_START_TIME_SECOND = "startTimeSecond";
    private static final String PREF_REMOTE_MODE = "remoteMode";
    
    private boolean isSprinklerOn = false;
    private List<CheckBox> dayCheckboxes = new ArrayList<>();
    private boolean[] scheduledDays = new boolean[7]; // Sun, Mon, Tue, Wed, Thu, Fri, Sat
    private TextView scheduleText;
    private TextView connectionModeText;
    private Button btnOn;
    private Button btnOff;
    private Button btnSchedule;
    private NexadomusApiClient apiClient;
    private SharedPreferences sharedPreferences;
    private int startTimeHour = 6;    // Default start time: 6:00 AM
    private int startTimeMinute = 0;  // Default start time minutes
    private int startTimeSecond = 0;  // Default start time seconds
    private int scheduleDuration = 30; // Default scheduled duration in minutes
    private int scheduleDurationSeconds = 0; // Default scheduled duration seconds

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sprinklers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the API client
        apiClient = NexadomusApiClient.getInstance();
        apiClient.setAppContext(requireContext());
        
        // Initialize shared preferences for offline schedule storage
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadScheduleFromPreferences();

        btnOn = view.findViewById(R.id.btnOn);
        btnOff = view.findViewById(R.id.btnOff);
        scheduleText = view.findViewById(R.id.scheduleText);
        btnSchedule = view.findViewById(R.id.btnSchedule);
        connectionModeText = view.findViewById(R.id.connectionModeText);

        // Check current status when fragment is created
        fetchCurrentStatus();
        
        // Update the schedule text with current schedule
        updateScheduleText();

        btnOn.setOnClickListener(v -> {
            // Disable buttons during API call
            setButtonsEnabled(false);
            
            // Turn on sprinklers with simple on command
            apiClient.controlSprinklers("on", new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        // This was a remote command via ThingSpeak
                        showRemoteMode(true);
                        Toast.makeText(getContext(), "Remote command sent: Sprinklers on", Toast.LENGTH_SHORT).show();
                    } else {
                        // Local command
                        showRemoteMode(false);
                        Toast.makeText(getContext(), "Manual command: Sprinklers on", Toast.LENGTH_SHORT).show();
                    }
                    // Update UI to reflect that sprinklers should be on
                    isSprinklerOn = true;
                    setButtonsEnabled(true);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to turn on sprinklers: " + error);
                    setButtonsEnabled(true);
                }
            });
        });

        btnOff.setOnClickListener(v -> {
            // Disable buttons during API call
            setButtonsEnabled(false);
            
            apiClient.controlSprinklers("off", new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        showRemoteMode(true);
                        Toast.makeText(getContext(), "Remote command sent: Sprinklers off", Toast.LENGTH_SHORT).show();
                    } else {
                        showRemoteMode(false);
                        Toast.makeText(getContext(), "Manual command: Sprinklers off", Toast.LENGTH_SHORT).show();
                    }
                    isSprinklerOn = false;
                    setButtonsEnabled(true);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to turn off sprinklers: " + error);
                    setButtonsEnabled(true);
                }
            });
        });

        btnSchedule.setOnClickListener(v -> showScheduleDialog());
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
        apiClient.getSprinklersStatus(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    
                    // Update sprinkler status
                    if (json.has("sprinkler")) {
                        int sprinklerAngle = json.getInt("sprinkler");
                        isSprinklerOn = (sprinklerAngle == 180);
                    }
                    
                    // Check if we're in remote mode
                    if (json.has("operating_mode")) {
                        String mode = json.getString("operating_mode");
                        boolean isRemote = !mode.equals("local_only");
                        showRemoteMode(isRemote);
                    }
                    
                    // Update schedule information from ESP32
                    if (json.has("sprinkler_schedule")) {
                        JSONObject scheduleObj = json.getJSONObject("sprinkler_schedule");
                        
                        // Update days
                        if (scheduleObj.has("days")) {
                            JSONArray days = scheduleObj.getJSONArray("days");
                            for (int i = 0; i < Math.min(days.length(), 7); i++) {
                                scheduledDays[i] = days.getBoolean(i);
                            }
                        }
                        
                        // Update schedule times
                        if (scheduleObj.has("startHour")) {
                            startTimeHour = scheduleObj.getInt("startHour");
                        }
                        if (scheduleObj.has("startMinute")) {
                            startTimeMinute = scheduleObj.getInt("startMinute");
                        }
                        if (scheduleObj.has("startSecond")) {
                            startTimeSecond = scheduleObj.getInt("startSecond");
                        }
                        if (scheduleObj.has("duration")) {
                            scheduleDuration = scheduleObj.getInt("duration");
                        }
                        if (scheduleObj.has("durationSeconds")) {
                            scheduleDurationSeconds = scheduleObj.getInt("durationSeconds");
                        }
                        
                        // Update UI with schedule information
                        updateScheduleText();
                        
                        // Save updated schedule to preferences
                        saveScheduleToPreferences();
                    }
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Failed to parse status data", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(getContext(), "Failed to fetch status: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showScheduleDialog() {
        // Inflate the custom view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sprinkler_schedule, null);
        
        // Find all day checkboxes
        dayCheckboxes.clear();
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxSunday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxMonday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxTuesday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxWednesday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxThursday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxFriday));
        dayCheckboxes.add(dialogView.findViewById(R.id.checkboxSaturday));
        
        // Set initial state of day checkboxes
        for (int i = 0; i < Math.min(dayCheckboxes.size(), scheduledDays.length); i++) {
            dayCheckboxes.get(i).setChecked(scheduledDays[i]);
        }
        
        // Setup hour, minute, second pickers
        NumberPicker hourPicker = dialogView.findViewById(R.id.hourPicker);
        if (hourPicker != null) {
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(startTimeHour);
        }
        
        NumberPicker minutePicker = dialogView.findViewById(R.id.minutePicker);
        if (minutePicker != null) {
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(startTimeMinute);
        }
        
        NumberPicker secondPicker = dialogView.findViewById(R.id.secondPicker);
        if (secondPicker != null) {
            secondPicker.setMinValue(0);
            secondPicker.setMaxValue(59);
            secondPicker.setValue(startTimeSecond);
        }
        
        // Setup duration picker
        NumberPicker durationMinutesPicker = dialogView.findViewById(R.id.durationMinutesPicker);
        if (durationMinutesPicker != null) {
            durationMinutesPicker.setMinValue(0);
            durationMinutesPicker.setMaxValue(60); // Allow up to 60 minutes (1 hour)
            durationMinutesPicker.setValue(scheduleDuration);
        }
        
        NumberPicker durationSecondsPicker = dialogView.findViewById(R.id.durationSecondsPicker);
        if (durationSecondsPicker != null) {
            durationSecondsPicker.setMinValue(0);
            durationSecondsPicker.setMaxValue(59); // 0-59 seconds
            durationSecondsPicker.setValue(scheduleDurationSeconds);
        }
        
        // Update time and duration text initially
        TextView timeText = dialogView.findViewById(R.id.timeText);
        TextView durationText = dialogView.findViewById(R.id.durationText);
        
        updateTimeTextWithSeconds(timeText, "Start time: ", startTimeHour, startTimeMinute, startTimeSecond);
        
        // Format duration
        if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
            durationText.setText("Duration: 1 hour");
        } else {
            durationText.setText(String.format("Duration: %d:%02d", scheduleDuration, scheduleDurationSeconds));
        }
        
        // Setup hour change listener with validation
        if (hourPicker != null) {
            hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                startTimeHour = newVal;
                updateTimeTextWithSeconds(timeText, "Start time: ", startTimeHour, startTimeMinute, startTimeSecond);
            });
        }
        
        // Setup minute change listener
        if (minutePicker != null) {
            minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                startTimeMinute = newVal;
                updateTimeTextWithSeconds(timeText, "Start time: ", startTimeHour, startTimeMinute, startTimeSecond);
            });
        }
        
        // Setup second change listener
        if (secondPicker != null) {
            secondPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                startTimeSecond = newVal;
                updateTimeTextWithSeconds(timeText, "Start time: ", startTimeHour, startTimeMinute, startTimeSecond);
            });
        }
        
        // Setup duration minutes change listener
        if (durationMinutesPicker != null) {
            durationMinutesPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                scheduleDuration = newVal;
                
                // Special case: if exactly 60 minutes is selected, force seconds to 0
                // to prevent exceeding 1 hour
                if (scheduleDuration == 60 && scheduleDurationSeconds > 0) {
                    scheduleDurationSeconds = 0;
                    if (durationSecondsPicker != null) {
                        durationSecondsPicker.setValue(0);
                    }
                }
                
                // Update display
                if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
                    durationText.setText("Duration: 1 hour");
                } else {
                    durationText.setText(String.format("Duration: %d:%02d", scheduleDuration, scheduleDurationSeconds));
                }
            });
        }
        
        // Setup duration seconds change listener
        if (durationSecondsPicker != null) {
            durationSecondsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                // If 60 minutes already selected, prevent adding seconds
                if (scheduleDuration == 60 && newVal > 0) {
                    Toast.makeText(getContext(), "Maximum duration is 1 hour", Toast.LENGTH_SHORT).show();
                    picker.setValue(0);
                    return;
                }
                
                scheduleDurationSeconds = newVal;
                
                // Update display
                if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
                    durationText.setText("Duration: 1 hour");
                } else {
                    durationText.setText(String.format("Duration: %d:%02d", scheduleDuration, scheduleDurationSeconds));
                }
            });
        }
        
        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Sprinkler Schedule");
        builder.setView(dialogView);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            // Save the schedule
            for (int i = 0; i < Math.min(dayCheckboxes.size(), scheduledDays.length); i++) {
                scheduledDays[i] = dayCheckboxes.get(i).isChecked();
            }
            
            // Save schedule to preferences
            saveScheduleToPreferences();
            
            // Send to ESP32
            sendScheduleToESP32();
            
            // Update the UI
            updateScheduleText();
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void updateTimeTextWithSeconds(TextView textView, String prefix, int hour, int minute, int second) {
        textView.setText(String.format("%s%02d:%02d:%02d", prefix, hour, minute, second));
    }

    private void sendScheduleToESP32() {
        // Format days as a string of 0s and 1s
        StringBuilder daysBuilder = new StringBuilder();
        for (boolean day : scheduledDays) {
            daysBuilder.append(day ? "1" : "0");
        }
        String daysString = daysBuilder.toString();
        
        // Create the schedule command
        String command = "sprinkler_schedule_" + daysString + "_" + 
                         startTimeHour + "_" + startTimeMinute + "_" + startTimeSecond + "_" + 
                         scheduleDuration + "_" + scheduleDurationSeconds;
        
        // Send it to ESP32
        apiClient.sendCustomCommand(command, new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                Toast.makeText(getContext(), "Schedule updated", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(getContext(), "Failed to update schedule: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateScheduleText() {
        if (scheduleText == null) return;
        
        StringBuilder builder = new StringBuilder();
        builder.append("Schedule: ");
        
        // Check if any days are selected
        boolean anyDaySelected = false;
        for (boolean day : scheduledDays) {
            if (day) {
                anyDaySelected = true;
                break;
            }
        }
        
        if (!anyDaySelected) {
            builder.append("None");
            scheduleText.setText(builder.toString());
            return;
        }
        
        // Add days
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        boolean firstDay = true;
        
        for (int i = 0; i < scheduledDays.length; i++) {
            if (scheduledDays[i]) {
                if (!firstDay) {
                    builder.append(", ");
                }
                builder.append(dayNames[i]);
                firstDay = false;
            }
        }
        
        // Add time
        builder.append(" at ");
        builder.append(String.format("%02d:%02d:%02d", startTimeHour, startTimeMinute, startTimeSecond));
        
        // Add duration
        builder.append(" for ");
        
        if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
            builder.append("1 hour");
        } else if (scheduleDuration == 0) {
            builder.append(String.format("%d seconds", scheduleDurationSeconds));
        } else {
            builder.append(String.format("%d:%02d", scheduleDuration, scheduleDurationSeconds));
        }
        
        scheduleText.setText(builder.toString());
    }
    
    private void loadScheduleFromPreferences() {
        String savedSchedule = sharedPreferences.getString(PREF_SCHEDULE_DAYS, "0000000");
        for (int i = 0; i < Math.min(savedSchedule.length(), 7); i++) {
            scheduledDays[i] = savedSchedule.charAt(i) == '1';
        }
        
        // Load time settings
        startTimeHour = sharedPreferences.getInt(PREF_START_TIME_HOUR, 6);
        startTimeMinute = sharedPreferences.getInt(PREF_START_TIME_MINUTE, 0);
        startTimeSecond = sharedPreferences.getInt(PREF_START_TIME_SECOND, 0);
        scheduleDuration = sharedPreferences.getInt(PREF_SCHEDULE_DURATION, 30);
        scheduleDurationSeconds = sharedPreferences.getInt(PREF_SCHEDULE_DURATION_SECONDS, 0);
    }
    
    private void saveScheduleToPreferences() {
        // Create a string representation of days (e.g., "1010101")
        StringBuilder daysBuilder = new StringBuilder();
        for (boolean day : scheduledDays) {
            daysBuilder.append(day ? "1" : "0");
        }
        String daysString = daysBuilder.toString();
        
        sharedPreferences.edit()
            .putString(PREF_SCHEDULE_DAYS, daysString)
            .putInt(PREF_START_TIME_HOUR, startTimeHour)
            .putInt(PREF_START_TIME_MINUTE, startTimeMinute)
            .putInt(PREF_START_TIME_SECOND, startTimeSecond)
            .putInt(PREF_SCHEDULE_DURATION, scheduleDuration)
            .putInt(PREF_SCHEDULE_DURATION_SECONDS, scheduleDurationSeconds)
            .apply();
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
        }
        
        // Save mode to preferences for persistence
        sharedPreferences.edit().putBoolean(PREF_REMOTE_MODE, isRemote).apply();
    }
    
    private void setButtonsEnabled(boolean enabled) {
        if (btnOn != null) btnOn.setEnabled(enabled);
        if (btnOff != null) btnOff.setEnabled(enabled);
        if (btnSchedule != null) btnSchedule.setEnabled(enabled);
    }
    
    private void showError(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_sprinklers, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_help) {
            // Show a simple toast message instead of using the non-existent dialog method
            Toast.makeText(getContext(), 
                    "Sprinkler Controls: Turn sprinklers on/off or set an automatic schedule.",
                    Toast.LENGTH_LONG).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 