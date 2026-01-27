package com.shreyanshi.scamshield.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

public class ScamDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "scamshield.db";
    private static final int DB_VERSION = 2; // Incremented version

    // Scam Records Table
    public static final String TABLE_SCAMS = "scam_history";
    public static final String COL_SCAM_ID = "id";
    public static final String COL_SCAM_DATE = "date";
    public static final String COL_SCAM_REASON = "reason";

    // Call History Table
    public static final String TABLE_CALLS = "call_history";
    public static final String COL_CALL_ID = "id";
    public static final String COL_CALL_NUMBER = "number";
    public static final String COL_CALL_DATE = "date_time";
    public static final String COL_CALL_IS_SCAM = "is_scam";

    public ScamDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createScamTable = "CREATE TABLE " + TABLE_SCAMS + " (" +
                COL_SCAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SCAM_DATE + " TEXT, " +
                COL_SCAM_REASON + " TEXT)";
        
        String createCallTable = "CREATE TABLE " + TABLE_CALLS + " (" +
                COL_CALL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CALL_NUMBER + " TEXT, " +
                COL_CALL_DATE + " TEXT, " +
                COL_CALL_IS_SCAM + " INTEGER)";

        db.execSQL(createScamTable);
        db.execSQL(createCallTable);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Ensure tables exist even if DB file existed before
        String createScamTable = "CREATE TABLE IF NOT EXISTS " + TABLE_SCAMS + " (" +
                COL_SCAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SCAM_DATE + " TEXT, " +
                COL_SCAM_REASON + " TEXT)";
        String createCallTable = "CREATE TABLE IF NOT EXISTS " + TABLE_CALLS + " (" +
                COL_CALL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CALL_NUMBER + " TEXT, " +
                COL_CALL_DATE + " TEXT, " +
                COL_CALL_IS_SCAM + " INTEGER)";
        db.execSQL(createScamTable);
        db.execSQL(createCallTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCAMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CALLS);
        onCreate(db);
    }

    // --- SCAM METHODS ---
    public void insertScam(String date, String reason) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SCAM_DATE, date);
        cv.put(COL_SCAM_REASON, reason);
        db.insert(TABLE_SCAMS, null, cv);
        db.close();
    }

    public Cursor getAllScams() {
        SQLiteDatabase db = this.getReadableDatabase();
        try {
            return db.rawQuery("SELECT * FROM " + TABLE_SCAMS + " ORDER BY " + COL_SCAM_ID + " DESC", null);
        } catch (SQLiteException e) {
            // Table missing or other DB problem — return an empty cursor with expected columns
            MatrixCursor mc = new MatrixCursor(new String[] { COL_SCAM_ID, COL_SCAM_DATE, COL_SCAM_REASON });
            return mc;
        }
    }

    // --- CALL HISTORY METHODS ---
    public void insertCallLog(String number, String dateTime, boolean isScam) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CALL_NUMBER, number);
        cv.put(COL_CALL_DATE, dateTime);
        cv.put(COL_CALL_IS_SCAM, isScam ? 1 : 0);
        db.insert(TABLE_CALLS, null, cv);
        db.close();
    }

    public Cursor getAllCalls() {
        SQLiteDatabase db = this.getReadableDatabase();
        try {
            return db.rawQuery("SELECT * FROM " + TABLE_CALLS + " ORDER BY " + COL_CALL_ID + " DESC", null);
        } catch (SQLiteException e) {
            // Table missing or other DB problem — return an empty cursor with expected columns
            MatrixCursor mc = new MatrixCursor(new String[] { COL_CALL_ID, COL_CALL_NUMBER, COL_CALL_DATE, COL_CALL_IS_SCAM });
            return mc;
        }
    }
}
