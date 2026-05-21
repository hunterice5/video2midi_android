package com.video2midi;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.video2midi.core.KeyPositionCalculator;
import com.video2midi.core.MidiGenerator;
import com.video2midi.core.VideoProcessor;
import com.video2midi.model.MidiNote;
import com.video2midi.model.Preferences;
import com.video2midi.utils.MidiWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private Button btnSelectVideo;
    private Button btnPreview;
    private Button btnSettings;
    private TextView tvVideoPath;
    private TextView tvStatus;
    
    private Preferences preferences;
    private String currentVideoPath;
    private VideoProcessor videoProcessor;
    
    private ActivityResultLauncher<String> videoPickerLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "MainActivity created - using Android Media API (no OpenCV)");
        
        // Инициализация preferences
        preferences = new Preferences();
        preferences.load(this);
        
        // Инициализация позиций клавиш
        KeyPositionCalculator.updateKeyPositions(preferences);
        
        initViews();
        setupListeners();
        checkPermissions();
        
        // Инициализация video picker launcher
        videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    onVideoSelected(uri);
                }
            }
        );
    }
    
    private void initViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnPreview = findViewById(R.id.btnPreview);
        btnSettings = findViewById(R.id.btnSettings);
        tvVideoPath = findViewById(R.id.tvVideoPath);
        tvStatus = findViewById(R.id.tvStatus);
        
        btnPreview.setEnabled(false);
    }
    
    private void setupListeners() {
        btnSelectVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectVideo();
            }
        });
        
        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPreview();
            }
        });
        
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

    }
    
    private void checkPermissions() {
        String[] permissions;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.READ_MEDIA_VIDEO
            };
        } else {
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, 
                                          int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "Permissions are required for app to work", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void selectVideo() {
        videoPickerLauncher.launch("video/*");
    }
    
    private void onVideoSelected(Uri videoUri) {
        // Сохраняем URI как строку
        currentVideoPath = videoUri.toString();
        
        Log.d(TAG, "Video selected: " + currentVideoPath);
        
        // Освобождаем предыдущий процессор если был
        if (videoProcessor != null) {
            videoProcessor.release();
        }
        
        // Создаем новый процессор с передачей Context
        videoProcessor = new VideoProcessor(this, currentVideoPath, preferences);
        
        if (videoProcessor.getFrameCount() == 0) {
            Toast.makeText(this, "Failed to open video file", Toast.LENGTH_SHORT).show();
            currentVideoPath = null;
            videoProcessor = null;
            return;
        }
        
        String displayName = getFileName(videoUri);
        
        tvVideoPath.setText("Video: " + displayName);
        tvStatus.setText(String.format("Frames: %d, FPS: %.2f, Resolution: %dx%d",
            videoProcessor.getFrameCount(),
            videoProcessor.getFPS(),
            videoProcessor.getVideoWidth(),
            videoProcessor.getVideoHeight()));
        
        btnPreview.setEnabled(true);

        Toast.makeText(this, "Video loaded successfully", Toast.LENGTH_SHORT).show();
    }
    
    private String getFileName(Uri uri) {
        String fileName = "Selected Video";
        
        try {
            android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null
            );
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        );
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
        }
        
        return fileName;
    }
    
    private void openPreview() {
        if (currentVideoPath == null) {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("videoPath", currentVideoPath);
        startActivity(intent);
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, 1);
    }
    
    private void openColorMap() {
        if (currentVideoPath == null) {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(this, ColorMapActivity.class);
        intent.putExtra("videoPath", currentVideoPath);
        startActivityForResult(intent, 2);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            // Перезагружаем настройки после возврата из Settings или ColorMap
            preferences.load(this);
            
            if (requestCode == 1) {
                // Обновляем позиции клавиш после изменения настроек
                KeyPositionCalculator.updateKeyPositions(preferences);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoProcessor != null) {
            videoProcessor.release();
        }
    }
}
