package com.shreyanshi.scamshield.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.ScamDatabaseHelper;
import com.shreyanshi.scamshield.services.ScamOverlayService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvNumber;
    private TextView tvStatusOverlay, tvStatusVosk;
    private final StringBuilder numberBuilder = new StringBuilder();
    private ScamDatabaseHelper dbHelper;
    private View permissionWarningView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        dbHelper = new ScamDatabaseHelper(getContext());
        tvNumber = view.findViewById(R.id.tvNumber);
        tvStatusOverlay = view.findViewById(R.id.tvStatusOverlay);
        tvStatusVosk = view.findViewById(R.id.tvStatusVosk);
        permissionWarningView = view.findViewById(R.id.homePermissionWarning);
        
        Button btnTestScam = view.findViewById(R.id.btnTestScam);
        ImageButton btnBackspace = view.findViewById(R.id.btnBackspace);
        FloatingActionButton btnCall = view.findViewById(R.id.btnCall);

        // Dialer buttons
        setDigitClickListener(view, R.id.btn0, "0");
        setDigitClickListener(view, R.id.btn1, "1");
        setDigitClickListener(view, R.id.btn2, "2");
        setDigitClickListener(view, R.id.btn3, "3");
        setDigitClickListener(view, R.id.btn4, "4");
        setDigitClickListener(view, R.id.btn5, "5");
        setDigitClickListener(view, R.id.btn6, "6");
        setDigitClickListener(view, R.id.btn7, "7");
        setDigitClickListener(view, R.id.btn8, "8");
        setDigitClickListener(view, R.id.btn9, "9");
        setDigitClickListener(view, R.id.btnStar, "*");
        setDigitClickListener(view, R.id.btnHash, "#");

        btnBackspace.setOnClickListener(v -> {
            if (numberBuilder.length() > 0) {
                numberBuilder.deleteCharAt(numberBuilder.length() - 1);
                updateDisplay();
            }
        });

        btnCall.setOnClickListener(v -> {
            String number = numberBuilder.toString();
            if (!TextUtils.isEmpty(number)) {
                dialNumber(number);
            } else {
                Toast.makeText(getContext(), "Please enter a number", Toast.LENGTH_SHORT).show();
            }
        });

        btnTestScam.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ScamOverlayService.class);
            i.putExtra("action", "SHOW_ALERT");
            i.putExtra("keywords", "TEST_SCAM_ALERT");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                requireContext().startForegroundService(i);
            } else {
                requireContext().startService(i);
            }
            Toast.makeText(getContext(), "Showing test alert...", Toast.LENGTH_SHORT).show();
        });

        permissionWarningView.setOnClickListener(v -> {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        });

        return view;
    }

    private void setDigitClickListener(View parent, int id, String digit) {
        View v = parent.findViewById(id);
        if (v != null) v.setOnClickListener(v1 -> {
            numberBuilder.append(digit);
            updateDisplay();
        });
    }

    private void updateDisplay() {
        tvNumber.setText(numberBuilder.toString());
    }

    private void dialNumber(String number) {
        String currentDateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        dbHelper.insertCallLog(number, currentDateTime, false);

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + Uri.encode(number)));
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusPanel();
    }

    private void updateStatusPanel() {
        Context context = requireContext();
        
        // Check Overlay Permission
        boolean overlayGranted = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            overlayGranted = Settings.canDrawOverlays(context);
        }
        tvStatusOverlay.setText(overlayGranted ? "GRANTED" : "MISSING");
        tvStatusOverlay.setTextColor(overlayGranted ? 0xFF4CAF50 : 0xFFF44336);

        // Check Vosk Model
        File modelFolder = new File(context.getFilesDir(), "vosk-model");
        boolean modelExists = modelFolder.exists() && modelFolder.isDirectory();
        tvStatusVosk.setText(modelExists ? "INSTALLED" : "MISSING");
        tvStatusVosk.setTextColor(modelExists ? 0xFF4CAF50 : 0xFFF44336);

        // Check Mic Permission
        boolean micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        permissionWarningView.setVisibility(micGranted ? View.GONE : View.VISIBLE);
    }
}
