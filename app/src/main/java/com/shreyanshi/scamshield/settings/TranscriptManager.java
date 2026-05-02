package com.shreyanshi.scamshield.settings;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TranscriptManager {
    private static final String TAG = "ScamShield-Transcript";
    private static final String TRANSCRIPT_FOLDER = "ScamShield/Transcripts";
    private static final String FILE_PREFIX = "CallTranscript_";
    private static final String FILE_EXTENSION = ".txt";
    
    private final Context context;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat timeFormat;
    
    public TranscriptManager(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        this.timeFormat = new SimpleDateFormat("HH-mm-ss", Locale.US);
    }
    
    public String saveTranscript(String phoneNumber, String transcriptText) {
        if (transcriptText == null || transcriptText.isEmpty()) {
            Log.w(TAG, "Transcript is empty, not saving");
            return null;
        }
        
        try {
            String fileName = generateFileName(phoneNumber);
            String filePath = TRANSCRIPT_FOLDER + "/" + fileName;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveWithMediaStore(filePath, transcriptText);
            } else {
                return saveWithFileSystem(fileName, transcriptText);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving transcript: " + e.getMessage());
            return null;
        }
    }
    
    private String generateFileName(String phoneNumber) {
        String date = dateFormat.format(new Date());
        String time = timeFormat.format(new Date());
        String number = sanitizeFileName(phoneNumber);
        
        return FILE_PREFIX + number + "_" + date + "_" + time + FILE_EXTENSION;
    }
    
    private String sanitizeFileName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^0-9+]", "");
    }
    
    private String saveWithMediaStore(String filePath, String content) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, new File(filePath).getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + 
                TRANSCRIPT_FOLDER);
        
        android.net.Uri uri = context.getContentResolver().insert(
            MediaStore.Files.getContentUri("external"), values
        );
        
        if (uri == null) {
            throw new IOException("Failed to create MediaStore entry");
        }
        
        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("Failed to open output stream");
            }
            os.write(content.getBytes());
        }
        
        Log.i(TAG, "✅ Transcript saved via MediaStore: " + filePath);
        return filePath;
    }
    
    private String saveWithFileSystem(String fileName, String content) throws IOException {
        File directory = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            TRANSCRIPT_FOLDER
        );
        
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory.getPath());
            }
        }
        
        File file = new File(directory, fileName);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
        }
        
        Log.i(TAG, "✅ Transcript saved: " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }
    
    public boolean deleteTranscript(String filePath) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.Uri uri = MediaStore.Files.getContentUri("external");
                return context.getContentResolver().delete(uri, 
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                    new String[]{new File(filePath).getName()}) > 0;
            } else {
                File file = new File(filePath);
                return file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting transcript: " + e.getMessage());
            return false;
        }
    }
}