package com.video2midi;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.video2midi.core.KeyPositionCalculator;
import com.video2midi.model.Preferences;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    
    private Preferences preferences;
    
    // UI элементы
    private SeekBar seekOctave;
    private TextView tvOctave;
    
    private SeekBar seekTempo;
    private TextView tvTempo;
    
    private SeekBar seekSensitivity;
    private TextView tvSensitivity;
    
    private SeekBar seekMinDuration;
    private TextView tvMinDuration;
    
    private SeekBar seekWhiteKeyWidth;
    private TextView tvWhiteKeyWidth;
    
    private SeekBar seekBlackKeyPos;
    private TextView tvBlackKeyPos;
    
    private SeekBar seekKeysCount;
    private TextView tvKeysCount;
    
    private CheckBox cbNotesOverlap;
    private CheckBox cbIgnoreMinDuration;
    private CheckBox cbUseSparks;
    private CheckBox cbUseAlternateKeys;
    private CheckBox cbRollcheck;
    private CheckBox cbRollcheckPriority;
    private CheckBox cbUsePerColorDelta;
    private CheckBox cbSyncNotesStart;
    
    private Button btnSave;
    private Button btnReset;
    private Button btnCancel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        preferences = new Preferences();
        preferences.load(this);
        
        initViews();
        loadSettings();
        setupListeners();
    }
    
    private void initViews() {
        // Octave
        seekOctave = findViewById(R.id.seekOctave);
        tvOctave = findViewById(R.id.tvOctave);
        
        // Tempo
        seekTempo = findViewById(R.id.seekTempo);
        tvTempo = findViewById(R.id.tvTempo);
        
        // Sensitivity
        seekSensitivity = findViewById(R.id.seekSensitivity);
        tvSensitivity = findViewById(R.id.tvSensitivity);
        
        // Min Duration
        seekMinDuration = findViewById(R.id.seekMinDuration);
        tvMinDuration = findViewById(R.id.tvMinDuration);
        
        // White Key Width
        seekWhiteKeyWidth = findViewById(R.id.seekWhiteKeyWidth);
        tvWhiteKeyWidth = findViewById(R.id.tvWhiteKeyWidth);
        
        // Black Key Position
        seekBlackKeyPos = findViewById(R.id.seekBlackKeyPos);
        tvBlackKeyPos = findViewById(R.id.tvBlackKeyPos);
        
        // Keys Count
        seekKeysCount = findViewById(R.id.seekKeysCount);
        tvKeysCount = findViewById(R.id.tvKeysCount);
        
        // Checkboxes
        cbNotesOverlap = findViewById(R.id.cbNotesOverlap);
        cbIgnoreMinDuration = findViewById(R.id.cbIgnoreMinDuration);
        cbUseSparks = findViewById(R.id.cbUseSparks);
        cbUseAlternateKeys = findViewById(R.id.cbUseAlternateKeys);
        cbRollcheck = findViewById(R.id.cbRollcheck);
        cbRollcheckPriority = findViewById(R.id.cbRollcheckPriority);
        cbUsePerColorDelta = findViewById(R.id.cbUsePerColorDelta);
        cbSyncNotesStart = findViewById(R.id.cbSyncNotesStart);
        
        // Buttons
        btnSave = findViewById(R.id.btnSave);
        btnReset = findViewById(R.id.btnReset);
        btnCancel = findViewById(R.id.btnCancel);
    }
    
    private void loadSettings() {
        // Octave (0-7)
        seekOctave.setMax(7);
        seekOctave.setProgress(preferences.getOctave());
        tvOctave.setText("Octave: " + preferences.getOctave());
        
        // Tempo (30-240)
        seekTempo.setMax(210);
        seekTempo.setProgress(preferences.getTempo() - 30);
        tvTempo.setText("Tempo: " + preferences.getTempo());
        
        // Sensitivity (0-130)
        seekSensitivity.setMax(130);
        seekSensitivity.setProgress(preferences.getSensitivity());
        tvSensitivity.setText("Sensitivity: " + preferences.getSensitivity());
        
        // Min Duration (0-2.0 sec)
        seekMinDuration.setMax(200);
        seekMinDuration.setProgress((int)(preferences.getMinimalDuration() * 100));
        tvMinDuration.setText(String.format("Min Duration: %.2f sec", 
            preferences.getMinimalDuration()));
        
        // White Key Width (10-40)
        seekWhiteKeyWidth.setMax(400);
        seekWhiteKeyWidth.setProgress(preferences.getWhiteKeyWidth() - 10);
        tvWhiteKeyWidth.setText("White Key Width: " + preferences.getWhiteKeyWidth());
        
        // Black Key Position (0-1.0)
        seekBlackKeyPos.setMax(100);
        seekBlackKeyPos.setProgress((int)(preferences.getBlackKeyRelativePosition() * 100));
        tvBlackKeyPos.setText(String.format("Black Key Pos: %.2f", 
            preferences.getBlackKeyRelativePosition()));
        
        // Keys Count (12-144)
        seekKeysCount.setMax(132);
        seekKeysCount.setProgress(preferences.getKeysPosCount() - 12);
        tvKeysCount.setText("Keys Count: " + preferences.getKeysPosCount());
        
        // Checkboxes
        cbNotesOverlap.setChecked(preferences.isNotesOverlap());
        cbIgnoreMinDuration.setChecked(preferences.isIgnoreMinimalDuration());
        cbUseSparks.setChecked(preferences.isUseSparks());
        cbUseAlternateKeys.setChecked(preferences.isUseAlternateKeys());
        cbRollcheck.setChecked(preferences.isRollcheck());
        cbRollcheckPriority.setChecked(preferences.isRollcheckPriority());
        cbUsePerColorDelta.setChecked(preferences.isUsePerColorDelta());
        cbSyncNotesStart.setChecked(preferences.isSyncNotesStartPos());
    }
    
    private void setupListeners() {
        // Octave
        seekOctave.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvOctave.setText("Octave: " + progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Tempo
        seekTempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTempo.setText("Tempo: " + (progress + 30));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Sensitivity
        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSensitivity.setText("Sensitivity: " + progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Min Duration
        seekMinDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                tvMinDuration.setText(String.format("Min Duration: %.2f sec", value));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // White Key Width
        seekWhiteKeyWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvWhiteKeyWidth.setText("White Key Width: " + (progress + 10));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Black Key Position
        seekBlackKeyPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                tvBlackKeyPos.setText(String.format("Black Key Pos: %.2f", value));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Keys Count
        seekKeysCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvKeysCount.setText("Keys Count: " + (progress + 12));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Buttons
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetSettings();
            }
        });
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    
    private void saveSettings() {
        // Сохраняем все настройки
        preferences.setOctave(seekOctave.getProgress());
        preferences.setTempo(seekTempo.getProgress() + 30);
        preferences.setSensitivity(seekSensitivity.getProgress());
        preferences.setMinimalDuration(seekMinDuration.getProgress() / 100.0f);
        preferences.setWhiteKeyWidth(seekWhiteKeyWidth.getProgress() + 10);
        preferences.setBlackKeyRelativePosition(seekBlackKeyPos.getProgress() / 100.0f);
        preferences.setKeysPosCount(seekKeysCount.getProgress() + 12);
        
        preferences.setNotesOverlap(cbNotesOverlap.isChecked());
        preferences.setIgnoreMinimalDuration(cbIgnoreMinDuration.isChecked());
        preferences.setUseSparks(cbUseSparks.isChecked());
        preferences.setUseAlternateKeys(cbUseAlternateKeys.isChecked());
        preferences.setRollcheck(cbRollcheck.isChecked());
        preferences.setRollcheckPriority(cbRollcheckPriority.isChecked());
        preferences.setUsePerColorDelta(cbUsePerColorDelta.isChecked());
        preferences.setSyncNotesStartPos(cbSyncNotesStart.isChecked());
        
        // Обновляем позиции клавиш
        KeyPositionCalculator.updateKeyPositions(preferences);
        
        // Сохраняем в SharedPreferences
        preferences.save(this);
        
        setResult(RESULT_OK);
        finish();
    }
    
    private void resetSettings() {
        preferences = new Preferences(); // Создаем с настройками по умолчанию
        loadSettings();
    }
}
