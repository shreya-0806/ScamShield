package com.shreyanshi.scamshield.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BlockedNumberDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "blocked_numbers.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_NAME = "blocked";
    private static final String COL_NUMBER = "number";

    public BlockedNumberDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_NAME +
                        " (" + COL_NUMBER + " TEXT PRIMARY KEY)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // Add number
    public void blockNumber(String number) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NUMBER, number);
        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // Remove number
    public void unblockNumber(String number) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, COL_NUMBER + "=?", new String[]{number});
    }

    // Check if blocked
    public boolean isBlocked(String number) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_NUMBER + "=?",
                new String[]{number}
        );
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }
}
