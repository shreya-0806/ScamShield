package com.shreyanshi.scamshield.ui.history;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;
import com.shreyanshi.scamshield.database.ScamDatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<CallLogModel> callLogs = new ArrayList<>();
    private ScamDatabaseHelper dbHelper;

    public HistoryFragment() {}

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_history, container, false);

        dbHelper = new ScamDatabaseHelper(getContext());
        recyclerView = view.findViewById(R.id.recyclerHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadCallHistory();

        return view;
    }

    private void loadCallHistory() {
        callLogs.clear();
        Cursor cursor = dbHelper.getAllCalls();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(ScamDatabaseHelper.COL_CALL_NUMBER));
                String date = cursor.getString(cursor.getColumnIndexOrThrow(ScamDatabaseHelper.COL_CALL_DATE));
                int isScamInt = cursor.getInt(cursor.getColumnIndexOrThrow(ScamDatabaseHelper.COL_CALL_IS_SCAM));
                
                callLogs.add(new CallLogModel(number, date, isScamInt == 1));
            }
            cursor.close();
        }

        adapter = new HistoryAdapter(callLogs);
        recyclerView.setAdapter(adapter);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh history when returning to the fragment
        loadCallHistory();
    }
}
