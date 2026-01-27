package com.shreyanshi.scamshield.activities;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.ScamDatabaseHelper;

import java.util.ArrayList;

public class ScamHistoryActivity extends AppCompatActivity {

    ListView listViewScams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scam_history);

        listViewScams = findViewById(R.id.listViewScams);

        ScamDatabaseHelper db = new ScamDatabaseHelper(this);
        Cursor cursor = db.getAllScams();

        ArrayList<String> scamList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            do {
                String date =
                        cursor.getString(cursor.getColumnIndexOrThrow("date"));
                String reason =
                        cursor.getString(cursor.getColumnIndexOrThrow("reason"));

                scamList.add(date + "\nReason: " + reason);
            } while (cursor.moveToNext());
        }

        cursor.close();

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1,
                        scamList);

        listViewScams.setAdapter(adapter);
    }
}
