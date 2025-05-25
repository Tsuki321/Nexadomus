package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String NEXADOMUS_SSID = "Nexadomus Home";
    private NavController navController;
    private BottomNavigationView bottomNav;
    private NavOptions navOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Initialize API clients with application context
        NexadomusApiClient.getInstance().setAppContext(getApplicationContext());
        ThingSpeakClient.getInstance().setAppContext(getApplicationContext());
        
        // Get the NavHostFragment and NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            
            // Set up the AppBarConfiguration to connect NavController with Toolbar
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.homeFragment, R.id.settingsFragment)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            
            // Connect the BottomNavigationView
            bottomNav = findViewById(R.id.bottom_navigation);
            
            // Create navigation options with no animations
            navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setEnterAnim(0)
                .setExitAnim(0)
                .setPopEnterAnim(0)
                .setPopExitAnim(0)
                .build();
            
            // Set up bottom navigation
            setupBottomNavigation();
            
            // Listen for destination changes to update selection
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                // Just update the selected item without triggering the listener
                if (destination.getId() == R.id.homeFragment) {
                    bottomNav.setSelectedItemId(R.id.homeFragment);
                } else if (destination.getId() == R.id.settingsFragment) {
                    bottomNav.setSelectedItemId(R.id.settingsFragment);
                }
            });
        }
        
        // Check WiFi connection status
        checkWiFiConnection();
    }
    
    /**
     * Set up the bottom navigation with menu items and navigation
     */
    private void setupBottomNavigation() {
        // Clear existing menu items first
        bottomNav.getMenu().clear();
        
        // Inflate the menu
        getMenuInflater().inflate(R.menu.bottom_nav_menu, bottomNav.getMenu());
        
        // Set up navigation listener
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int currentDestId = navController.getCurrentDestination().getId();
            
            if (id == R.id.homeFragment && currentDestId != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment, null, navOptions);
            } else if (id == R.id.settingsFragment && currentDestId != R.id.settingsFragment) {
                navController.navigate(R.id.settingsFragment, null, navOptions);
            }
            
            return true;
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Empty menu for now, we removed the help icon
        return true;
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check WiFi connection every time the app resumes
        checkWiFiConnection();
    }
    
    private void checkWiFiConnection() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WiFi manager is null");
            showToast("Cannot access WiFi services");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "WiFi is disabled");
            showToast("WiFi is disabled. Please enable WiFi for local control.");
            return;
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            Log.e(TAG, "WiFi info is null");
            showToast("Not connected to WiFi");
            return;
        }

        String ssid = wifiInfo.getSSID();
        if (ssid != null) {
            ssid = ssid.replace("\"", ""); // Remove quotes
            Log.d(TAG, "Connected to WiFi: " + ssid);
            
            if (NEXADOMUS_SSID.equals(ssid)) {
                showToast("Connected to Nexadomus AP - Local control available");
            } else {
                showToast("Connected to " + ssid + " - Remote control via ThingSpeak");
            }
        } else {
            Log.e(TAG, "SSID is null");
            showToast("Cannot determine WiFi connection");
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}