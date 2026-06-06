package com.video2midi;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.video2midi.core.KeyPositionCalculator;
import com.video2midi.core.MidiGenerator;
//import com.video2midi.core.FrameStripExporter;
import com.video2midi.core.VideoProcessor;
import com.video2midi.model.ColorMap;
import com.video2midi.model.KeyPosition;
import com.video2midi.model.MidiNote;
import com.video2midi.model.Preferences;
import com.video2midi.ui.CustomPreviewView;
import com.video2midi.utils.MidiWriter;

import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.List;
import android.os.Environment;
import android.os.Build;
import androidx.core.content.FileProvider;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.provider.MediaStore;


public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "PreviewActivity";
    private static final int REQUEST_COLOR_MAP = 100;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        com.video2midi.model.Preferences preferences = new com.video2midi.model.Preferences();
        preferences.load(newBase);
        super.attachBaseContext(com.video2midi.utils.LocaleHelper.wrapContext(newBase, preferences.getLanguage()));
    }
    
    // Класс для результата загрузки (вынесен из вложенного класса)
    private static class LoadResult {
        Bitmap bitmap;
        boolean fromCache;
        long loadTimeMs;
        
        LoadResult(Bitmap bitmap, boolean fromCache, long loadTimeMs) {
            this.bitmap = bitmap;
            this.fromCache = fromCache;
            this.loadTimeMs = loadTimeMs;
        }
    }
    
    private DrawerLayout drawerLayout;
    private CustomPreviewView previewView;
    private SeekBar seekBarFrame;
    private SeekBar seekKeyWidth;
    private TextView tvFrameNumber;
    private TextView tvKeyWidth;
    private Button btnPrevFrame;
    private Button btnPlayPause;
    private Button btnNextFrame;
    private boolean isPlaying = false;
    private android.os.Handler playbackHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable playbackRunnable;
    private Button btnClose;
    private Button btnMenu;
    private Button btnToggleKeyboard;
    private Button btnToggleSparks;
    private Button btnColorPicker;
    private Button btnColorMap;
    private Button btnConvert;
    private Button btnSaveSettings;
    private Button btnAlignVertical;
    private Button btnAlignHorizontal;
    private Button btnResetKey;
    private Button btnResetAllKeys;
    private Button btnMoveKeyboard;
    private Button btnResetView;

    private Button btnIncKeyWidth;
    private Button btnDecKeyWidth;


    private CheckBox cbShowKeyNumbers;
    private CheckBox cbShowOctaveMarker;
    private CheckBox cbShowKeyPresses;
    
    private VideoProcessor videoProcessor;
    private Preferences preferences;
    private String videoPath;
    
    private LoadFrameTask currentTask;
    private boolean colorPickerMode = false;
    private boolean showKeyboard = true;
    private boolean showSparks = false;
    private boolean moveKeyboardMode = false;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        
        videoPath = getIntent().getStringExtra("videoPath");
        
        if (videoPath == null) {
            finish();
            return;
        }
        
        preferences = new Preferences();
        preferences.load(this);
        
        // Auto-initialize key positions if they are all at (0, 0) (uninitialized)
        boolean needsInit = true;
        java.util.List<com.video2midi.model.KeyPosition> kps = preferences.getKeysPositions();
        if (kps != null && !kps.isEmpty()) {
            for (com.video2midi.model.KeyPosition kp : kps) {
                if (kp.getX() != 0 || kp.getY() != 0) {
                    needsInit = false;
                    break;
                }
            }
        }
        if (needsInit) {
            KeyPositionCalculator.updateKeyPositions(preferences);
            preferences.save(this);
            Log.d(TAG, "Key positions auto-initialized with defaults");
        }
        
        videoProcessor = new VideoProcessor(this, videoPath, preferences);
        
        if (videoProcessor.getFrameCount() == 0) {
            Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        setupListeners();
        
        // Показываем первый кадр
        showFrame(0);
    }

    
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        previewView = findViewById(R.id.previewView);
        seekBarFrame = findViewById(R.id.seekBarFrame);
        seekKeyWidth = findViewById(R.id.seekKeyWidth);
        tvFrameNumber = findViewById(R.id.tvFrameNumber);
        tvKeyWidth = findViewById(R.id.tvKeyWidth);
        btnPrevFrame = findViewById(R.id.btnPrevFrame);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNextFrame = findViewById(R.id.btnNextFrame);
        btnClose = findViewById(R.id.btnClose);
        btnMenu = findViewById(R.id.btnMenu);
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard);
        btnToggleSparks = findViewById(R.id.btnToggleSparks);
        btnColorPicker = findViewById(R.id.btnColorPicker);
        btnColorMap = findViewById(R.id.btnColorMap);
        btnConvert = findViewById(R.id.btnConvert);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnAlignVertical = findViewById(R.id.btnAlignVertical);
        btnAlignHorizontal = findViewById(R.id.btnAlignHorizontal);
        btnResetKey = findViewById(R.id.btnResetKey);
        btnResetAllKeys = findViewById(R.id.btnResetAllKeys);
        btnMoveKeyboard = findViewById(R.id.btnMoveKeyboard);
        btnResetView = findViewById(R.id.btnResetView);

        btnIncKeyWidth= findViewById(R.id.btnIncKeyWidth);
        btnDecKeyWidth= findViewById(R.id.btnDecKeyWidth);

        cbShowKeyNumbers = findViewById(R.id.cbShowKeyNumbers);
        cbShowOctaveMarker = findViewById(R.id.cbShowOctaveMarker);
        cbShowKeyPresses = findViewById(R.id.cbShowKeyPresses);
        
        //seekBarFrame.setMax(videoProcessor.getFrameCount() - 1);
        int maxFrame = videoProcessor.getFrameCount() - 1;
        Log.d(TAG, "Setting SeekBar max to: " + maxFrame);
        seekBarFrame.setMax(maxFrame);
        seekBarFrame.setProgress(0);
        
        previewView.setPreferences(preferences);
        previewView.setShowKeyboard(showKeyboard);
        previewView.setShowSparks(showSparks);
        previewView.setShowKeyPresses(true);
        
        seekKeyWidth.setProgress(preferences.getWhiteKeyWidth() - 10);
        tvKeyWidth.setText(String.valueOf(preferences.getWhiteKeyWidth()));
        
        cbShowKeyPresses.setChecked(true);
    }

    private void setupListeners() {
        btnMenu.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(Gravity.END)) {
                drawerLayout.closeDrawer(Gravity.END);
            } else {
                drawerLayout.openDrawer(Gravity.END);
            }
        });

        seekKeyWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int width = progress + 10;
                tvKeyWidth.setText(String.valueOf(width));
                if (fromUser) {
                    preferences.setWhiteKeyWidth(width);
                    KeyPositionCalculator.updateKeyPositions(preferences);
                    previewView.setPreferences(preferences);
                    previewView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnIncKeyWidth.setOnClickListener(v -> {
            int width = seekKeyWidth.getProgress()+1;
            Log.d(TAG, "btnIncKeyWidth clicked, current seekbar: " + width);

            if (width > 400 ) {
                width = 400;
            }
            Log.d(TAG, "Setting new frame: " + width);
            seekKeyWidth.setProgress(width);
            preferences.setWhiteKeyWidth(width+10);
            KeyPositionCalculator.updateKeyPositions(preferences);
            previewView.setPreferences(preferences);
            previewView.invalidate();
        });

        btnDecKeyWidth.setOnClickListener(v -> {
            int width = seekKeyWidth.getProgress()-1;
            Log.d(TAG, "btnIncKeyWidth clicked, current seekbar: " + width);

            if (width < 1 ) {
                width = 1;
            }
            Log.d(TAG, "Setting new frame: " + width);
            seekKeyWidth.setProgress(width);
            preferences.setWhiteKeyWidth(width+10);
            KeyPositionCalculator.updateKeyPositions(preferences);
            previewView.setPreferences(preferences);
            previewView.invalidate();
        });


        btnPrevFrame.setOnClickListener(v -> {
            int currentFrame = seekBarFrame.getProgress();
            Log.d(TAG, "btnPrevFrame clicked, current seekbar: " + currentFrame);

            if (currentFrame > 0) {
                int newFrame = currentFrame - 1;
                Log.d(TAG, "Setting new frame: " + newFrame);

                seekBarFrame.setProgress(newFrame);
                showFrame(newFrame);
            } else {
                Log.d(TAG, "Already at frame 0");
            }
        });

        btnNextFrame.setOnClickListener(v -> {
            int currentFrame = seekBarFrame.getProgress();
            Log.d(TAG, "btnNextFrame clicked, current seekbar: " + currentFrame);

            if (currentFrame < videoProcessor.getFrameCount() - 1) {
                int newFrame = currentFrame + 1;
                Log.d(TAG, "Setting new frame: " + newFrame);

                seekBarFrame.setProgress(newFrame);
                showFrame(newFrame);
            } else {
                Log.d(TAG, "Already at last frame");
            }
        });

        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                isPlaying = false;
                btnPlayPause.setText("▶");
                videoProcessor.stopSequentialDecoding();
                btnPrevFrame.setEnabled(true);
                btnNextFrame.setEnabled(true);
                seekBarFrame.setEnabled(true);
                btnConvert.setEnabled(true);
                btnColorPicker.setEnabled(true);
                btnColorMap.setEnabled(true);
                btnMenu.setEnabled(true);
                btnClose.setEnabled(true);
                playbackHandler.removeCallbacks(playbackRunnable);
            } else {
                isPlaying = true;
                btnPlayPause.setText("⏸");
                btnPrevFrame.setEnabled(false);
                btnNextFrame.setEnabled(false);
                seekBarFrame.setEnabled(false);
                btnConvert.setEnabled(false);
                btnColorPicker.setEnabled(false);
                btnColorMap.setEnabled(false);
                btnMenu.setEnabled(false);
                btnClose.setEnabled(false);

                int startFrame = seekBarFrame.getProgress();
                videoProcessor.startSequentialDecoding(startFrame, 1);

                playbackRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!isPlaying) return;

                        int currentFrame = seekBarFrame.getProgress();
                        int nextFrame = currentFrame + 1;
                        if (nextFrame >= videoProcessor.getFrameCount()) {
                            runOnUiThread(() -> {
                                if (isPlaying) {
                                    btnPlayPause.performClick();
                                }
                            });
                            return;
                        }

                        long frameStartTime = System.currentTimeMillis();
                        new Thread(() -> {
                            boolean success = videoProcessor.processFrame(nextFrame);
                            if (success) {
                                Bitmap frame = videoProcessor.getCurrentFrame();
                                if (frame != null && !frame.isRecycled()) {
                                    Bitmap frameCopy = frame.copy(Bitmap.Config.ARGB_8888, true);
                                    runOnUiThread(() -> {
                                        if (isPlaying) {
                                            previewView.setDisplayBitmap(frameCopy);
                                            seekBarFrame.setProgress(nextFrame);
                                            tvFrameNumber.setText(String.format("Frame: %d / %d",
                                                    nextFrame, videoProcessor.getFrameCount()));

                                            long elapsed = System.currentTimeMillis() - frameStartTime;
                                            double fps = videoProcessor.getFPS();
                                            long delay = (long) (1000.0 / fps) - elapsed;
                                            playbackHandler.postDelayed(playbackRunnable, Math.max(1, delay));
                                        } else {
                                            frameCopy.recycle();
                                        }
                                    });
                                }
                            } else {
                                runOnUiThread(() -> {
                                    if (isPlaying) {
                                        btnPlayPause.performClick();
                                        Toast.makeText(PreviewActivity.this, "Playback error", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();
                    }
                };
                playbackHandler.post(playbackRunnable);
            }
        });

        seekBarFrame.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, String.format("SeekBar changed: progress=%d, fromUser=%b", progress, fromUser));

                // Загружаем кадр только при изменении пользователем
                if (fromUser) {
                    Log.d(TAG, "User changed seekbar, loading frame: " + progress);
                    showFrame(progress);
                } else {
                    Log.d(TAG, "Programmatic seekbar change, skipping");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "SeekBar tracking started");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "SeekBar tracking stopped at: " + seekBar.getProgress());
            }
        });

        btnToggleKeyboard.setOnClickListener(v -> {
            showKeyboard = !showKeyboard;
            previewView.setShowKeyboard(showKeyboard);
            btnToggleKeyboard.setBackgroundTintList(
                    getColorStateList(showKeyboard ? R.color.success : R.color.secondary)
            );
        });

        btnToggleSparks.setOnClickListener(v -> {
            showSparks = !showSparks;
            previewView.setShowSparks(showSparks);
            btnToggleSparks.setBackgroundTintList(
                    getColorStateList(showSparks ? R.color.success : R.color.secondary)
            );
        });

        btnColorPicker.setOnClickListener(v -> toggleColorPickerMode());

        btnColorMap.setOnClickListener(v -> openColorMap());

        btnConvert.setOnClickListener(v -> startConversion());

        btnSaveSettings.setOnClickListener(v -> saveSettings());

        btnAlignVertical.setOnClickListener(v -> alignKeysVertical());

        btnAlignHorizontal.setOnClickListener(v -> alignKeysHorizontal());

        btnResetKey.setOnClickListener(v -> resetSelectedKey());

        btnResetAllKeys.setOnClickListener(v -> resetAllKeys());

        btnMoveKeyboard.setOnClickListener(v -> toggleMoveKeyboardMode());

        btnResetView.setOnClickListener(v -> {
            previewView.resetTransform();
            Toast.makeText(this, "View reset", Toast.LENGTH_SHORT).show();
        });

        cbShowKeyPresses.setOnCheckedChangeListener((buttonView, isChecked) -> {
            previewView.setShowKeyPresses(isChecked);
        });

        btnClose.setOnClickListener(v -> {
            preferences.save(this);
            setResult(RESULT_OK);
            finish();
        });

        previewView.setOnKeyClickListener(keyIndex -> {
            if (!moveKeyboardMode) {
                Toast.makeText(PreviewActivity.this,
                        "Key " + keyIndex + " selected", Toast.LENGTH_SHORT).show();
            }
        });

        previewView.setOnColorPickListener((x, y, color) -> showColorPickedDialog(x, y, color));

        previewView.setOnKeyboardMovedListener((dx, dy) -> {
            // Можно добавить дополнительную логику
        });

        previewView.setOnTouchListener((v, event) -> {
            if (colorPickerMode && event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                previewView.pickColorAt(event.getX(), event.getY());
                return true;
            }
            return false;
        });
    }

    private void navigateToFrame(int frameNumber) {
        // Убираем старую задачу
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(true);
        }

        // ВАЖНО: Сначала обновляем SeekBar БЕЗ триггера события
        seekBarFrame.setProgress(frameNumber);

        // Потом принудительно загружаем кадр
        showFrame(frameNumber);
    }

    private void toggleMoveKeyboardMode() {
        moveKeyboardMode = !moveKeyboardMode;
        previewView.setMoveKeyboardMode(moveKeyboardMode);
        
        if (moveKeyboardMode) {
            btnMoveKeyboard.setText("Stop Move");
            btnMoveKeyboard.setBackgroundTintList(getColorStateList(R.color.danger));
            Toast.makeText(this, "Drag to move keyboard", Toast.LENGTH_LONG).show();
            
            if (colorPickerMode) {
                toggleColorPickerMode();
            }
        } else {
            btnMoveKeyboard.setText("Move Keyboard");
            btnMoveKeyboard.setBackgroundTintList(getColorStateList(R.color.accent));
        }
    }
    
    private void toggleColorPickerMode() {
        colorPickerMode = !colorPickerMode;
        
        if (colorPickerMode) {
            btnColorPicker.setText("Cancel");
            btnColorPicker.setBackgroundTintList(getColorStateList(R.color.danger));
            Toast.makeText(this, "Tap to pick color", Toast.LENGTH_SHORT).show();
            
            if (moveKeyboardMode) {
                toggleMoveKeyboardMode();
            }
        } else {
            btnColorPicker.setText("Pick");
            btnColorPicker.setBackgroundTintList(getColorStateList(R.color.success));
        }
    }
    
    private void showColorPickedDialog(int x, int y, int[] color) {
        colorPickerMode = false;
        btnColorPicker.setText("Pick");
        btnColorPicker.setBackgroundTintList(getColorStateList(R.color.success));
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_color_picked, null);
        
        View colorPreview = dialogView.findViewById(R.id.colorPreview);
        TextView tvColorInfo = dialogView.findViewById(R.id.tvColorInfo);
        TextView tvPosition = dialogView.findViewById(R.id.tvPosition);
        android.widget.Spinner spinnerSlots = dialogView.findViewById(R.id.spinnerSlots);
        
        colorPreview.setBackgroundColor(Color.rgb(color[0], color[1], color[2]));
        tvColorInfo.setText(String.format("RGB(%d, %d, %d)", color[0], color[1], color[2]));
        tvPosition.setText(String.format("Position: (%d, %d)", x, y));

        java.util.List<com.video2midi.model.ColorMap> colors = preferences.getKeypColors();
        java.util.List<String> slotDescriptions = new java.util.ArrayList<>();
        int defaultSelected = 0;
        boolean foundEmpty = false;

        for (int i = 0; i < colors.size(); i++) {
            com.video2midi.model.ColorMap cm = colors.get(i);
            if (cm.isEmpty()) {
                slotDescriptions.add(String.format("Slot %d (Empty)", i + 1));
                if (!foundEmpty) {
                    defaultSelected = i;
                    foundEmpty = true;
                }
            } else {
                slotDescriptions.add(String.format("Slot %d (RGB: %d, %d, %d)", i + 1, cm.getR(), cm.getG(), cm.getB()));
            }
        }

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, slotDescriptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSlots.setAdapter(adapter);
        spinnerSlots.setSelection(defaultSelected);
        
        new AlertDialog.Builder(this)
            .setTitle("Add to Color Map")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                int selectedIndex = spinnerSlots.getSelectedItemPosition();
                if (selectedIndex >= 0 && selectedIndex < colors.size()) {
                    com.video2midi.model.ColorMap selectedMap = colors.get(selectedIndex);
                    selectedMap.setR(color[0]);
                    selectedMap.setG(color[1]);
                    selectedMap.setB(color[2]);
                    preferences.setKeypColors(colors);
                    preferences.save(this);
                    Toast.makeText(this, "Saved to slot " + (selectedIndex + 1), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void openColorMap() {
        Intent intent = new Intent(this, ColorMapActivity.class);
        intent.putExtra("videoPath", videoPath);
        startActivityForResult(intent, REQUEST_COLOR_MAP);
    }
    
    private int selectedFrameStep = 1; // Default to High Precision (process 100% of frames)

    private void startConversion() {
        String[] options = {
            "High Precision (Slow, process 100% of frames)",
            "Normal Speed (2x Faster, process every 2nd frame)",
            "Fast (3x Faster, process every 3rd frame)",
            "Super Fast (4x Faster, process every 4th frame)"
        };
        int defaultChoice = 0; // "High Precision"
        selectedFrameStep = 1; // Reset default choice variable

        new AlertDialog.Builder(this)
            .setTitle("Choose Conversion Speed")
            .setSingleChoiceItems(options, defaultChoice, (dialog, which) -> {
                selectedFrameStep = which + 1;
            })
            .setPositiveButton("Convert", (dialog, which) -> {
                new ConversionTask(selectedFrameStep).execute();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void alignKeysVertical() {
        int selectedKey = previewView.getSelectedKeyIndex();
        if (selectedKey < 0) {
            Toast.makeText(this, "Select a key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        KeyPosition selectedPos = preferences.getKeysPositions().get(selectedKey);
        boolean isBlack = KeyPositionCalculator.isBlackKeyByIndex(selectedKey);
        
        for (int i = 0; i < preferences.getKeysPositions().size(); i++) {
            if (isBlack == KeyPositionCalculator.isBlackKeyByIndex(i)) {
                preferences.getKeysPositions().get(i).setY(selectedPos.getY());
            }
        }
        
        previewView.invalidate();
        Toast.makeText(this, "Keys aligned vertically", Toast.LENGTH_SHORT).show();
    }
    
    private void alignKeysHorizontal() {
        int selectedKey = previewView.getSelectedKeyIndex();
        if (selectedKey < 0) {
            Toast.makeText(this, "Select a key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        KeyPosition selectedPos = preferences.getKeysPositions().get(selectedKey);
        boolean isBlack = KeyPositionCalculator.isBlackKeyByIndex(selectedKey);
        
        for (int i = 0; i < preferences.getKeysPositions().size(); i++) {
            if (isBlack == KeyPositionCalculator.isBlackKeyByIndex(i)) {
                preferences.getKeysPositions().get(i).setX(selectedPos.getX());
            }
        }
        
        previewView.invalidate();
        Toast.makeText(this, "Keys aligned horizontally", Toast.LENGTH_SHORT).show();
    }
    
    private void resetSelectedKey() {
        int selectedKey = previewView.getSelectedKeyIndex();
        if (selectedKey < 0) {
            Toast.makeText(this, "Select a key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        KeyPositionCalculator.updateKeyPositions(preferences);
        previewView.setPreferences(preferences);
        previewView.invalidate();
        Toast.makeText(this, "Key reset", Toast.LENGTH_SHORT).show();
    }
    
    private void resetAllKeys() {
        new AlertDialog.Builder(this)
            .setTitle("Reset All Keys")
            .setMessage("Reset all keys to default positions?")
            .setPositiveButton("Reset", (dialog, which) -> {
                KeyPositionCalculator.updateKeyPositions(preferences);
                preferences.setXOffsetWhiteKeys(60);
                preferences.setYOffsetWhiteKeys(673);
                previewView.setPreferences(preferences);
                previewView.invalidate();
                Toast.makeText(this, "All keys reset", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void saveSettings() {
        preferences.save(this);
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        drawerLayout.closeDrawer(Gravity.END);
    }

    private void showFrame(int frameNumber) {
        Log.d(TAG, "===== showFrame START =====");
        Log.d(TAG, "Requested frame: " + frameNumber);
        Log.d(TAG, "Current seekBar progress: " + seekBarFrame.getProgress());

        // Отменяем предыдущую задачу
        if (currentTask != null && !currentTask.isCancelled()) {
            Log.d(TAG, "Cancelling previous task");
            currentTask.cancel(true);
        }

        // Запускаем новую задачу загрузки
        currentTask = new LoadFrameTask();
        currentTask.execute(frameNumber);

        Log.d(TAG, "===== showFrame END =====");
    }
    // AsyncTask для загрузки кадров
    private class LoadFrameTask extends AsyncTask<Integer, Void, Bitmap> {
        private int frameNumber;

        @Override
        protected void onPreExecute() {
            btnPrevFrame.setEnabled(false);
            btnNextFrame.setEnabled(false);
            seekBarFrame.setEnabled(false);
        }

        @Override
        protected Bitmap doInBackground(Integer... params) {
            frameNumber = params[0];

            Log.d(TAG, "Loading frame " + frameNumber);

            if (!videoProcessor.processFrame(frameNumber)) {
                Log.e(TAG, "Failed to process frame: " + frameNumber);
                return null;
            }

            Bitmap frame = videoProcessor.getCurrentFrame();
            if (frame == null) {
                Log.e(TAG, "Frame is null");
                return null;
            }

            return frame.copy(Bitmap.Config.ARGB_8888, true);
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null && !isCancelled()) {
                previewView.setDisplayBitmap(result);
                tvFrameNumber.setText(String.format("Frame: %d / %d",
                        frameNumber, videoProcessor.getFrameCount()));
            } else if (!isCancelled()) {
                Toast.makeText(PreviewActivity.this,
                        "Failed to load frame", Toast.LENGTH_SHORT).show();
            }

            btnPrevFrame.setEnabled(true);
            btnNextFrame.setEnabled(true);
            seekBarFrame.setEnabled(true);
        }
    }


    private class LoadFrameTask_old extends AsyncTask<Integer, Void, LoadResult> {
        private int frameNumber;

        @Override
        protected void onPreExecute() {
            btnPrevFrame.setEnabled(false);
            btnNextFrame.setEnabled(false);
            seekBarFrame.setEnabled(false);
        }

        @Override
        protected LoadResult doInBackground(Integer... params) {
            frameNumber = params[0];
            long startTime = System.currentTimeMillis();

            Log.d(TAG, "LoadFrameTask started for frame: " + frameNumber);

            if (isCancelled()) {
                Log.d(TAG, "Task cancelled before processing");
                return null;
            }

            // Всегда загружаем из видео
            Log.d(TAG, "Calling videoProcessor.processFrame(" + frameNumber + ")");
            boolean success = videoProcessor.processFrame(frameNumber);

            Log.d(TAG, "videoProcessor.processFrame returned: " + success);

            if (isCancelled()) {
                return null;
            }

            if (!success) {
                Log.e(TAG, "Failed to process frame " + frameNumber);
                return null;
            }

            Bitmap frame = videoProcessor.getCurrentFrame();
            Log.d(TAG, "getCurrentFrame returned: " + (frame != null ? "bitmap " + frame.hashCode() : "null"));

            if (frame == null || frame.isRecycled()) {
                Log.e(TAG, "Frame is null or recycled");
                return null;
            }

            long loadTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, String.format("Frame %d loaded in %dms", frameNumber, loadTime));

            return new LoadResult(frame, false, loadTime);
        }

        @Override
        protected void onPostExecute(LoadResult result) {
            if (result != null && result.bitmap != null && !result.bitmap.isRecycled()) {
                Log.d(TAG, String.format("Displaying frame %d: %dx%d, hash=%d",
                        frameNumber,
                        result.bitmap.getWidth(),
                        result.bitmap.getHeight(),
                        result.bitmap.hashCode()));

                previewView.setDisplayBitmap(result.bitmap);
                previewView.forceUpdateKeyPresses();

                String status = String.format("F:%d/%d",
                        frameNumber, videoProcessor.getFrameCount());

                if (result.fromCache) {
                    status += " [cached]";
                } else if (result.loadTimeMs > 100) {
                    status += String.format(" [%dms]", result.loadTimeMs);
                }

                tvFrameNumber.setText(status);

                Log.d(TAG, "Frame " + frameNumber + " displayed: " + status);
            } else {
                Log.e(TAG, "Failed to display frame " + frameNumber);
                Toast.makeText(PreviewActivity.this,
                        "Failed to load frame " + frameNumber, Toast.LENGTH_SHORT).show();
            }

            btnPrevFrame.setEnabled(frameNumber > 0);
            btnNextFrame.setEnabled(frameNumber < videoProcessor.getFrameCount() - 1);
            seekBarFrame.setEnabled(true);
        }

        @Override
        protected void onCancelled(LoadResult result) {
            Log.d(TAG, "Frame loading cancelled for frame " + frameNumber);

            if (result != null && result.bitmap != null && !result.bitmap.isRecycled()) {
                result.bitmap.recycle();
            }

            btnPrevFrame.setEnabled(true);
            btnNextFrame.setEnabled(true);
            seekBarFrame.setEnabled(true);
        }
    }

    // ConversionTask остается без изменений...
    private class ConversionTask extends AsyncTask<Void, Object, File> {
        private ProgressDialog progressDialog;
        private MidiGenerator midiGenerator;
        private String currentPhase = "Processing...";
        private int currentNoteCount = 0;
        private boolean wasCancelled = false;
        private int frameStep = 1;

        // ДОБАВЛЕНО: Для отслеживания времени
        private long startTimeMillis;
        private int totalFrames;
        private int currentFrame;

        public ConversionTask() {
            this.frameStep = 1;
        }

        public ConversionTask(int frameStep) {
            this.frameStep = frameStep;
        }

        @Override
        protected void onPreExecute() {
            startTimeMillis = System.currentTimeMillis();
            totalFrames = videoProcessor.getFrameCount();
            currentFrame = 0;

            progressDialog = new ProgressDialog(PreviewActivity.this);
            progressDialog.setTitle("Converting to MIDI");
            progressDialog.setMessage(currentPhase);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setCancelable(true);
            progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel",
                    (dialog, which) -> {
                        if (midiGenerator != null) {
                            midiGenerator.cancel();
                            wasCancelled = true;
                        }
                        cancel(true);
                    });
            progressDialog.show();
        }

        @Override
        protected File doInBackground(Void... voids) {
            try {
                midiGenerator = new MidiGenerator(videoProcessor, preferences);

                midiGenerator.setProgressCallback(new MidiGenerator.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total) {
                        currentFrame = current;
                        int progress = (int) ((current * 100.0) / total);
                        publishProgress("progress", progress, current, total);
                    }

                    @Override
                    public void onFrameProcessed(int frameNumber) {
                        currentFrame = frameNumber;
                        if (frameNumber % 10 == 0) { // ИЗМЕНЕНО: Обновляем чаще для более точного ETA
                            publishProgress("frame", frameNumber);
                        }
                    }

                    @Override
                    public void onNotesUpdated(int noteCount) {
                        currentNoteCount = noteCount;
                        publishProgress("notes", noteCount);
                    }
                });

                boolean success = midiGenerator.process(0, totalFrames, frameStep);

                if (isCancelled() && midiGenerator.getNoteCount() == 0) {
                    return null;
                }

                publishProgress("phase", "Finalizing MIDI...");
                midiGenerator.syncNotesStartPosition();
                midiGenerator.mergeOverlappingNotes();

                List<MidiNote> notes = midiGenerator.getNotes();
                if (notes.isEmpty()) {
                    return null;
                }

                File outputFile = saveMidiFile(notes);

                return outputFile;

            } catch (Exception e) {
                Log.e(TAG, "Conversion error", e);
                return null;
            }
        }

        private File saveMidiFile(List<MidiNote> notes) throws IOException {
            String baseName = wasCancelled ? "video2midi_partial" : "video2midi_output";
            String displayName = baseName;
            
            // Try using MediaStore on Q+ to write directly to public Music directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName + ".mid");
                    values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/midi");
                    values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
                    values.put(MediaStore.Audio.Media.IS_PENDING, 1);

                    ContentResolver resolver = getContentResolver();
                    Uri collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    Uri uri = resolver.insert(collectionUri, values);
                    if (uri != null) {
                        try (java.io.OutputStream os = resolver.openOutputStream(uri)) {
                            if (os != null) {
                                MidiWriter writer = new MidiWriter(preferences.getTempo(), "Video2MIDI");
                                writer.writeNotes(notes, os);
                                
                                values.clear();
                                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                                resolver.update(uri, values, null, null);
                                
                                Log.d(TAG, "Saved MIDI to public Music via MediaStore: " + uri.toString());
                                return new File("/storage/emulated/0/" + Environment.DIRECTORY_MUSIC, displayName + ".mid");
                            }
                        } catch (Exception e) {
                            resolver.delete(uri, null, null);
                            throw e;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to save via MediaStore, falling back to File API", e);
                }
            }

            // Fallback for pre-Q or if MediaStore failed:
            File musicDir = null;
            try {
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (!publicDir.exists()) {
                    publicDir.mkdirs();
                }
                musicDir = publicDir;
            } catch (Exception e) {
                Log.w(TAG, "Cannot write to public Music directory via File API, falling back to app files dir", e);
                musicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                if (musicDir != null && !musicDir.exists()) {
                    musicDir.mkdirs();
                }
            }

            if (musicDir == null) {
                musicDir = getCacheDir();
            }

            File outputFile = new File(musicDir, displayName + ".mid");
            int counter = 1;
            while (outputFile.exists()) {
                outputFile = new File(musicDir, displayName + "_" + counter + ".mid");
                counter++;
            }

            MidiWriter writer = new MidiWriter(preferences.getTempo(), "Video2MIDI");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                writer.writeNotes(notes, fos);
            }

            // Trigger Media Scanner to make the file immediately visible
            try {
                android.media.MediaScannerConnection.scanFile(
                    PreviewActivity.this,
                    new String[]{outputFile.getAbsolutePath()},
                    null,
                    (path, uri) -> Log.d(TAG, "MediaScanner scanned: " + path + " -> uri: " + uri)
                );
            } catch (Exception ignored) {}

            return outputFile;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            if (progressDialog == null || !progressDialog.isShowing()) {
                return;
            }

            if (values.length >= 2) {
                String type = (String) values[0];

                switch (type) {
                    case "phase":
                        currentPhase = (String) values[1];
                        progressDialog.setMessage(currentPhase);
                        break;

                    case "progress":
                        int progress = (Integer) values[1];
                        int current = values.length >= 3 ? (Integer) values[2] : currentFrame;
                        int total = values.length >= 4 ? (Integer) values[3] : totalFrames;

                        progressDialog.setProgress(progress);

                        // ДОБАВЛЕНО: Вычисление времени
                        long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
                        long elapsedSeconds = elapsedMillis / 1000;

                        String elapsedTime = formatTime(elapsedSeconds);
                        String estimatedTime = "calculating...";
                        String speed = "0.0";

                        if (current > 0 && elapsedSeconds > 0) {
                            // Скорость обработки (кадров в секунду)
                            float framesPerSecond = current / (float) elapsedSeconds;
                            speed = String.format("%.1f", framesPerSecond);

                            // Оставшиеся кадры
                            int remainingFrames = total - current;

                            // Оставшееся время
                            long remainingSeconds = (long) (remainingFrames / Math.max(0.1f, framesPerSecond));
                            estimatedTime = formatTime(remainingSeconds);
                        }

                        // ИЗМЕНЕНО: Расширенное сообщение с временем
                        String message = String.format(
                                "Processing... %d%%\n" +
                                        "Notes: %d\n" +
                                        "Elapsed: %s\n" +
                                        "Remaining: %s\n" +
                                        "Speed: %s fps",
                                progress, currentNoteCount, elapsedTime, estimatedTime, speed
                        );

                        progressDialog.setMessage(message);
                        break;

                    case "frame":
                        int frameNumber = (Integer) values[1];
                        currentFrame = frameNumber;

                        // Обновляем время при обработке кадров
                        long elapsed = System.currentTimeMillis() - startTimeMillis;
                        long elapsedSec = elapsed / 1000;

                        String elTime = formatTime(elapsedSec);
                        String estTime = "calculating...";
                        String fps = "0.0";

                        if (frameNumber > 0 && elapsedSec > 0) {
                            float framesPerSec = frameNumber / (float) elapsedSec;
                            fps = String.format("%.1f", framesPerSec);

                            int remaining = totalFrames - frameNumber;
                            long remainingSec = (long) (remaining / Math.max(0.1f, framesPerSec));
                            estTime = formatTime(remainingSec);
                        }

                        progressDialog.setMessage(String.format(
                                "Frame %d/%d\n" +
                                        "Notes: %d\n" +
                                        "Elapsed: %s\n" +
                                        "Remaining: %s\n" +
                                        "Speed: %s fps",
                                frameNumber, totalFrames, currentNoteCount, elTime, estTime, fps
                        ));
                        break;

                    case "notes":
                        currentNoteCount = (Integer) values[1];
                        break;
                }
            }
        }

        // ДОБАВЛЕНО: Метод форматирования времени
        private String formatTime(long totalSeconds) {
            if (totalSeconds < 0) {
                return "unknown";
            }

            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%d:%02d", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }

        @Override
        protected void onPostExecute(File result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            // ДОБАВЛЕНО: Показываем общее время конвертации
            long totalTime = (System.currentTimeMillis() - startTimeMillis) / 1000;
            String totalTimeStr = formatTime(totalTime);

            if (result != null) {
                String title = wasCancelled ? "Partial Conversion Saved" : "Success";
                String message = wasCancelled ?
                        String.format("Partial MIDI saved (%d notes) in %s:\n%s",
                                currentNoteCount, totalTimeStr, result.getAbsolutePath()) :
                        String.format("MIDI saved (%d notes) in %s:\n%s",
                                currentNoteCount, totalTimeStr, result.getAbsolutePath());

                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle("Error")
                        .setMessage("Conversion failed or no notes generated.\nTotal time: " + totalTimeStr)
                        .setPositiveButton("OK", null)
                        .show();
            }
        }

        @Override
        protected void onCancelled(File result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            // ДОБАВЛЕНО: Время до отмены
            long totalTime = (System.currentTimeMillis() - startTimeMillis) / 1000;
            String totalTimeStr = formatTime(totalTime);

            if (midiGenerator != null && midiGenerator.getNoteCount() > 0) {
                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle("Save Partial Result?")
                        .setMessage(String.format(
                                "Conversion was cancelled after %s.\n" +
                                        "Generated %d notes so far.\n\n" +
                                        "Do you want to save the partial MIDI file?",
                                totalTimeStr, currentNoteCount))
                        .setPositiveButton("Save", (dialog, which) -> {
                            new Thread(() -> {
                                try {
                                    List<MidiNote> notes = midiGenerator.getNotes();
                                    midiGenerator.syncNotesStartPosition();

                                    File outputFile = saveMidiFile(notes);

                                    runOnUiThread(() -> {
                                        new AlertDialog.Builder(PreviewActivity.this)
                                                .setTitle("Partial MIDI Saved")
                                                .setMessage(String.format("Saved %d notes (processed for %s) to:\n%s",
                                                        currentNoteCount, totalTimeStr, outputFile.getAbsolutePath()))
                                                .setPositiveButton("OK", null)
                                                .show();
                                    });

                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to save partial MIDI", e);
                                    runOnUiThread(() -> {
                                        Toast.makeText(PreviewActivity.this,
                                                "Failed to save partial MIDI", Toast.LENGTH_LONG).show();
                                    });
                                }
                            }).start();
                        })
                        .setNegativeButton("Discard", (dialog, which) -> {
                            Toast.makeText(PreviewActivity.this,
                                    String.format("Partial result discarded (ran for %s)", totalTimeStr),
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                Toast.makeText(PreviewActivity.this,
                        String.format("Conversion cancelled after %s (no notes generated)", totalTimeStr),
                        Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled() {
            onCancelled(null);
        }
    }

    private class ConversionTask_old extends AsyncTask<Void, Object, File> {
        private ProgressDialog progressDialog;
        private MidiGenerator midiGenerator;
        private String currentPhase = "Processing...";
        private int currentNoteCount = 0;
        private boolean wasCancelled = false;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PreviewActivity.this);
            progressDialog.setTitle("Converting to MIDI");
            progressDialog.setMessage(currentPhase);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setCancelable(true);
            progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel",
                    (dialog, which) -> {
                        if (midiGenerator != null) {
                            midiGenerator.cancel();
                            wasCancelled = true;
                        }
                        cancel(true);
                    });
            progressDialog.show();
        }

        @Override
        protected File doInBackground(Void... voids) {
            try {
                midiGenerator = new MidiGenerator(videoProcessor, preferences);

                midiGenerator.setProgressCallback(new MidiGenerator.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total) {
                        int progress = (int) ((current * 100.0) / total);
                        publishProgress("progress", progress);
                    }

                    @Override
                    public void onFrameProcessed(int frameNumber) {
                        if (frameNumber % 100 == 0) {
                            publishProgress("frame", frameNumber);
                        }
                    }

                    @Override
                    public void onNotesUpdated(int noteCount) {
                        currentNoteCount = noteCount;
                        publishProgress("notes", noteCount);
                    }
                });

                int totalFrames = videoProcessor.getFrameCount();
                boolean success = midiGenerator.process(0, totalFrames);

                // ИЗМЕНЕНО: Даже при отмене продолжаем если есть ноты
                if (isCancelled() && midiGenerator.getNoteCount() == 0) {
                    return null;
                }

                publishProgress("phase", "Finalizing MIDI...");
                midiGenerator.syncNotesStartPosition();

                List<MidiNote> notes = midiGenerator.getNotes();
                if (notes.isEmpty()) {
                    return null;
                }

                // ДОБАВЛЕНО: Сохраняем даже при отмене
                File outputFile = saveMidiFile(notes);

                return outputFile;

            } catch (Exception e) {
                Log.e(TAG, "Conversion error", e);
                return null;
            }
        }

        // ДОБАВЛЕНО: Вынесли сохранение в отдельный метод
        private File saveMidiFile(List<MidiNote> notes) throws IOException {
            File musicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC);
            if (!musicDir.exists()) {
                musicDir.mkdirs();
            }

            String baseName = wasCancelled ? "video2midi_partial" : "video2midi_output";
            File outputFile = new File(musicDir, baseName + ".mid");

            int counter = 1;
            while (outputFile.exists()) {
                outputFile = new File(musicDir, baseName + "_" + counter + ".mid");
                counter++;
            }

            MidiWriter writer = new MidiWriter(preferences.getTempo(), "Video2MIDI");
            writer.writeNotes(notes, outputFile);

            return outputFile;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            if (progressDialog == null || !progressDialog.isShowing()) {
                return;
            }

            if (values.length >= 2) {
                String type = (String) values[0];

                switch (type) {
                    case "phase":
                        currentPhase = (String) values[1];
                        progressDialog.setMessage(currentPhase);
                        break;

                    case "progress":
                        int progress = (Integer) values[1];
                        progressDialog.setProgress(progress);
                        // ИЗМЕНЕНО: Добавили количество нот
                        progressDialog.setMessage(String.format("Processing... %d%% | Notes: %d",
                                progress, currentNoteCount));
                        break;

                    case "frame":
                        int frameNumber = (Integer) values[1];
                        progressDialog.setMessage(String.format("Frame %d | Notes: %d",
                                frameNumber, currentNoteCount));
                        break;

                    case "notes":
                        currentNoteCount = (Integer) values[1];
                        // Обновляем сообщение с новым количеством нот
                        break;
                }
            }
        }

        @Override
        protected void onPostExecute(File result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (result != null) {
                String title = wasCancelled ? "Partial Conversion Saved" : "Success";
                String message = wasCancelled ?
                        String.format("Partial MIDI saved (%d notes):\n%s", currentNoteCount, result.getAbsolutePath()) :
                        String.format("MIDI saved (%d notes):\n%s", currentNoteCount, result.getAbsolutePath());

                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle("Error")
                        .setMessage("Conversion failed or no notes generated.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }

        @Override
        protected void onCancelled(File result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            // ДОБАВЛЕНО: Спрашиваем сохранить ли частичный результат
            if (midiGenerator != null && midiGenerator.getNoteCount() > 0) {
                new AlertDialog.Builder(PreviewActivity.this)
                        .setTitle("Save Partial Result?")
                        .setMessage(String.format("Conversion was cancelled.\nGenerated %d notes so far.\n\nDo you want to save the partial MIDI file?",
                                currentNoteCount))
                        .setPositiveButton("Save", (dialog, which) -> {
                            // Сохраняем в фоновом потоке
                            new Thread(() -> {
                                try {
                                    List<MidiNote> notes = midiGenerator.getNotes();
                                    midiGenerator.syncNotesStartPosition();

                                    File outputFile = saveMidiFile(notes);

                                    runOnUiThread(() -> {
                                        new AlertDialog.Builder(PreviewActivity.this)
                                                .setTitle("Partial MIDI Saved")
                                                .setMessage(String.format("Saved %d notes to:\n%s",
                                                        currentNoteCount, outputFile.getAbsolutePath()))
                                                .setPositiveButton("OK", null)
                                                .show();
                                    });

                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to save partial MIDI", e);
                                    runOnUiThread(() -> {
                                        Toast.makeText(PreviewActivity.this,
                                                "Failed to save partial MIDI", Toast.LENGTH_LONG).show();
                                    });
                                }
                            }).start();
                        })
                        .setNegativeButton("Discard", (dialog, which) -> {
                            Toast.makeText(PreviewActivity.this,
                                    "Partial result discarded", Toast.LENGTH_SHORT).show();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                Toast.makeText(PreviewActivity.this,
                        "Conversion cancelled (no notes generated)", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled() {
            onCancelled(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (videoProcessor != null) {
            int keyframeCount = videoProcessor.getKeyframeCount();
            Log.d(TAG, "Video has " + keyframeCount + " keyframes");
            Toast.makeText(this, 
                "Keyframes indexed: " + keyframeCount, 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_COLOR_MAP && resultCode == RESULT_OK) {
            preferences.load(this);
            previewView.setPreferences(preferences);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isPlaying) {
            btnPlayPause.performClick();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (isPlaying) {
            isPlaying = false;
            playbackHandler.removeCallbacks(playbackRunnable);
            if (videoProcessor != null) {
                videoProcessor.stopSequentialDecoding();
            }
        }
        super.onDestroy();
        
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(true);
        }

        if (videoProcessor != null) {
            videoProcessor.release();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END);
        } else {
            preferences.save(this);
            setResult(RESULT_OK);
            super.onBackPressed();
        }
    }
}