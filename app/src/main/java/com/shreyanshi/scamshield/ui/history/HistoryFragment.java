package com.shreyanshi.scamshield.ui.history;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shreyanshi.scamshield.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final int PERMISSION_REQUEST_READ_CALL_LOG = 101;
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<CallLogModel> callLogs = new ArrayList<>();
    private TextView tvEmpty;

    public HistoryFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        recyclerView = view.findViewById(R.id.recyclerHistory);
        tvEmpty = view.findViewById(R.id.tvHistoryEmpty);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadCallHistory();

        return view;
    }

    private void loadCallHistory() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) 
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, PERMISSION_REQUEST_READ_CALL_LOG);
            return;
        }

        callLogs.clear();
        
        try {
            Cursor cursor = requireContext().getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.DATE,
                            CallLog.Calls.DURATION,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.CACHED_NAME
                    },
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                int limit = 0;
                while (cursor.moveToNext() && limit < 100) {
                    String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));

                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                    String dateStr = sdf.format(new Date(dateMillis));

                    String displayName = (name != null && !name.isEmpty()) ? name : number;
                    int callType = getCallType(type);

                    callLogs.add(new CallLogModel(displayName, number, dateStr, callType));
                    limit++;
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (callLogs.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }

        adapter = new HistoryAdapter(callLogs);
        recyclerView.setAdapter(adapter);
    }

    private int getCallType(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return CallLogModel.TYPE_INCOMING;
            case CallLog.Calls.OUTGOING_TYPE:
                return CallLogModel.TYPE_OUTGOING;
            case CallLog.Calls.MISSED_TYPE:
                return CallLogModel.TYPE_MISSED;
            default:
                return CallLogModel.TYPE_MISSED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_READ_CALL_LOG) {
            loadCallHistory();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCallHistory();
    }
}
