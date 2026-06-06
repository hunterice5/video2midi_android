package com.video2midi.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Preferences {
    private static final String PREFS_NAME = "Video2MidiPrefs";
    
    // MIDI настройки
    private int octave = 3;
    private int tempo = 120;
    private float minimalDuration = 0.02f;
    private int sensitivity = 90;
    private boolean notesOverlap = false;
    private boolean ignoreMinimalDuration = false;
    private String language = "en";
    
    // Визуальные настройки
    private int whiteKeyWidth = 25;
    private int xOffsetWhiteKeys = 60;
    private int yOffsetWhiteKeys = 673;
    private int yOffsetBlackKeys = -30;
    private float blackKeyRelativePosition = 0.4f;
    private int keysAngle = 90;
    private int keysPosCount = 89;
    
    // Spark настройки
    private int sparkYPos = -110;
    private boolean useSparks = false;
    
    // Дополнительные настройки
    private boolean useAlternateKeys = false;
    private boolean rollcheck = false;
    private boolean rollcheckPriority = false;
    private boolean usePerColorDelta = false;
    private boolean syncNotesStartPos = false;
    private float syncNotesStartPosTimeDelta = 1000f;
    
    // Цвета для детекции
    private List<ColorMap> keypColors;
    private List<Integer> channelAccordance;
    private List<Integer> channelProgAccordance;
    private List<Float> sparkseSensitivity;
    private List<Float> perColorDelta;
    
    // Позиции клавиш
    private List<KeyPosition> keysPositions;
    
    // Альтернативные цвета
    private List<ColorMap> alternateColors;
    private List<Integer> alternateSensitivity;
    
    public Preferences() {
        initializeDefaults();
    }
    
    private void initializeDefaults() {
        // Инициализация цветов по умолчанию
        keypColors = new ArrayList<>();
        keypColors.add(new ColorMap(166, 250, 103)); // L.GREEN
        keypColors.add(new ColorMap(58, 146, 0));    // D.GREEN
        keypColors.add(new ColorMap(102, 185, 207)); // L.BLUE
        keypColors.add(new ColorMap(8, 113, 174));   // D.BLUE
        keypColors.add(new ColorMap(255, 255, 85));  // L.YELLOW
        keypColors.add(new ColorMap(254, 210, 0));   // D.YELLOW
        keypColors.add(new ColorMap(255, 212, 85));  // L.ORANGE
        keypColors.add(new ColorMap(255, 138, 0));   // D.ORANGE
        keypColors.add(new ColorMap(253, 125, 114)); // L.RED
        keypColors.add(new ColorMap(255, 37, 9));    // D.RED
        
        // Добавляем пустые слоты
        for (int i = 0; i < 14; i++) {
            keypColors.add(new ColorMap(0, 0, 0));
        }
        
        // Инициализация каналов
        channelAccordance = new ArrayList<>();
        channelProgAccordance = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            channelAccordance.add(i / 2);
            channelProgAccordance.add(0);
        }
        
        // Инициализация чувствительности sparks
        sparkseSensitivity = new ArrayList<>();
        perColorDelta = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            sparkseSensitivity.add(50f);
            perColorDelta.add(90f);
        }
        
        // Инициализация позиций клавиш
        keysPositions = new ArrayList<>();
        alternateColors = new ArrayList<>();
        alternateSensitivity = new ArrayList<>();
        
        for (int i = 0; i < 144; i++) {
            keysPositions.add(new KeyPosition(0, 0));
            alternateColors.add(new ColorMap(0, 0, 0));
            alternateSensitivity.add(0);
        }
    }
    
    public void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        
        // Сохранение простых типов
        editor.putInt("octave", octave);
        editor.putInt("tempo", tempo);
        editor.putFloat("minimalDuration", minimalDuration);
        editor.putInt("sensitivity", sensitivity);
        editor.putBoolean("notesOverlap", notesOverlap);
        editor.putBoolean("ignoreMinimalDuration", ignoreMinimalDuration);
        editor.putString("language", language);
        
        editor.putInt("whiteKeyWidth", whiteKeyWidth);
        editor.putInt("xOffsetWhiteKeys", xOffsetWhiteKeys);
        editor.putInt("yOffsetWhiteKeys", yOffsetWhiteKeys);
        editor.putInt("yOffsetBlackKeys", yOffsetBlackKeys);
        editor.putFloat("blackKeyRelativePosition", blackKeyRelativePosition);
        editor.putInt("keysAngle", keysAngle);
        editor.putInt("keysPosCount", keysPosCount);
        
        editor.putInt("sparkYPos", sparkYPos);
        editor.putBoolean("useSparks", useSparks);
        editor.putBoolean("useAlternateKeys", useAlternateKeys);
        editor.putBoolean("rollcheck", rollcheck);
        editor.putBoolean("rollcheckPriority", rollcheckPriority);
        editor.putBoolean("usePerColorDelta", usePerColorDelta);
        
        // Сохранение списков как JSON
        editor.putString("keypColors", gson.toJson(keypColors));
        editor.putString("channelAccordance", gson.toJson(channelAccordance));
        editor.putString("channelProgAccordance", gson.toJson(channelProgAccordance));
        editor.putString("sparkseSensitivity", gson.toJson(sparkseSensitivity));
        editor.putString("perColorDelta", gson.toJson(perColorDelta));
        editor.putString("keysPositions", gson.toJson(keysPositions));
        editor.putString("alternateColors", gson.toJson(alternateColors));
        editor.putString("alternateSensitivity", gson.toJson(alternateSensitivity));
        
        editor.apply();
    }
    
    public void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        
        // Загрузка простых типов
        octave = prefs.getInt("octave", 3);
        tempo = prefs.getInt("tempo", 120);
        minimalDuration = prefs.getFloat("minimalDuration", 0.02f);
        sensitivity = prefs.getInt("sensitivity", 90);
        notesOverlap = prefs.getBoolean("notesOverlap", false);
        ignoreMinimalDuration = prefs.getBoolean("ignoreMinimalDuration", false);
        language = prefs.getString("language", "en");
        
        whiteKeyWidth = prefs.getInt("whiteKeyWidth", 25);
        xOffsetWhiteKeys = prefs.getInt("xOffsetWhiteKeys", 60);
        yOffsetWhiteKeys = prefs.getInt("yOffsetWhiteKeys", 673);
        yOffsetBlackKeys = prefs.getInt("yOffsetBlackKeys", -30);
        blackKeyRelativePosition = prefs.getFloat("blackKeyRelativePosition", 0.4f);
        keysAngle = prefs.getInt("keysAngle", 90);
        keysPosCount = prefs.getInt("keysPosCount", 89);
        
        sparkYPos = prefs.getInt("sparkYPos", -110);
        useSparks = prefs.getBoolean("useSparks", false);
        useAlternateKeys = prefs.getBoolean("useAlternateKeys", false);
        rollcheck = prefs.getBoolean("rollcheck", false);
        rollcheckPriority = prefs.getBoolean("rollcheckPriority", false);
        usePerColorDelta = prefs.getBoolean("usePerColorDelta", false);
        
        // Загрузка списков из JSON
        Type colorMapListType = new TypeToken<ArrayList<ColorMap>>(){}.getType();
        Type intListType = new TypeToken<ArrayList<Integer>>(){}.getType();
        Type floatListType = new TypeToken<ArrayList<Float>>(){}.getType();
        Type keyPosListType = new TypeToken<ArrayList<KeyPosition>>(){}.getType();
        
        String keypColorsJson = prefs.getString("keypColors", null);
        if (keypColorsJson != null) {
            keypColors = gson.fromJson(keypColorsJson, colorMapListType);
        }
        
        String channelAccordanceJson = prefs.getString("channelAccordance", null);
        if (channelAccordanceJson != null) {
            channelAccordance = gson.fromJson(channelAccordanceJson, intListType);
        }
        
        String keysPositionsJson = prefs.getString("keysPositions", null);
        if (keysPositionsJson != null) {
            keysPositions = gson.fromJson(keysPositionsJson, keyPosListType);
        }
        
        // Загрузка остальных списков...
    }
    
    // Getters и Setters
    public int getOctave() { return octave; }
    public void setOctave(int octave) { this.octave = octave; }
    
    public int getTempo() { return tempo; }
    public void setTempo(int tempo) { this.tempo = tempo; }
    
    public float getMinimalDuration() { return minimalDuration; }
    public void setMinimalDuration(float minimalDuration) { 
        this.minimalDuration = minimalDuration; 
    }
    public boolean isPrefetchEnabled() {
        // Возвращает настройку предзагрузки кадров
        return true; // или читайте из SharedPreferences
    }
    public int getSensitivity() { return sensitivity; }
    public void setSensitivity(int sensitivity) { this.sensitivity = sensitivity; }
    
    public boolean isNotesOverlap() { return notesOverlap; }
    public void setNotesOverlap(boolean notesOverlap) { 
        this.notesOverlap = notesOverlap; 
    }
    
    public boolean isIgnoreMinimalDuration() { return ignoreMinimalDuration; }
    public void setIgnoreMinimalDuration(boolean ignore) { 
        this.ignoreMinimalDuration = ignore; 
    }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public int getWhiteKeyWidth() { return whiteKeyWidth; }
    public void setWhiteKeyWidth(int width) { this.whiteKeyWidth = width; }
    
    public int getXOffsetWhiteKeys() { return xOffsetWhiteKeys; }
    public void setXOffsetWhiteKeys(int offset) { this.xOffsetWhiteKeys = offset; }
    
    public int getYOffsetWhiteKeys() { return yOffsetWhiteKeys; }
    public void setYOffsetWhiteKeys(int offset) { this.yOffsetWhiteKeys = offset; }
    
    public int getYOffsetBlackKeys() { return yOffsetBlackKeys; }
    public void setYOffsetBlackKeys(int offset) { this.yOffsetBlackKeys = offset; }
    
    public float getBlackKeyRelativePosition() { return blackKeyRelativePosition; }
    public void setBlackKeyRelativePosition(float pos) { 
        this.blackKeyRelativePosition = pos; 
    }
    
    public int getKeysAngle() { return keysAngle; }
    public void setKeysAngle(int angle) { this.keysAngle = angle; }
    
    public int getKeysPosCount() { return keysPosCount; }
    public void setKeysPosCount(int count) { this.keysPosCount = count; }
    
    public int getSparkYPos() { return sparkYPos; }
    public void setSparkYPos(int pos) { this.sparkYPos = pos; }
    
    public boolean isUseSparks() { return useSparks; }
    public void setUseSparks(boolean use) { this.useSparks = use; }
    
    public boolean isUseAlternateKeys() { return useAlternateKeys; }
    public void setUseAlternateKeys(boolean use) { this.useAlternateKeys = use; }
    
    public boolean isRollcheck() { return rollcheck; }
    public void setRollcheck(boolean rollcheck) { this.rollcheck = rollcheck; }
    
    public boolean isRollcheckPriority() { return rollcheckPriority; }
    public void setRollcheckPriority(boolean priority) { 
        this.rollcheckPriority = priority; 
    }
    
    public boolean isUsePerColorDelta() { return usePerColorDelta; }
    public void setUsePerColorDelta(boolean use) { this.usePerColorDelta = use; }
    
    public List<ColorMap> getKeypColors() { return keypColors; }
    public void setKeypColors(List<ColorMap> colors) { this.keypColors = colors; }
    
    public List<Integer> getChannelAccordance() { return channelAccordance; }
    public void setChannelAccordance(List<Integer> channels) { 
        this.channelAccordance = channels; 
    }
    
    public List<Integer> getChannelProgAccordance() { 
        return channelProgAccordance; 
    }
    public void setChannelProgAccordance(List<Integer> progs) { 
        this.channelProgAccordance = progs; 
    }
    
    public List<Float> getSparkseSensitivity() { return sparkseSensitivity; }
    public void setSparkseSensitivity(List<Float> sensitivity) { 
        this.sparkseSensitivity = sensitivity; 
    }
    
    public List<Float> getPerColorDelta() { return perColorDelta; }
    public void setPerColorDelta(List<Float> delta) { this.perColorDelta = delta; }
    
    public List<KeyPosition> getKeysPositions() { return keysPositions; }
    public void setKeysPositions(List<KeyPosition> positions) { 
        this.keysPositions = positions; 
    }
    
    public List<ColorMap> getAlternateColors() { return alternateColors; }
    public void setAlternateColors(List<ColorMap> colors) { 
        this.alternateColors = colors; 
    }
    
    public List<Integer> getAlternateSensitivity() { return alternateSensitivity; }
    public void setAlternateSensitivity(List<Integer> sensitivity) { 
        this.alternateSensitivity = sensitivity; 
    }
    
    public ColorMap getAlternateColor(int index) {
        if (index >= 0 && index < alternateColors.size()) {
            return alternateColors.get(index);
        }
        return new ColorMap(0, 0, 0);
    }
    
    public boolean isSyncNotesStartPos() { return syncNotesStartPos; }
    public void setSyncNotesStartPos(boolean sync) { this.syncNotesStartPos = sync; }
    
    public float getSyncNotesStartPosTimeDelta() { 
        return syncNotesStartPosTimeDelta; 
    }
    public void setSyncNotesStartPosTimeDelta(float delta) { 
        this.syncNotesStartPosTimeDelta = delta; 
    }
}
