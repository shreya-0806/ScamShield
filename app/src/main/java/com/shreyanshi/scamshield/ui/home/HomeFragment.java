package com.shreyanshi.scamshield.ui.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.shreyanshi.scamshield.services.SpeechToTextProcessor;
import com.shreyanshi.scamshield.utils.ScamDetector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;
import android.Manifest;
import android.content.Context;

public class HomeFragment extends Fragment {

    private TextView tvNumber;
    private final StringBuilder numberBuilder = new StringBuilder();
    private ScamDatabaseHelper dbHelper;
    private View permissionWarningView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        dbHelper = new ScamDatabaseHelper(getContext());
        tvNumber = view.findViewById(R.id.tvNumber);
        ImageButton btnBackspace = view.findViewById(R.id.btnBackspace);
        FloatingActionButton btnCall = view.findViewById(R.id.btnCall);
        permissionWarningView = view.findViewById(R.id.homePermissionWarning);

        // Set up digit buttons
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
                saveCallAndDial(number);
            } else {
                Toast.makeText(getContext(), "Please enter a number", Toast.LENGTH_SHORT).show();
            }
        });

        // Show or hide permission warning
        updatePermissionWarning();

        return view;
    }

    private void setDigitClickListener(View parent, int id, String digit) {
        parent.findViewById(id).setOnClickListener(v -> {
            numberBuilder.append(digit);
            updateDisplay();
        });
    }

    private void updateDisplay() {
        tvNumber.setText(numberBuilder.toString());
    }

    private void saveCallAndDial(String number) {
        String currentDateTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        dbHelper.insertCallLog(number, currentDateTime, false);

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + Uri.encode(number)));
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SpeechToTextProcessor.SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String spokenText = SpeechToTextProcessor.getSpeechResult(data);
            analyzeText(spokenText);
        }
    }

    private void analyzeText(String text) {
        ScamDetector.ScamResult result = ScamDetector.detect(text);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        if (result.isScam) {
            builder.setTitle("🚨 Scam Alert!")
                    .setMessage("Suspicious keywords detected: " + result.matchedKeywords.toString() + 
                                "\n\nFull Text: \"" + text + "\"")
                    .setPositiveButton("OK", null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            
            // Log this as a scam record in DB
            String date = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date());
            dbHelper.insertScam(date, "Keywords detected: " + result.matchedKeywords);
        } else {
            Toast.makeText(getContext(), "No scam keywords detected.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionWarning();
    }

    private void updatePermissionWarning() {
        boolean micGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        permissionWarningView.setVisibility(micGranted ? View.GONE : View.VISIBLE);
    }
}
