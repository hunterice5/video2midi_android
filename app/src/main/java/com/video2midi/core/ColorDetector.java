package com.video2midi.core;

import com.video2midi.model.ColorMap;
import com.video2midi.model.Preferences;
import android.util.Log;

public class ColorDetector {
    private static final String TAG = "ColorDetector";
    
    private Preferences prefs;
    
    public ColorDetector(Preferences prefs) {
        this.prefs = prefs;
    }
    
    public KeyPressResult detectKeyPress(int[] pixelColor, int[] sparkColor, int keyIndex) {
        KeyPressResult result = new KeyPressResult();
        result.isPressed = false;
        result.channel = 0;
        result.colorIndex = -1;
        
        if (prefs.isUseAlternateKeys()) {
            return detectWithAlternateColors(pixelColor, keyIndex);
        } else {
            return detectWithColorMap(pixelColor, sparkColor, keyIndex);
        }
    }
    
    private KeyPressResult detectWithAlternateColors(int[] pixelColor, int keyIndex) {
        KeyPressResult result = new KeyPressResult();
        
        ColorMap alternateColor = prefs.getAlternateColor(keyIndex);
        int alternateSensitivity = prefs.getAlternateSensitivity().get(keyIndex);
        int delta = prefs.getSensitivity() + alternateSensitivity;
        
        int diff = colorDifference(pixelColor, alternateColor.toArray());
        
        // Для альтернативных цветов проверяем БОЛЬШЕ порога (нажата если отличается)
        result.isPressed = diff > delta;
        result.channel = 0;
        result.colorIndex = -1;
        
        return result;
    }
    
    private KeyPressResult detectWithColorMap(int[] pixelColor, int[] sparkColor, int keyIndex) {
        KeyPressResult result = new KeyPressResult();
        
        int minDelta = Integer.MAX_VALUE;
        int selectedColorIndex = -1;
        
        for (int i = 0; i < prefs.getKeypColors().size(); i++) {
            ColorMap refColor = prefs.getKeypColors().get(i);
            
            // Пропускаем пустые цвета
            if (refColor.isEmpty()) {
                continue;
            }
            
            int delta = prefs.getSensitivity();
            
            // Используем индивидуальную чувствительность если включено
            if (prefs.isUsePerColorDelta() && i < prefs.getPerColorDelta().size()) {
                delta = prefs.getPerColorDelta().get(i).intValue();
            }
            
            int diff = colorDifference(pixelColor, refColor.toArray());
            
            if (diff < delta) {
                // Цвет совпадает
                if (diff < minDelta) {
                    minDelta = diff;
                    selectedColorIndex = i;
                }
            }
        }
        
        if (selectedColorIndex != -1) {
            result.isPressed = true;
            result.colorIndex = selectedColorIndex;
            result.channel = prefs.getChannelAccordance().get(selectedColorIndex);
            
            // Проверка sparks если включено
            if (prefs.isUseSparks() && sparkColor != null) {
                ColorMap refColor = prefs.getKeypColors().get(selectedColorIndex);
                float sparkSensitivity = prefs.getSparkseSensitivity().get(selectedColorIndex);
                
                boolean hasSparkDelta = 
                    (sparkColor[0] - refColor.getR()) > sparkSensitivity ||
                    (sparkColor[1] - refColor.getG()) > sparkSensitivity ||
                    (sparkColor[2] - refColor.getB()) > sparkSensitivity;
                
                if (!hasSparkDelta) {
                    // Spark не обнаружен - клавиша не нажата
                    result.isPressedBySpark = true;
                    result.isPressed = false;
                }
            }
        }
        
        return result;
    }
    
    private int colorDifference(int[] c1, int[] c2) {
        return Math.abs(c1[0] - c2[0]) + 
               Math.abs(c1[1] - c2[1]) + 
               Math.abs(c1[2] - c2[2]);
    }
    
    public static class KeyPressResult {
        public boolean isPressed;
        public boolean isPressedBySpark;
        public int channel;
        public int colorIndex;
    }
}
