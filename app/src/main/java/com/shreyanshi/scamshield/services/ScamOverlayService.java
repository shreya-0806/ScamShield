package com.shreyanshi.scamshield.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;

import com.shreyanshi.scamshield.R;

public class ScamOverlayService extends Service {

    private static final String TAG = "ScamOverlay";

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            LayoutInflater inflater = LayoutInflater.from(this);

            // ✅ SMALL overlay instead of full screen
            overlayView = inflater.inflate(R.layout.overlay_small, null);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT // 🔥 IMPORTANT (no black screen)
            );

            params.gravity = Gravity.TOP | Gravity.END;
            params.x = 20;
            params.y = 100;

            windowManager.addView(overlayView, params);

            Log.i(TAG, "✅ Overlay added (safe mode)");

        } catch (Exception e) {
            Log.e(TAG, "❌ Overlay error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                Log.i(TAG, "Overlay removed");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}