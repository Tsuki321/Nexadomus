package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String NEXADOMUS_SSID = "Nexadomus Home";
    private NavController navController;
    private BottomNavigationView bottomNav;

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
            
            // Connect the BottomNavigationView with navigation
            bottomNav = findViewById(R.id.bottom_navigation);
            
            // Update bottom navigation visibility based on current destination
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                // Show appropriate bottom navigation items based on destination
                if (destination.getId() == R.id.homeFragment) {
                    bottomNav.getMenu().clear();
                    getMenuInflater().inflate(R.menu.bottom_nav_menu, bottomNav.getMenu());
                } else {
                    bottomNav.getMenu().clear();
                    getMenuInflater().inflate(R.menu.bottom_nav_fragments, bottomNav.getMenu());
                }
                
                // Set up navigation with the controller
                NavigationUI.setupWithNavController(bottomNav, navController);
            });
        }
        
        // Check WiFi connection status
        checkWiFiConnection();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_help) {
            // Show help dialog using existing HelpDialogUtil
            HelpDialogUtil.showHelpDialog(this, "Local Mode Connection", 
                    "If for some reason you are unable to command the Devices through Remote mode, try switching to Local mode by connecting to the Hotspot of the ESP32.\n\n" +
                    "SSID: Nexadomus Home\n" +
                    "Password: smartHome1234");
            return true;
        }
        
        return super.onOptionsItemSelected(item);
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