package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {

    // Splash screen display duration in milliseconds
    private static final long SPLASH_DISPLAY_LENGTH = 2000;
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Required permissions based on Android version
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

    private TextView permissionText;
    private Button permissionButton;
    private boolean permissionsGranted = false;

    // Permission request launcher
    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize UI components
        permissionText = findViewById(R.id.permissionText);
        permissionButton = findViewById(R.id.permissionButton);

        // Set up the permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResult);

        // Check if permissions are already granted
        if (hasAllRequiredPermissions()) {
            permissionsGranted = true;
            showSplashScreen();
        } else {
            // Show permission UI if permissions are not granted
            showPermissionUI();
        }
    }

    private boolean hasAllRequiredPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void showPermissionUI() {
        permissionText.setVisibility(View.VISIBLE);
        permissionButton.setVisibility(View.VISIBLE);

        permissionButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For Android 6.0+ use requestPermissions or launcher
                if (shouldShowRequestPermissionRationale()) {
                    showPermissionRationaleDialog();
                } else {
                    requestRequiredPermissions();
                }
            } else {
                // For pre-Marshmallow, permissions are granted at install time
                permissionsGranted = true;
                showSplashScreen();
            }
        });
    }

    private boolean shouldShowRequestPermissionRationale() {
        boolean shouldShowRationale = false;
        for (String permission : getRequiredPermissions()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                shouldShowRationale = true;
                break;
            }
        }
        return shouldShowRationale;
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(getString(R.string.permission_explanation))
            .setPositiveButton("OK", (dialog, which) -> requestRequiredPermissions())
            .setNegativeButton("Cancel", (dialog, which) -> {
                Toast.makeText(this, "App requires these permissions to function properly", Toast.LENGTH_LONG).show();
            })
            .create()
            .show();
    }

    private void requestRequiredPermissions() {
        // Get only the permissions that are not yet granted
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        // Request the permissions
        if (!permissionsToRequest.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
                );
            } else {
                // Use the launcher as a fallback, though not needed for pre-M
                requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
            }
        } else {
            // All permissions are already granted
            permissionsGranted = true;
            showSplashScreen();
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
                permissionsGranted = true;
                showSplashScreen();
            } else {
                permissionText.setText(R.string.permission_explanation_denied);
            }
        }
    }

    private void handlePermissionResult(Map<String, Boolean> result) {
        boolean allGranted = true;
        for (Boolean granted : result.values()) {
            allGranted = allGranted && granted;
        }

        if (allGranted) {
            // All permissions granted, proceed to main activity
            permissionsGranted = true;
            showSplashScreen();
        } else {
            // Some permissions were denied, update the UI to explain why they're needed
            permissionText.setText(R.string.permission_explanation_denied);
        }
    }

    private void showSplashScreen() {
        permissionText.setVisibility(View.GONE);
        permissionButton.setVisibility(View.GONE);

        // Use Handler to delay loading the MainActivity
        new Handler().postDelayed(() -> {
            // Create an Intent to start the main activity
            Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(mainIntent);
            finish(); // Close the splash activity so it can't be returned to
        }, SPLASH_DISPLAY_LENGTH);
    }
} 