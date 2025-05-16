package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
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
    private static final String PREF_TIMER_ACTIVE = "timerActive";
    private static final String PREF_TIMER_END_TIME = "timerEndTime";
    private static final String PREF_START_TIME_HOUR = "startTimeHour";
    private static final String PREF_START_TIME_MINUTE = "startTimeMinute";
    private static final String PREF_START_TIME_SECOND = "startTimeSecond";
    private static final String PREF_MANUAL_DURATION_MINUTES = "manualDurationMinutes";
    private static final String PREF_MANUAL_DURATION_SECONDS = "manualDurationSeconds";
    private static final String PREF_REMOTE_MODE = "remoteMode";
    
    private boolean isSprinklerOn = false;
    private List<CheckBox> dayCheckboxes = new ArrayList<>();
    private boolean[] scheduledDays = new boolean[7]; // Sun, Mon, Tue, Wed, Thu, Fri, Sat
    private LinearLayout durationLayout;
    private TextView durationText;
    private TextView timerText;
    private TextView scheduleText;
    private TextView connectionModeText;
    private Button btnConfirmDuration;
    private Button btnOn;
    private Button btnOff;
    private Button btnSchedule;
    private CountDownTimer countDownTimer;
    private int selectedDurationMinutes = 5; // Default duration in minutes for manual activation
    private int selectedDurationSeconds = 0; // Default seconds for manual activation
    private int scheduleDuration = 30; // Default scheduled duration in minutes
    private int scheduleDurationSeconds = 0; // Default scheduled duration seconds
    private int remainingTimerSeconds = 0;
    private NexadomusApiClient apiClient;
    private SharedPreferences sharedPreferences;
    private int startTimeHour = 6;    // Default start time: 6:00 AM
    private int startTimeMinute = 0;  // Default start time minutes
    private int startTimeSecond = 0;  // Default start time seconds
    
    // Number pickers for the manual duration selection
    private NumberPicker minutesPicker;
    private NumberPicker secondsPicker;

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
        durationLayout = view.findViewById(R.id.durationLayout);
        durationText = view.findViewById(R.id.durationText);
        
        // Replace SeekBar with NumberPickers for minutes and seconds
        minutesPicker = view.findViewById(R.id.minutesPicker);
        secondsPicker = view.findViewById(R.id.secondsPicker);
        
        // Configure the number pickers
        if (minutesPicker != null) {
            minutesPicker.setMinValue(0);
            minutesPicker.setMaxValue(15); // 0-15 minutes
            minutesPicker.setValue(selectedDurationMinutes);
            minutesPicker.setWrapSelectorWheel(true);
        }
        
        if (secondsPicker != null) {
            secondsPicker.setMinValue(0);
            secondsPicker.setMaxValue(59); // 0-59 seconds
            secondsPicker.setValue(selectedDurationSeconds);
            secondsPicker.setWrapSelectorWheel(true);
        }
        
        btnConfirmDuration = view.findViewById(R.id.btnConfirmDuration);
        timerText = view.findViewById(R.id.timerText);
        scheduleText = view.findViewById(R.id.scheduleText);
        btnSchedule = view.findViewById(R.id.btnSchedule);
        connectionModeText = view.findViewById(R.id.connectionModeText);

        // Initially hide duration controls and timer
        durationLayout.setVisibility(View.GONE);
        timerText.setVisibility(View.GONE);

        // Check current status when fragment is created
        fetchCurrentStatus();
        
        // Update the schedule text with current schedule
        updateScheduleText();

        // Set up number picker change listeners
        if (minutesPicker != null) {
            minutesPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                selectedDurationMinutes = newVal;
                
                // Enforce 15 minute maximum for manual timer
                if (selectedDurationMinutes == 15 && selectedDurationSeconds > 0) {
                    selectedDurationSeconds = 0;
                    if (secondsPicker != null) {
                        secondsPicker.setValue(0);
                    }
                    Toast.makeText(getContext(), "Maximum duration is 15 minutes", Toast.LENGTH_SHORT).show();
                }
                
                updateDurationText();
                saveManualDurationToPreferences();
            });
        }
        
        if (secondsPicker != null) {
            secondsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                selectedDurationSeconds = newVal;
                
                // Enforce 15 minute maximum for manual timer
                if (selectedDurationMinutes == 15 && selectedDurationSeconds > 0) {
                    selectedDurationSeconds = 0;
                    picker.setValue(0);
                    Toast.makeText(getContext(), "Maximum duration is 15 minutes", Toast.LENGTH_SHORT).show();
                }
                
                updateDurationText();
                saveManualDurationToPreferences();
            });
        }

        btnOn.setOnClickListener(v -> {
            // Disable buttons during API call
            setButtonsEnabled(false);
            
            // Ensure we don't exceed 15 minutes
            if (selectedDurationMinutes == 15 && selectedDurationSeconds > 0) {
                selectedDurationSeconds = 0;
                if (secondsPicker != null) {
                    secondsPicker.setValue(0);
                }
                updateDurationText();
            }
            
            // Calculate total seconds
            int totalSeconds = (selectedDurationMinutes * 60) + selectedDurationSeconds;
            
            // Turn on sprinklers with duration via API (convert to minutes and seconds)
            apiClient.controlSprinklers("on&duration_seconds=" + totalSeconds, new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        // This was a remote command via ThingSpeak
                        showRemoteMode(true);
                        
                        // Show different messages based on whether a timer is set
                        if (totalSeconds > 0) {
                            Toast.makeText(getContext(), "Remote command sent: Sprinklers on with timer for " + 
                                    formatDuration(totalSeconds), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Remote command sent: Sprinklers on indefinitely", 
                                    Toast.LENGTH_SHORT).show();
                        }
                        
                        // Update UI to reflect that sprinklers should be on
                        isSprinklerOn = true;
                        
                    } else {
                        // Update local state
                        isSprinklerOn = true;
                        showRemoteMode(false);
                        
                        // Start a timer display if we get remaining seconds
                        if (response.contains("remaining_seconds")) {
                            try {
                                JSONObject json = new JSONObject(response);
                                if (json.has("sprinkler_schedule")) {
                                    JSONObject schedule = json.getJSONObject("sprinkler_schedule");
                                    if (schedule.getBoolean("timer_active")) {
                                        remainingTimerSeconds = schedule.getInt("remaining_seconds");
                                        
                                        // Check if this is likely an override of an existing timer
                                        if (countDownTimer != null) {
                                            countDownTimer.cancel();
                                            Toast.makeText(getContext(), "Override complete, timer starts", Toast.LENGTH_SHORT).show();
                                        }
                                        
                                        startTimerDisplay(remainingTimerSeconds);
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Command sent, but timer parsing failed", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // No timer was set, show indefinite message
                            Toast.makeText(getContext(), "Manual command: Sprinklers on indefinitely", Toast.LENGTH_SHORT).show();
                        }
                    }
                    durationLayout.setVisibility(View.VISIBLE);
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
            
            // Turn off sprinklers via API
            apiClient.controlSprinklers("off", new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    if (response.contains("Remote command sent")) {
                        // This was a remote command via ThingSpeak
                        showRemoteMode(true);
                        Toast.makeText(getContext(), "Remote command sent successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        // Update local state 
                        isSprinklerOn = false;
                        showRemoteMode(false);
                    }
                    durationLayout.setVisibility(View.GONE);
                    timerText.setVisibility(View.GONE);
                    if (countDownTimer != null) {
                        countDownTimer.cancel();
                    }
                    setButtonsEnabled(true);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to turn off sprinklers: " + error);
                    setButtonsEnabled(true);
                }
            });
        });

        btnConfirmDuration.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            
            // Ensure we don't exceed 15 minutes
            if (selectedDurationMinutes == 15 && selectedDurationSeconds > 0) {
                selectedDurationSeconds = 0;
                if (secondsPicker != null) {
                    secondsPicker.setValue(0);
                }
                updateDurationText();
            }
            
            // Calculate total seconds for the timer
            int totalSeconds = (selectedDurationMinutes * 60) + selectedDurationSeconds;
            
            // Send updated duration to ESP32
            apiClient.controlSprinklers("on&duration_seconds=" + totalSeconds, new NexadomusApiClient.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(getContext(), "Override complete, timer starts", Toast.LENGTH_SHORT).show();
                    startTimerDisplay(totalSeconds);
                    durationLayout.setVisibility(View.GONE);
                }

                @Override
                public void onError(String error) {
                    showError("Failed to update timer duration: " + error);
                }
            });
        });

        btnSchedule.setOnClickListener(v -> showScheduleDialog());
    }
    
    // Helper method to update duration text
    private void updateDurationText() {
        if (durationText != null) {
            // Display "15 minutes" when at max duration
            if (selectedDurationMinutes == 15 && selectedDurationSeconds == 0) {
                durationText.setText("Duration: 15 minutes");
            } else {
                durationText.setText(String.format("Duration: %d:%02d", selectedDurationMinutes, selectedDurationSeconds));
            }
        }
    }
    
    // Save manual duration to preferences
    private void saveManualDurationToPreferences() {
        sharedPreferences.edit()
            .putInt(PREF_MANUAL_DURATION_MINUTES, selectedDurationMinutes)
            .putInt(PREF_MANUAL_DURATION_SECONDS, selectedDurationSeconds)
            .apply();
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // First check if we were previously in remote mode
        boolean wasInRemoteMode = sharedPreferences.getBoolean(PREF_REMOTE_MODE, false);
        if (wasInRemoteMode) {
            showRemoteMode(true);
        }
        
        // Check if there's a saved timer that needs to be displayed
        if (sharedPreferences.getBoolean(PREF_TIMER_ACTIVE, false)) {
            long endTimeMillis = sharedPreferences.getLong(PREF_TIMER_END_TIME, 0);
            long currentTimeMillis = System.currentTimeMillis();
            
            if (endTimeMillis > currentTimeMillis) {
                int remainingSecs = (int)((endTimeMillis - currentTimeMillis) / 1000);
                if (remainingSecs > 1) {
                    // Restart the timer display with the remaining time
                    startTimerDisplay(remainingSecs);
                }
            } else {
                // Timer has expired while we were away, clear the state
                sharedPreferences.edit()
                    .putBoolean(PREF_TIMER_ACTIVE, false)
                    .apply();
            }
        }
        
        // Still try to refresh status when fragment becomes visible
        // If we're in remote mode, this will just reconfirm that status
        fetchCurrentStatus();
    }
    
    private void fetchCurrentStatus() {
        // Disable controls while fetching
        setButtonsEnabled(false);
        
        // Get current status from ESP32
        apiClient.getStatus(new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    
                    // Update sprinkler state
                    if (json.has("sprinkler")) {
                        isSprinklerOn = json.getInt("sprinkler") == 180;
                    }
                    
                    // Update schedule from JSON if available
                    if (json.has("sprinkler_schedule")) {
                        JSONObject schedule = json.getJSONObject("sprinkler_schedule");
                        
                        if (schedule.has("days")) {
                            JSONArray days = schedule.getJSONArray("days");
                            for (int i = 0; i < Math.min(days.length(), 7); i++) {
                                scheduledDays[i] = days.getBoolean(i);
                            }
                        }
                        
                        if (schedule.has("startHour")) {
                            startTimeHour = schedule.getInt("startHour");
                        }
                        
                        if (schedule.has("startMinute")) {
                            startTimeMinute = schedule.getInt("startMinute");
                        }
                        
                        if (schedule.has("startSecond")) {
                            startTimeSecond = schedule.getInt("startSecond");
                        }
                        
                        if (schedule.has("duration")) {
                            scheduleDuration = schedule.getInt("duration");
                        }
                        
                        if (schedule.has("durationSeconds")) {
                            scheduleDurationSeconds = schedule.getInt("durationSeconds");
                        }
                        
                        // Handle timer status - could be manual timer or scheduled timer
                        boolean timerActive = false;
                        int remainingSecs = 0;
                        
                        if (schedule.has("timer_active") && schedule.getBoolean("timer_active")) {
                            timerActive = true;
                            
                            if (schedule.has("remaining_seconds")) {
                                remainingSecs = schedule.getInt("remaining_seconds");
                            }
                        }
                        
                        // If there's an active timer and the sprinklers are on, show or update the timer display
                        if (timerActive && isSprinklerOn && remainingSecs > 0) {
                            // Cancel any existing timer to prevent duplicates
                            if (countDownTimer != null) {
                                countDownTimer.cancel();
                            }
                            
                            // Start a new timer with the remaining seconds
                            startTimerDisplay(remainingSecs);
                            
                            // Show the duration layout only if we're in local mode
                            durationLayout.setVisibility(View.GONE);
                            
                        } else if (isSprinklerOn) {
                            // Sprinklers are on but no timer - show duration controls
                            durationLayout.setVisibility(View.VISIBLE);
                            
                            // Hide any existing timer display
                            if (countDownTimer != null) {
                                countDownTimer.cancel();
                            }
                            timerText.setVisibility(View.GONE);
                            
                            // Clear timer preferences
                            sharedPreferences.edit()
                                .putBoolean(PREF_TIMER_ACTIVE, false)
                                .apply();
                        } else {
                            // Sprinklers are off - hide all timer UI
                            durationLayout.setVisibility(View.GONE);
                            
                            if (countDownTimer != null) {
                                countDownTimer.cancel();
                            }
                            timerText.setVisibility(View.GONE);
                            
                            // Clear timer preferences
                            sharedPreferences.edit()
                                .putBoolean(PREF_TIMER_ACTIVE, false)
                                .apply();
                        }
                        
                        // Save schedule to preferences
                        saveScheduleToPreferences();
                        
                        // Update schedule text
                        updateScheduleText();
                    }
                    
                    showRemoteMode(false); // We're in local mode if we can get status
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                
                setButtonsEnabled(true);
            }
            
            @Override
            public void onError(String error) {
                if (error.contains("Failed to connect")) {
                    // We're in remote mode
                    showRemoteMode(true);
                } else {
                    showError("Failed to get status: " + error);
                }
                
                setButtonsEnabled(true);
            }
        });
    }

    private void startTimerDisplay(int seconds) {
        // Ensure we have a positive number of seconds
        if (seconds <= 0) {
            // Invalid timer duration
            timerText.setVisibility(View.GONE);
            return;
        }
        
        timerText.setVisibility(View.VISIBLE);
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        // Save the timer end time in preferences
        long endTimeMillis = System.currentTimeMillis() + (seconds * 1000);
        sharedPreferences.edit()
            .putBoolean(PREF_TIMER_ACTIVE, true)
            .putLong(PREF_TIMER_END_TIME, endTimeMillis)
            .apply();

        countDownTimer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 60000;
                long secs = (millisUntilFinished % 60000) / 1000;
                timerText.setText(String.format("Timer: %02d:%02d", minutes, secs));
                
                // Update the remaining seconds in case we need to restore after fragment recreation
                remainingTimerSeconds = (int) (millisUntilFinished / 1000);
            }

            @Override
            public void onFinish() {
                timerText.setText("Timer: Complete");
                
                // Check if the sprinklers are still on via the ESP32 before sending an off command
                apiClient.getStatus(new NexadomusApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            
                            // Check if sprinklers are actually on
                            boolean sprinklersOn = false;
                            if (json.has("sprinkler")) {
                                sprinklersOn = json.getInt("sprinkler") == 180;
                            }
                            
                            // Only send the off command if sprinklers are actually on
                            if (sprinklersOn) {
                                // Send command to turn off the sprinklers
                                apiClient.controlSprinklers("off", new NexadomusApiClient.ApiCallback() {
                                    @Override
                                    public void onSuccess(String response) {
                                        // Update UI after successful command
                                        isSprinklerOn = false;
                                        durationLayout.setVisibility(View.GONE);
                                        timerText.setVisibility(View.GONE);
                                        Toast.makeText(getContext(), "Timer finished, turning off sprinklers", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        // Check if we're in remote mode
                                        if (error.contains("Failed to connect")) {
                                            // We're in remote mode, send the off command directly
                                            Toast.makeText(getContext(), "Remote mode detected, sending stop command", Toast.LENGTH_SHORT).show();
                                            showRemoteMode(true);
                                        }
                                        
                                        // In any case, still turn off as a precaution
                                        sendSprinklersOffCommand();
                                    }
                                });
                            } else {
                                // Sprinklers are already off, just update UI
                                isSprinklerOn = false;
                                durationLayout.setVisibility(View.GONE);
                                timerText.setVisibility(View.GONE);
                            }
                            
                        } catch (JSONException e) {
                            e.printStackTrace();
                            // In case of error, still turn off as a precaution
                            sendSprinklersOffCommand();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Check if we're in remote mode
                        if (error.contains("Failed to connect")) {
                            // We're in remote mode, send the off command directly
                            Toast.makeText(getContext(), "Remote mode detected, sending stop command", Toast.LENGTH_SHORT).show();
                            showRemoteMode(true);
                        }
                        
                        // In any case, still turn off as a precaution
                        sendSprinklersOffCommand();
                    }
                });

                // Clear the timer state in preferences
                sharedPreferences.edit()
                    .putBoolean(PREF_TIMER_ACTIVE, false)
                    .apply();
            }
        }.start();
    }
    
    // Helper method to send off command
    private void sendSprinklersOffCommand() {
        apiClient.controlSprinklers("off", new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                // Update UI after successful command
                isSprinklerOn = false;
                durationLayout.setVisibility(View.GONE);
                timerText.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Timer finished, turning off sprinklers", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                showError("Failed to turn off sprinklers: " + error);
                // Still update UI even if command fails
                isSprinklerOn = false;
                durationLayout.setVisibility(View.GONE);
                timerText.setVisibility(View.GONE);
            }
        });
    }

    private void showScheduleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Schedule Sprinklers");

        // Use ScrollView to make sure all content is accessible
        ScrollView scrollView = new ScrollView(requireContext());
        
        // Create layout for checkboxes
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        dayCheckboxes.clear();

        for (int i = 0; i < days.length; i++) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(days[i]);
            checkBox.setChecked(scheduledDays[i]);
            checkBox.setPadding(0, 10, 0, 10);
            layout.addView(checkBox);
            dayCheckboxes.add(checkBox);
        }
        
        // Add start time selector with NumberPickers
        TextView startTimeLabel = new TextView(requireContext());
        startTimeLabel.setText("Start Time:");
        startTimeLabel.setPadding(0, 20, 0, 10);
        layout.addView(startTimeLabel);
        
        // Linear layout for the time pickers in a row - use proper weighting
        LinearLayout timePickersLayout = new LinearLayout(requireContext());
        timePickersLayout.setOrientation(LinearLayout.HORIZONTAL);
        timePickersLayout.setGravity(android.view.Gravity.CENTER);
        
        // Create layouts for each picker with consistent spacing
        LinearLayout hourLayout = new LinearLayout(requireContext());
        hourLayout.setOrientation(LinearLayout.VERTICAL);
        hourLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f)); // Equal weight
        hourLayout.setGravity(android.view.Gravity.CENTER);
        
        LinearLayout minuteLayout = new LinearLayout(requireContext());
        minuteLayout.setOrientation(LinearLayout.VERTICAL);
        minuteLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f)); // Equal weight
        minuteLayout.setGravity(android.view.Gravity.CENTER);
        
        LinearLayout secondLayout = new LinearLayout(requireContext());
        secondLayout.setOrientation(LinearLayout.VERTICAL);
        secondLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f)); // Equal weight
        secondLayout.setGravity(android.view.Gravity.CENTER);

        // Hour picker (0-23)
        NumberPicker hourPicker = new NumberPicker(requireContext());
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(startTimeHour);
        hourPicker.setWrapSelectorWheel(true);
        
        // Minute picker (0-59)
        NumberPicker minutePicker = new NumberPicker(requireContext());
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(startTimeMinute);
        minutePicker.setWrapSelectorWheel(true);
        
        // Second picker (0-59)
        NumberPicker secondPicker = new NumberPicker(requireContext());
        secondPicker.setMinValue(0);
        secondPicker.setMaxValue(59);
        secondPicker.setValue(startTimeSecond);
        secondPicker.setWrapSelectorWheel(true);
        
        // Add labels for each picker with better alignment
        TextView hourLabel = new TextView(requireContext());
        hourLabel.setText("H");
        hourLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hourLabel.setGravity(android.view.Gravity.CENTER);
        
        TextView minuteLabel = new TextView(requireContext());
        minuteLabel.setText("M");
        minuteLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        minuteLabel.setGravity(android.view.Gravity.CENTER);
        
        TextView secondLabel = new TextView(requireContext());
        secondLabel.setText("S");
        secondLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        secondLabel.setGravity(android.view.Gravity.CENTER);
        
        // Add the pickers and labels to their layouts
        hourLayout.addView(hourPicker);
        hourLayout.addView(hourLabel);
        
        minuteLayout.addView(minutePicker);
        minuteLayout.addView(minuteLabel);
        
        secondLayout.addView(secondPicker);
        secondLayout.addView(secondLabel);
        
        // Add all time selection components to the row
        timePickersLayout.addView(hourLayout);
        timePickersLayout.addView(minuteLayout);
        timePickersLayout.addView(secondLayout);
        
        layout.addView(timePickersLayout);
        
        TextView startTimeValue = new TextView(requireContext());
        updateTimeTextWithSeconds(startTimeValue, "Start Time", startTimeHour, startTimeMinute, startTimeSecond);
        startTimeValue.setPadding(0, 10, 0, 20);
        layout.addView(startTimeValue);
        
        // Add schedule duration selector with NumberPicker
        TextView durationLabel = new TextView(requireContext());
        durationLabel.setText("Schedule Duration:");
        durationLabel.setPadding(0, 20, 0, 10);
        layout.addView(durationLabel);
        
        // Use NumberPicker for duration with clear layout
        LinearLayout durationLayout = new LinearLayout(requireContext());
        durationLayout.setOrientation(LinearLayout.HORIZONTAL);
        durationLayout.setGravity(android.view.Gravity.CENTER);
        
        // Minutes picker
        LinearLayout durationMinutesLayout = new LinearLayout(requireContext());
        durationMinutesLayout.setOrientation(LinearLayout.VERTICAL);
        durationMinutesLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f)); // Equal weight
        durationMinutesLayout.setGravity(android.view.Gravity.CENTER);
        
        // Seconds picker
        LinearLayout durationSecondsLayout = new LinearLayout(requireContext());
        durationSecondsLayout.setOrientation(LinearLayout.VERTICAL);
        durationSecondsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f)); // Equal weight
        durationSecondsLayout.setGravity(android.view.Gravity.CENTER);
        
        NumberPicker scheduleDurationMinutesPicker = new NumberPicker(requireContext());
        scheduleDurationMinutesPicker.setMinValue(0);
        scheduleDurationMinutesPicker.setMaxValue(60); // 0-60 minutes
        scheduleDurationMinutesPicker.setValue(scheduleDuration);
        scheduleDurationMinutesPicker.setWrapSelectorWheel(true);
        
        NumberPicker scheduleDurationSecondsPicker = new NumberPicker(requireContext());
        scheduleDurationSecondsPicker.setMinValue(0);
        scheduleDurationSecondsPicker.setMaxValue(59); // 0-59 seconds
        scheduleDurationSecondsPicker.setValue(scheduleDurationSeconds);
        scheduleDurationSecondsPicker.setWrapSelectorWheel(true);
        
        TextView durationMinutesLabel = new TextView(requireContext());
        durationMinutesLabel.setText("M");
        durationMinutesLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        durationMinutesLabel.setGravity(android.view.Gravity.CENTER);
        
        TextView durationSecondsLabel = new TextView(requireContext());
        durationSecondsLabel.setText("S");
        durationSecondsLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        durationSecondsLabel.setGravity(android.view.Gravity.CENTER);
        
        durationMinutesLayout.addView(scheduleDurationMinutesPicker);
        durationMinutesLayout.addView(durationMinutesLabel);
        
        durationSecondsLayout.addView(scheduleDurationSecondsPicker);
        durationSecondsLayout.addView(durationSecondsLabel);
        
        durationLayout.addView(durationMinutesLayout);
        durationLayout.addView(durationSecondsLayout);
        
        layout.addView(durationLayout);
        
        TextView durationValue = new TextView(requireContext());
        durationValue.setText(String.format("%d:%02d", scheduleDuration, scheduleDurationSeconds));
        durationValue.setPadding(0, 10, 0, 20);
        durationValue.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        layout.addView(durationValue);
        
        // Set up change listeners for the time pickers
        hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            startTimeHour = newVal;
            updateTimeTextWithSeconds(startTimeValue, "Start Time", startTimeHour, startTimeMinute, startTimeSecond);
        });
        
        minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            startTimeMinute = newVal;
            updateTimeTextWithSeconds(startTimeValue, "Start Time", startTimeHour, startTimeMinute, startTimeSecond);
        });
        
        secondPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            startTimeSecond = newVal;
            updateTimeTextWithSeconds(startTimeValue, "Start Time", startTimeHour, startTimeMinute, startTimeSecond);
        });
        
        // Duration change listener for minutes
        scheduleDurationMinutesPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            scheduleDuration = newVal;
            
            // Validate total duration doesn't exceed 1 hour
            if (scheduleDuration == 60 && scheduleDurationSeconds > 0) {
                scheduleDuration = 59;
                scheduleDurationSeconds = 59;
                
                // Update the picker to reflect the adjusted value
                scheduleDurationMinutesPicker.setValue(scheduleDuration);
                scheduleDurationSecondsPicker.setValue(scheduleDurationSeconds);
                
                Toast.makeText(getContext(), "Maximum duration is 1 hour", Toast.LENGTH_SHORT).show();
            }
            
            // Update display
            if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
                durationValue.setText("1 hour");
            } else {
                durationValue.setText(String.format("%d:%02d", scheduleDuration, scheduleDurationSeconds));
            }
        });
        
        // Duration change listener for seconds
        scheduleDurationSecondsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            scheduleDurationSeconds = newVal;
            
            // Validate total duration doesn't exceed 1 hour
            if (scheduleDuration == 60 && scheduleDurationSeconds > 0) {
                scheduleDuration = 59;
                scheduleDurationSeconds = 59;
                
                // Update the picker to reflect the adjusted value
                scheduleDurationMinutesPicker.setValue(scheduleDuration);
                scheduleDurationSecondsPicker.setValue(scheduleDurationSeconds);
                
                Toast.makeText(getContext(), "Maximum duration is 1 hour", Toast.LENGTH_SHORT).show();
            }
            
            // Update display
            if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
                durationValue.setText("1 hour");
            } else {
                durationValue.setText(String.format("%d:%02d", scheduleDuration, scheduleDurationSeconds));
            }
        });

        // Add the layout to the scroll view to ensure all content is accessible
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            // Get selected days
            for (int i = 0; i < dayCheckboxes.size(); i++) {
                scheduledDays[i] = dayCheckboxes.get(i).isChecked();
            }
            
            // Save schedule to preferences
            saveScheduleToPreferences();
            
            // Update schedule text
            updateScheduleText();
            
            // Send schedule to ESP32
            sendScheduleToESP32();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void updateTimeTextWithSeconds(TextView textView, String prefix, int hour, int minute, int second) {
        String amPm = hour < 12 ? "AM" : "PM";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        textView.setText(String.format("%s: %d:%02d:%02d %s", prefix, hour12, minute, second, amPm));
    }
    
    private void sendScheduleToESP32() {
        // Create a string representation of the schedule (7 days as 0/1)
        StringBuilder daysString = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            daysString.append(scheduledDays[i] ? "1" : "0");
        }
        
        // Format for sprinkler_schedule command
        // Updated format with seconds: sprinkler_schedule_<7 day bits>_<startHour>_<startMinute>_<startSecond>_<durationMinutes>_<durationSeconds>
        String command = "sprinkler_schedule_" + daysString.toString() + "_" 
                        + startTimeHour + "_" + startTimeMinute + "_" + startTimeSecond + "_" 
                        + scheduleDuration + "_" + scheduleDurationSeconds;
        
        // Send the schedule to ESP32
        apiClient.sendCustomCommand(command, new NexadomusApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                Toast.makeText(getContext(), "Schedule saved", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                showError("Failed to save schedule: " + error);
            }
        });
    }
    
    private void updateScheduleText() {
        if (scheduleText == null) return;
        
        // Ensure text wrapping is enabled
        scheduleText.setSingleLine(false);
        scheduleText.setMaxLines(3); // Allow up to 3 lines for long schedules
        
        boolean anyDayScheduled = false;
        for (boolean day : scheduledDays) {
            if (day) {
                anyDayScheduled = true;
                break;
            }
        }
        
        if (!anyDayScheduled) {
            scheduleText.setText("Schedule: Not Set");
            return;
        }
        
        String[] dayAbbreviations = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        StringBuilder scheduleStr = new StringBuilder("Schedule: ");
        
        boolean first = true;
        for (int i = 0; i < scheduledDays.length; i++) {
            if (scheduledDays[i]) {
                if (!first) {
                    scheduleStr.append(", ");
                }
                scheduleStr.append(dayAbbreviations[i]);
                first = false;
            }
        }
        
        String startAmPm = startTimeHour < 12 ? "AM" : "PM";
        int startHour12 = startTimeHour % 12;
        if (startHour12 == 0) startHour12 = 12;
        
        scheduleStr.append("\n")
                   .append(String.format("%d:%02d:%02d %s", startHour12, startTimeMinute, startTimeSecond, startAmPm))
                   .append(" for ");
        
        // Format duration with "1 hour" when it's exactly 60 minutes and 0 seconds
        if (scheduleDuration == 60 && scheduleDurationSeconds == 0) {
            scheduleStr.append("1 hour");
        } else {
            scheduleStr.append(String.format("%d:%02d", scheduleDuration, scheduleDurationSeconds));
        }
        
        scheduleText.setText(scheduleStr.toString());
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
        
        // Load manual duration settings (separate from schedule duration)
        selectedDurationMinutes = sharedPreferences.getInt(PREF_MANUAL_DURATION_MINUTES, 5);
        selectedDurationSeconds = sharedPreferences.getInt(PREF_MANUAL_DURATION_SECONDS, 0);
        
        // Ensure manual duration doesn't exceed 15 minutes
        if (selectedDurationMinutes == 15 && selectedDurationSeconds > 0) {
            selectedDurationMinutes = 14;
            selectedDurationSeconds = 59;
            saveManualDurationToPreferences();
        }
        
        // Check if there was an active timer when the app was closed
        boolean wasTimerActive = sharedPreferences.getBoolean(PREF_TIMER_ACTIVE, false);
        if (wasTimerActive) {
            long endTimeMillis = sharedPreferences.getLong(PREF_TIMER_END_TIME, 0);
            long currentTimeMillis = System.currentTimeMillis();
            
            // If the timer hasn't expired yet, restart it with remaining time
            if (endTimeMillis > currentTimeMillis) {
                int remainingSecs = (int)((endTimeMillis - currentTimeMillis) / 1000);
                // Only restart if there's meaningful time left (more than 1 second)
                if (remainingSecs > 1) {
                    remainingTimerSeconds = remainingSecs;
                    isSprinklerOn = true;
                }
            } else {
                // Timer has expired, clear the timer state
                sharedPreferences.edit()
                    .putBoolean(PREF_TIMER_ACTIVE, false)
                    .apply();
            }
        }
    }
    
    private void saveScheduleToPreferences() {
        StringBuilder schedule = new StringBuilder();
        for (boolean day : scheduledDays) {
            schedule.append(day ? "1" : "0");
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_SCHEDULE_DAYS, schedule.toString());
        editor.putInt(PREF_SCHEDULE_DURATION, scheduleDuration); // Save scheduled duration minutes
        editor.putInt(PREF_SCHEDULE_DURATION_SECONDS, scheduleDurationSeconds); // Save scheduled duration seconds
        editor.putInt(PREF_START_TIME_HOUR, startTimeHour);
        editor.putInt(PREF_START_TIME_MINUTE, startTimeMinute);
        editor.putInt(PREF_START_TIME_SECOND, startTimeSecond);
        editor.apply();
    }
    
    private void showRemoteMode(boolean isRemote) {
        // Save the mode to preferences for persistence
        sharedPreferences.edit().putBoolean(PREF_REMOTE_MODE, isRemote).apply();
        
        if (connectionModeText != null) {
            if (isRemote) {
                connectionModeText.setText("Mode: REMOTE (ThingSpeak)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else {
                connectionModeText.setText("Mode: LOCAL (Direct Connection)");
                connectionModeText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        }
    }
    
    private void setButtonsEnabled(boolean enabled) {
        if (btnOn != null) {
            btnOn.setEnabled(enabled);
        }
        if (btnOff != null) {
            btnOff.setEnabled(enabled);
        }
        if (btnConfirmDuration != null) {
            btnConfirmDuration.setEnabled(enabled);
        }
        if (btnSchedule != null) {
            btnSchedule.setEnabled(enabled);
        }
    }
    
    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            
            // Make sure we're saving the latest timer state
            if (remainingTimerSeconds > 0 && isSprinklerOn) {
                long endTimeMillis = System.currentTimeMillis() + (remainingTimerSeconds * 1000);
                sharedPreferences.edit()
                    .putBoolean(PREF_TIMER_ACTIVE, true)
                    .putLong(PREF_TIMER_END_TIME, endTimeMillis)
                    .apply();
            }
        }
    }

    // Helper method to format duration nicely
    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        if (seconds == 0) {
            return minutes + " " + (minutes == 1 ? "minute" : "minutes");
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
} 