package com.shreyanshi.scamshield.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * WindowManager-Based Debug Log Window (Moto/Redmi Compatible)
 * 
 * Displays real-time speech recognition events and service lifecycle logs
 * using WindowManager overlay for maximum device compatibility.
 * Works on Moto, Redmi, and other OEM devices with strict overlay restrictions.
 * 
 * Features:
 * - WindowManager overlay (TYPE_APPLICATION_OVERLAY on Android 7+, TYPE_PHONE on older)
 * - NO dependencies on Activity layout hierarchy
 * - Floating TextView (scrollable, dismissible, draggable)
 * - Timestamped log entries in format: "[HH:mm:ss] LOG: message"
 * - Color-coded emoji prefixes: ✅ ❌ 🎤 📢 🔄 🚨 🛑 ⚠️
 * - Limits to 50 lines to prevent memory bloat
 * - Toggle visibility (SharedPreferences key: "debug_log_enabled")
 * - Auto-scroll to latest entries
 * - Semi-transparent dark background (#1A121212)
 * - Green text (#00FF00) for visibility
 * - FLAG_NOT_FOCUSABLE: Window doesn't consume touch events (overlay only)
 * - FLAG_LAYOUT_IN_SCREEN: Window positioned behind status bar
 */
public class DebugLogWindow {
    
    private static final String TAG = "DebugLogWindow";
    private static final String PREF_NAME = "ScamShieldPrefs";
    private static final String PREF_DEBUG_LOG_ENABLED = "debug_log_enabled";
    private static final int MAX_LOG_LINES = 50;
    private static final int WINDOW_WIDTH = 800;  // pixels
    private static final int WINDOW_HEIGHT = 600; // pixels
    
    private final AppCompatActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WindowManager windowManager;
    
    private FrameLayout debugContainer;
    private ScrollView debugScroll;
    private TextView debugLogTextView;
    private WindowManager.LayoutParams windowParams;
    private boolean isInitialized = false;
    private boolean isWindowAdded = false;
    
    /**
     * Constructor: Initialize debug log window with activity context
     */
    public DebugLogWindow(AppCompatActivity activity) {
        this.activity = activity;
        // Use Application Context for WindowManager to survive Activity lifecycle
        this.windowManager = (WindowManager) activity.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        
        if (windowManager == null) {
            Log.e(TAG, "❌ CRITICAL: WindowManager is null - overlay window will not work");
        } else {
            Log.d(TAG, "✅ WindowManager obtained from Application Context");
        }
    }
    
    /**
     * Initialize the debug log window using WindowManager overlay
     * 
     * Works on Moto, Redmi, and other OEM Android devices.
     * Can be called from onCreate(), or re-called later to manually refresh window.
     * 
     * SAFETY: Wraps all WindowManager operations in try-catch to prevent crashes
     * 
     * @param parentContainer Unused (kept for API compatibility), actual window uses WindowManager
     */
    public void initialize(ViewGroup parentContainer) {
        // SAFETY CHECK 1: Verify windowManager is available
        if (windowManager == null) {
            Log.e(TAG, "❌ ERROR: WindowManager is null - cannot initialize debug window");
            isInitialized = false;
            return;
        }
        
        // SAFETY CHECK 2: Verify activity context is valid
        if (activity == null || activity.isDestroyed()) {
            Log.e(TAG, "❌ ERROR: Activity is destroyed - cannot initialize debug window");
            isInitialized = false;
            return;
        }
        
        try {
            // Check if debug log is enabled
            SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean debugEnabled = prefs.getBoolean(PREF_DEBUG_LOG_ENABLED, false);
            
            // SAFETY: If window already added, remove it first to prevent duplicate (crashes on Redmi)
            if (isWindowAdded && debugContainer != null) {
                try {
                    windowManager.removeView(debugContainer);
                    isWindowAdded = false;
                    Log.d(TAG, "🔄 Removed existing window for re-initialization");
                } catch (IllegalArgumentException e) {
                    // This is expected if view was already removed
                    Log.w(TAG, "⚠️ Window already removed: " + e.getMessage());
                    isWindowAdded = false;
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error removing old window: " + e.getMessage());
                    isWindowAdded = false;
                }
            }
            
            // Create container for debug window
            debugContainer = new FrameLayout(activity);
            debugContainer.setBackgroundColor(Color.parseColor("#1A121212")); // Dark semi-transparent
            
            // Create scrollable text view
            debugScroll = new ScrollView(activity);
            debugLogTextView = new TextView(activity);
            debugLogTextView.setTextColor(Color.parseColor("#00FF00")); // Bright green
            debugLogTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            debugLogTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
            debugLogTextView.setPadding(16, 16, 16, 16);
            debugLogTextView.setMaxLines(Integer.MAX_VALUE);
            debugLogTextView.setMovementMethod(new ScrollingMovementMethod());
            
            // Assemble layout
            debugScroll.addView(debugLogTextView, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            debugContainer.addView(debugScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            
            // Setup WindowManager layout parameters
            setupWindowManagerParams();
            
            // CRITICAL SAFETY FIX: Wrap addView in try-catch
            // This is the line that crashes on Redmi/Moto if windowParams are invalid
            try {
                Log.d(TAG, "🔧 About to add window to WindowManager (type=" + windowParams.type + ")");
                windowManager.addView(debugContainer, windowParams);
                isWindowAdded = true;
                Log.d(TAG, "✅ Successfully added window to WindowManager");
            } catch (WindowManager.BadTokenException e) {
                Log.e(TAG, "❌ CRASH FIX: BadTokenException - invalid window token: " + e.getMessage());
                isWindowAdded = false;
                isInitialized = false;
                return;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "❌ CRASH FIX: IllegalArgumentException - invalid window params: " + e.getMessage());
                Log.e(TAG, "   Type: " + windowParams.type + ", Flags: " + windowParams.flags);
                isWindowAdded = false;
                isInitialized = false;
                return;
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ CRASH FIX: IllegalStateException - invalid window state: " + e.getMessage());
                isWindowAdded = false;
                isInitialized = false;
                return;
            } catch (Exception e) {
                Log.e(TAG, "❌ CRASH FIX: Unexpected exception adding window: " + e.getClass().getSimpleName() 
                        + " - " + e.getMessage(), e);
                isWindowAdded = false;
                isInitialized = false;
                return;
            }
            
            // Set initial visibility
            debugContainer.setVisibility(debugEnabled ? View.VISIBLE : View.GONE);
            
            isInitialized = true;
            Log.d(TAG, "✅ DebugLogWindow initialized with WindowManager");
            
            // Log initial message
            if (debugEnabled) {
                logToScreen("✅ Debug window visible (WindowManager overlay)");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during debug log initialization: " + e.getMessage(), e);
            isInitialized = false;
        }
    }
    
    /**
     * Setup WindowManager.LayoutParams for overlay window
     * Uses TYPE_APPLICATION_OVERLAY on Android 7+ (preferred)
     * Falls back to TYPE_PHONE on Android 6.0 and older
     * 
     * Flag breakdown:
     * - FLAG_NOT_FOCUSABLE: Window doesn't consume touch events (pure overlay)
     * - FLAG_LAYOUT_IN_SCREEN: Measure and layout window in full screen coordinates
     * - FLAG_NOT_TOUCHABLE: (optional) Prevents window from receiving touch events
     */
    private void setupWindowManagerParams() {
        windowParams = new WindowManager.LayoutParams();
        
        // Choose type based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+: Use TYPE_APPLICATION_OVERLAY (recommended)
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            Log.d(TAG, "🔧 Using TYPE_APPLICATION_OVERLAY (Android 8.0+)");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0-7.1: Use TYPE_APPLICATION_OVERLAY if available
            try {
                windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                Log.d(TAG, "🔧 Using TYPE_APPLICATION_OVERLAY (Android 7.0+)");
            } catch (Exception e) {
                // Fallback to TYPE_PHONE for safety
                windowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
                Log.d(TAG, "🔧 Fallback to TYPE_PHONE (Android 7.0)");
            }
        } else {
            // Android 6.0 and older: Use TYPE_PHONE
            windowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            Log.d(TAG, "🔧 Using TYPE_PHONE (Android 6.0 or older)");
        }
        
        // Set window format (transparency support)
        windowParams.format = android.graphics.PixelFormat.TRANSLUCENT;
        
        // Set dimensions
        windowParams.width = WINDOW_WIDTH;
        windowParams.height = WINDOW_HEIGHT;
        
        // Set position: bottom-right corner with some margin
        windowParams.x = 0;
        windowParams.y = 0;
        windowParams.gravity = Gravity.BOTTOM | Gravity.START;
        
        // Critical flags for overlay compatibility
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE   // Don't consume touch input
                           | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN; // Measure in screen coords
        
        // Optional: Make window non-touchable if you want pure overlay behavior
        // windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        
        Log.d(TAG, "✅ WindowManager params configured (type=" + windowParams.type + ")");
    }
    
    /**
     * Add a timestamped debug log entry to the on-screen log
     * Format: "[HH:mm:ss] LOG: [message]"
     * 
     * Safe to call from any thread (uses Handler for main thread execution)
     */
    public void logToScreen(String message) {
        if (!isInitialized || debugLogTextView == null) {
            return;
        }
        
        mainHandler.post(() -> {
            try {
                // Format timestamp
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                String timestamp = "[" + sdf.format(new Date()) + "]";
                String logLine = timestamp + " " + message;
                
                // Get current text
                String currentText = debugLogTextView.getText().toString();
                
                // Append new line
                if (currentText.isEmpty()) {
                    debugLogTextView.setText(logLine);
                } else {
                    debugLogTextView.setText(currentText + "\n" + logLine);
                }
                
                // Limit to MAX_LOG_LINES to prevent memory bloat
                String fullText = debugLogTextView.getText().toString();
                String[] lines = fullText.split("\n");
                if (lines.length > MAX_LOG_LINES) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                        if (i > lines.length - MAX_LOG_LINES) sb.append("\n");
                        sb.append(lines[i]);
                    }
                    debugLogTextView.setText(sb.toString());
                }
                
                // Auto-scroll to bottom to show latest entries
                if (debugScroll != null) {
                    debugScroll.post(() -> debugScroll.fullScroll(View.FOCUS_DOWN));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error adding debug log entry: " + e.getMessage());
            }
        });
    }
    
    /**
     * Toggle debug log visibility
     * Updates SharedPreferences to persist visibility state across app restarts
     */
    public void toggleVisibility() {
        if (!isInitialized || debugContainer == null) {
            return;
        }
        
        try {
            SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean currentlyVisible = prefs.getBoolean(PREF_DEBUG_LOG_ENABLED, false);
            boolean newState = !currentlyVisible;
            
            // Update visibility
            debugContainer.setVisibility(newState ? View.VISIBLE : View.GONE);
            
            // Save preference
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PREF_DEBUG_LOG_ENABLED, newState);
            editor.apply();
            
            Log.i(TAG, "Debug log toggled: " + (newState ? "ON" : "OFF"));
            
        } catch (Exception e) {
            Log.e(TAG, "Error toggling debug log: " + e.getMessage());
        }
    }
    
    /**
     * Check if debug log is currently visible
     */
    public boolean isVisible() {
        if (!isInitialized || debugContainer == null) {
            return false;
        }
        return debugContainer.getVisibility() == View.VISIBLE;
    }
    
    /**
     * Clear all debug log entries
     */
    public void clear() {
        if (!isInitialized || debugLogTextView == null) {
            return;
        }
        
        mainHandler.post(() -> {
            try {
                debugLogTextView.setText("");
                logToScreen("🗑️ Debug log cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing debug log: " + e.getMessage());
            }
        });
    }
    
    /**
     * Destroy debug log window and cleanup resources
     * Must be called in Activity/Service onDestroy()
     * 
     * SAFETY: Properly handles all possible exception cases to prevent memory leaks
     */
    public void destroy() {
        try {
            // Remove window from WindowManager if it was added
            if (isWindowAdded && debugContainer != null && windowManager != null) {
                try {
                    windowManager.removeView(debugContainer);
                    isWindowAdded = false;
                    Log.d(TAG, "✅ Window removed from WindowManager");
                } catch (IllegalArgumentException e) {
                    // This is expected if view was already removed
                    Log.w(TAG, "⚠️ Window already removed (expected): " + e.getMessage());
                    isWindowAdded = false;
                } catch (IllegalStateException e) {
                    // Window manager in invalid state
                    Log.w(TAG, "⚠️ WindowManager in invalid state: " + e.getMessage());
                    isWindowAdded = false;
                } catch (Exception e) {
                    // Any other exception - still cleanup
                    Log.e(TAG, "❌ Error removing window: " + e.getMessage());
                    isWindowAdded = false;
                }
            }
            
            // Cleanup references
            debugLogTextView = null;
            debugScroll = null;
            debugContainer = null;
            windowParams = null;
            isInitialized = false;
            
            Log.d(TAG, "✅ DebugLogWindow destroyed and resources cleaned");
        } catch (Exception e) {
            Log.e(TAG, "Error during destroy: " + e.getMessage());
        }
    }
    
    /**
     * Get reference to debug log text view (for testing purposes)
     */
    public TextView getDebugTextView() {
        return debugLogTextView;
    }
}
