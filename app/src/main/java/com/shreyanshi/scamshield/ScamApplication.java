package com.shreyanshi.scamshield;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * ScamApplication - Global exception handler for crash logging and debugging
 * 
 * Provides:
 * 1. File logging of all uncaught exceptions to external storage
 * 2. Toast display of crash details for immediate user feedback
 * 3. Detailed Logcat output for debugging
 */
public class ScamApplication extends Application {
    
    private static final String TAG = "ScamApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "✅ ScamApplication initialized");
        
        setupGlobalExceptionHandler();
    }
    
    /**
     * Setup global uncaught exception handler
     * Logs crashes to file AND displays Toast with error details
     * 
     * SAFETY FIX: Shows user exactly which line/method crashed before app closes
     */
    private void setupGlobalExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "🚨 UNCAUGHT EXCEPTION - App will crash!", throwable);
            
            // Extract crash details for user feedback
            String exceptionMessage = throwable.getMessage() != null ? 
                    throwable.getMessage() : throwable.getClass().getSimpleName();
            String exceptionClass = throwable.getClass().getSimpleName();
            
            // Get the line number that crashed
            String crashLocation = "Unknown";
            if (throwable.getStackTrace() != null && throwable.getStackTrace().length > 0) {
                StackTraceElement element = throwable.getStackTrace()[0];
                crashLocation = element.getClassName() + ":" + element.getLineNumber() + 
                        " in " + element.getMethodName();
            }
            
            // Make these final for use in lambda
            final String finalExceptionClass = exceptionClass;
            final String finalCrashLocation = crashLocation;
            final String finalExceptionMessage = exceptionMessage;
            
            Log.e(TAG, "❌ CRASH DETAILS:");
            Log.e(TAG, "   Exception: " + finalExceptionClass);
            Log.e(TAG, "   Message: " + finalExceptionMessage);
            Log.e(TAG, "   Location: " + finalCrashLocation);
            Log.e(TAG, "   Thread: " + thread.getName());
            
            try {
                // Write to file (for crash report)
                File dir = getExternalFilesDir("logs");
                if (dir != null && !dir.exists()) dir.mkdirs();
                File f = new File(dir, "last_crash.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write("\n--- CRASH at " + System.currentTimeMillis() + " ---\n");
                fw.write("Exception: " + finalExceptionClass + "\n");
                fw.write("Message: " + finalExceptionMessage + "\n");
                fw.write("Location: " + finalCrashLocation + "\n");
                fw.write("Full Stack Trace:\n");
                PrintWriter pw = new PrintWriter(fw);
                throwable.printStackTrace(pw);
                pw.flush();
                pw.close();
                fw.close();
                
                Log.i(TAG, "✅ Crash logged to: " + f.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to write crash file: " + e.getMessage());
            }
            
            // SAFETY FIX: Show Toast with crash details before app closes
            // This helps developers see exactly what crashed
            try {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    try {
                        int locationLength = Math.min(50, finalCrashLocation.length());
                        String toastMessage = "⚠️ CRASH: " + finalExceptionClass + "\n" + 
                                "📍 " + finalCrashLocation.substring(0, locationLength);
                        
                        Toast.makeText(getApplicationContext(), 
                                toastMessage, 
                                Toast.LENGTH_LONG).show();
                        
                        Log.i(TAG, "✅ Displayed crash Toast to user");
                    } catch (Exception e) {
                        Log.e(TAG, "Could not display Toast: " + e.getMessage());
                    }
                });
                
                // Give Toast time to display (500ms) before crashing
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, "Error displaying crash Toast: " + e.getMessage());
            }
            
            // Delegate to default handler after logging
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });
        
        Log.d(TAG, "✅ Global exception handler installed");
    }
}

