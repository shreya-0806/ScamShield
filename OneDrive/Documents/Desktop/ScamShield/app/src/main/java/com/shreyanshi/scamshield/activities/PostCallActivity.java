package com.shreyanshi.scamshield.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.shreyanshi.scamshield.R;

public class PostCallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_call);

        Button btnYes = findViewById(R.id.btnYes);
        Button btnNo = findViewById(R.id.btnNo);

        btnYes.setOnClickListener(v -> {
            Toast.makeText(this, "Analysis saved to History", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnNo.setOnClickListener(v -> {
            finish();
        });
    }
}