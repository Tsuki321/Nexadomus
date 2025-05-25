package com.example.myapplication;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * Utility class to handle common help dialog functionality
 */
public class HelpDialogUtil {
    
    /**
     * Shows the standard connection help dialog
     * @param context The current context
     */
    public static void showConnectionHelpDialog(Context context) {
        new AlertDialog.Builder(context)
            .setTitle("How to Connect")
            .setMessage("When WiFi connection is down, connect your phone to the \"Nexadomus Home\" WiFi network with the password \"smartHome1234\"")
            .setPositiveButton("OK", null)
            .create()
            .show();
    }
    
    /**
     * Shows a customizable help dialog
     * @param context The current context
     * @param title The title of the dialog
     * @param message The message to display
     */
    public static void showHelpDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .show();
    }
} 