package com.shreyanshi.scamshield;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class ScamApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File dir = getExternalFilesDir("logs");
                if (dir != null && !dir.exists()) dir.mkdirs();
                File f = new File(dir, "last_crash.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write("\n--- CRASH at " + System.currentTimeMillis() + " ---\n");
                PrintWriter pw = new PrintWriter(fw);
                throwable.printStackTrace(pw);
                pw.flush();
                pw.close();
                fw.close();
            } catch (Exception e) {
                Log.e("ScamApplication", "Failed to write crash file", e);
            }

            // delegate to default handler after logging
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });
    }
}
