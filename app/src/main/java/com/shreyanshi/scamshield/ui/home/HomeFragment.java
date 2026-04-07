package com.shreyanshi.scamshield.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.ScamDatabaseHelper;
import com.shreyanshi.scamshield.services.ScamMonitorService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String PREF_NAME = "ScamShieldPrefs";
    private TextView tvNumber;
    private TextView tvStatusVosk, tvStatusProtection;
    private final StringBuilder numberBuilder = new StringBuilder();
    private ScamDatabaseHelper dbHelper;
    private View permissionWarningView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        dbHelper = new ScamDatabaseHelper(getContext());
        tvNumber = view.findViewById(R.id.tvNumber);
        tvStatusVosk = view.findViewById(R.id.tvStatusVosk);
        tvStatusProtection = view.findViewById(R.id.tvStatusProtection);
        permissionWarningView = view.findViewById(R.id.homePermissionWarning);
        
        Button btnTestScam = view.findViewById(R.id.btnTestScam);
        ImageButton btnBackspace = view.findViewById(R.id.btnBackspace);
        FloatingActionButton btnCall = view.findViewById(R.id.btnCall);

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
                makeCall(number);
            } else {
                Toast.makeText(getContext(), "Please enter a number", Toast.LENGTH_SHORT).show();
            }
        });

        btnTestScam.setOnClickListener(v -> {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("scam_alerts_enabled", true);
            
            if (!enabled) {
                Toast.makeText(getContext(), "Enable scam detection in Settings first", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent i = new Intent(requireContext(), ScamMonitorService.class);
            i.setAction("SHOW_TEST_ALERT");
            i.putExtra("keywords", "TEST_SCAM_ALERT");
            i.putExtra("number", "Test Number");
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

    private void makeCall(String number) {
        String currentDateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        dbHelper.insertCallLog(number, currentDateTime, false);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + Uri.encode(number)));
            startActivity(callIntent);
        } else {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + Uri.encode(number)));
            startActivity(dialIntent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusPanel();
    }

    private void updateStatusPanel() {
        Context context = requireContext();
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean protectionEnabled = prefs.getBoolean("scam_alerts_enabled", true);
        tvStatusProtection.setText(protectionEnabled ? "ACTIVE" : "INACTIVE");
        tvStatusProtection.setTextColor(protectionEnabled ? 0xFF4CAF50 : 0xFFF44336);

        File modelDir = new File(context.getFilesDir(), "vosk-model-small-en-in");
        File altModelDir = new File(context.getFilesDir(), "vosk-model");
        boolean modelExists = modelDir.exists() || altModelDir.exists();
        tvStatusVosk.setText(modelExists ? "READY" : "LOADING...");
        tvStatusVosk.setTextColor(modelExists ? 0xFF4CAF50 : 0xFFFF9800);

        boolean micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        permissionWarningView.setVisibility(micGranted ? View.GONE : View.VISIBLE);
    }
}
