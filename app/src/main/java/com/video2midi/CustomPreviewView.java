package com.video2midi.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.video2midi.core.ColorDetector;
import com.video2midi.model.KeyPosition;
import com.video2midi.model.Preferences;

import java.util.List;
import android.util.Log;

public class CustomPreviewView extends View {
    private static final String TAG = "CustomPreviewView";
    
    private Bitmap displayBitmap;
    private Paint paint;
    private Paint keyPaint;
    private Paint selectedKeyPaint;
    private Paint pressedKeyPaint;
    private Paint textPaint;
    
    private Preferences preferences;
    private ColorDetector colorDetector;
    
    private float translateX = 0;
    private float translateY = 0;
    private float scaleFactor = 1.0f;
    
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    
    private int selectedKeyIndex = -1;
    private boolean isDraggingKey = false;
    private boolean isDraggingAllKeys = false;
    private boolean isDraggingView = false;
    private float lastTouchX;
    private float lastTouchY;
    private float lastRawX;
    private float lastRawY;
    
    private OnKeyClickListener onKeyClickListener;
    private OnColorPickListener onColorPickListener;
    private OnKeyboardMovedListener onKeyboardMovedListener;
    
    private boolean showKeyboard = true;
    private boolean showSparks = false;
    private boolean moveKeyboardMode = false;
    private boolean showKeyPresses = true;

    private Bitmap previousBitmap;
    
    // Кэш для нажатых клавиш
    private boolean[] keyPressed;
    private int[][] keyPressedColor;
    
    public interface OnKeyClickListener {
        void onKeyClick(int keyIndex);
    }
    
    public interface OnColorPickListener {
        void onColorPick(int x, int y, int[] color);
    }
    
    public interface OnKeyboardMovedListener {
        void onKeyboardMoved(int dx, int dy);
    }
    
    public CustomPreviewView(Context context) {
        super(context);
        init();
    }
    
    public CustomPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        
        keyPaint = new Paint();
        keyPaint.setAntiAlias(true);
        keyPaint.setStyle(Paint.Style.STROKE);
        keyPaint.setStrokeWidth(3);
        
        selectedKeyPaint = new Paint();
        selectedKeyPaint.setAntiAlias(true);
        selectedKeyPaint.setStyle(Paint.Style.FILL);
        selectedKeyPaint.setColor(Color.argb(100, 255, 0, 0));
        
        pressedKeyPaint = new Paint();
        pressedKeyPaint.setAntiAlias(true);
        pressedKeyPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
        
        keyPressed = new boolean[144];
        keyPressedColor = new int[144][3];
    }
    
    public void setPreferences(Preferences prefs) {
        this.preferences = prefs;
        if (prefs != null) {
            this.colorDetector = new ColorDetector(prefs);
        }
        invalidate();
    }

    public void setDisplayBitmap(Bitmap bitmap) {
        Log.d(TAG, "=== setDisplayBitmap called ===");
        Log.d(TAG, "New bitmap: " + (bitmap != null ? "valid, hash=" + bitmap.hashCode() : "null"));
        Log.d(TAG, "Old bitmap: " + (displayBitmap != null ? "valid, hash=" + displayBitmap.hashCode() : "null"));

        if (bitmap != null && !bitmap.isRecycled()) {
            // Сохраняем старый bitmap для последующего освобождения
            previousBitmap = displayBitmap;

            // Устанавливаем новый
            this.displayBitmap = bitmap;

            Log.d(TAG, "displayBitmap updated to hash: " + this.displayBitmap.hashCode());

            // Обновляем состояния нажатых клавиш ВСЕГДА
            if (showKeyPresses && preferences != null && colorDetector != null) {
                Log.d(TAG, "Detecting key presses...");
                detectKeyPresses();
            }

            // Перерисовываем
            Log.d(TAG, "Calling invalidate()");
            invalidate();

            // Освобождаем старый bitmap ПОСЛЕ перерисовки
            post(() -> {
                if (previousBitmap != null && !previousBitmap.isRecycled() && previousBitmap != displayBitmap) {
                    int hash = previousBitmap.hashCode();
                    previousBitmap.recycle();
                    previousBitmap = null;
                    Log.v(TAG, "Previous bitmap recycled, hash was: " + hash);
                }
            });

            Log.d(TAG, "Display bitmap updated: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        } else {
            Log.e(TAG, "Attempt to set null or recycled bitmap");
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.v(TAG, "=== onDraw called ===");
        Log.v(TAG, "displayBitmap: " + (displayBitmap != null && !displayBitmap.isRecycled() ?
                "valid, hash=" + displayBitmap.hashCode() : "null or recycled"));

        super.onDraw(canvas);

        canvas.save();

        // Применяем трансформации
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor);

        // Рисуем видео кадр
        if (displayBitmap != null && !displayBitmap.isRecycled()) {
            Log.v(TAG, "Drawing bitmap: " + displayBitmap.hashCode());
            canvas.drawBitmap(displayBitmap, 0, 0, paint);
        } else {
            Log.w(TAG, "Skipping bitmap draw - bitmap is null or recycled");
        }

        // Рисуем виртуальную клавиатуру
        if (showKeyboard && preferences != null) {
            drawKeyboard(canvas);
        }

        canvas.restore();

        // Рисуем информацию (без трансформации)
        if (moveKeyboardMode) {
            drawMoveKeyboardInfo(canvas);
        } else if (selectedKeyIndex >= 0) {
            drawKeyInfo(canvas);
        }

        // Статистика нажатых клавиш
        if (showKeyPresses && showKeyboard) {
            drawPressedKeysStats(canvas);
        }

        Log.v(TAG, "=== onDraw finished ===");
    }

    public void forceUpdateKeyPresses() {
        Log.d(TAG, "forceUpdateKeyPresses called");

        if (showKeyPresses && displayBitmap != null && preferences != null && colorDetector != null) {
            Log.d(TAG, "Updating key presses and invalidating");
            detectKeyPresses();
            invalidate();
        } else {
            Log.d(TAG, "Skipping update: showKeyPresses=" + showKeyPresses +
                    ", displayBitmap=" + (displayBitmap != null) +
                    ", preferences=" + (preferences != null) +
                    ", colorDetector=" + (colorDetector != null));
        }
    }


    public void setShowKeyPresses(boolean show) {
        this.showKeyPresses = show;
        if (show && displayBitmap != null && preferences != null) {
            detectKeyPresses();
        }
        invalidate();
    }
    
    public boolean isShowKeyPresses() {
        return showKeyPresses;
    }
    
    private void detectKeyPresses() {
        if (displayBitmap == null || preferences == null || colorDetector == null) {
            return;
        }
        
        List<KeyPosition> keys = preferences.getKeysPositions();
        int xOffset = preferences.getXOffsetWhiteKeys();
        int yOffset = preferences.getYOffsetWhiteKeys();
        
        for (int i = 0; i < keys.size(); i++) {
            KeyPosition keyPos = keys.get(i);
            
            int x = keyPos.getX() + xOffset;
            int y = keyPos.getY() + yOffset;
            
            // Проверка границ
            if (x < 0 || x >= displayBitmap.getWidth() || 
                y < 0 || y >= displayBitmap.getHeight()) {
                keyPressed[i] = false;
                continue;
            }
            
            // Получаем цвет пикселя
            int[] pixelColor = getPixelColorFromBitmap(x, y);
            
            // Получаем цвет spark если включено
            int[] sparkColor = null;
            if (preferences.isUseSparks()) {
                int sparkX = x;
                int sparkY = preferences.getSparkYPos();
                sparkColor = getAveragePixelColorFromBitmap(sparkX, sparkY, 1);
            }
            
            // Проверяем нажатие через ColorDetector
            ColorDetector.KeyPressResult result = 
                colorDetector.detectKeyPress(pixelColor, sparkColor, i);
            
            keyPressed[i] = result.isPressed;
            
            if (result.isPressed && result.colorIndex >= 0 && 
                result.colorIndex < preferences.getKeypColors().size()) {
                // Сохраняем цвет нажатой клавиши
                keyPressedColor[i][0] = preferences.getKeypColors().get(result.colorIndex).getR();
                keyPressedColor[i][1] = preferences.getKeypColors().get(result.colorIndex).getG();
                keyPressedColor[i][2] = preferences.getKeypColors().get(result.colorIndex).getB();
            } else {
                keyPressedColor[i][0] = 0;
                keyPressedColor[i][1] = 0;
                keyPressedColor[i][2] = 0;
            }
        }
    }
    
    private int[] getPixelColorFromBitmap(int x, int y) {
        if (displayBitmap == null || displayBitmap.isRecycled()) {
            return new int[]{0, 0, 0};
        }
        
        if (x < 0 || x >= displayBitmap.getWidth() || 
            y < 0 || y >= displayBitmap.getHeight()) {
            return new int[]{0, 0, 0};
        }
        
        try {
            int pixel = displayBitmap.getPixel(x, y);
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            return new int[]{r, g, b};
        } catch (Exception e) {
            return new int[]{0, 0, 0};
        }
    }
    
    private int[] getAveragePixelColorFromBitmap(int x, int y, int height) {
        if (displayBitmap == null || displayBitmap.isRecycled()) {
            return new int[]{0, 0, 0};
        }
        
        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;
        
        for (int yOffset = 0; yOffset < height; yOffset++) {
            int currentY = y - yOffset;
            
            if (currentY < 0 || currentY >= displayBitmap.getHeight() || 
                x < 0 || x >= displayBitmap.getWidth()) {
                continue;
            }
            
            try {
                int pixel = displayBitmap.getPixel(x, currentY);
                sumR += (pixel >> 16) & 0xFF;
                sumG += (pixel >> 8) & 0xFF;
                sumB += pixel & 0xFF;
                count++;
            } catch (Exception e) {
                // Игнорируем
            }
        }
        
        if (count == 0) {
            return new int[]{0, 0, 0};
        }
        
        return new int[]{
            (int) (sumR / count),
            (int) (sumG / count),
            (int) (sumB / count)
        };
    }
    
    public void setOnKeyClickListener(OnKeyClickListener listener) {
        this.onKeyClickListener = listener;
    }
    
    public void setOnColorPickListener(OnColorPickListener listener) {
        this.onColorPickListener = listener;
    }
    
    public void setOnKeyboardMovedListener(OnKeyboardMovedListener listener) {
        this.onKeyboardMovedListener = listener;
    }
    
    public void setShowKeyboard(boolean show) {
        this.showKeyboard = show;
        invalidate();
    }
    
    public void setShowSparks(boolean show) {
        this.showSparks = show;
        invalidate();
    }
    
    public void setMoveKeyboardMode(boolean mode) {
        this.moveKeyboardMode = mode;
        if (mode) {
            selectedKeyIndex = -1;
        }
        invalidate();
    }
    
    public boolean isMoveKeyboardMode() {
        return moveKeyboardMode;
    }
    
    public int getSelectedKeyIndex() {
        return selectedKeyIndex;
    }
    
    public void setSelectedKeyIndex(int index) {
        this.selectedKeyIndex = index;
        invalidate();
    }
    
    private void drawKeyboard(Canvas canvas) {
        List<KeyPosition> keys = preferences.getKeysPositions();
        int xOffset = preferences.getXOffsetWhiteKeys();
        int yOffset = preferences.getYOffsetWhiteKeys();
        
        // Если режим перемещения клавиатуры - рисуем границу
        if (moveKeyboardMode) {
            drawKeyboardBounds(canvas, keys, xOffset, yOffset);
        }
        
        // Сначала рисуем белые клавиши
        for (int i = 0; i < keys.size(); i++) {
            if (isBlackKey(i)) continue;
            drawKey(canvas, i, keys.get(i), xOffset, yOffset, false);
        }
        
        // Потом черные клавиши (чтобы они были сверху)
        for (int i = 0; i < keys.size(); i++) {
            if (!isBlackKey(i)) continue;
            drawKey(canvas, i, keys.get(i), xOffset, yOffset, true);
        }
        
        // Рисуем sparks если включено
        if (showSparks) {
            drawSparks(canvas, keys, xOffset);
        }
    }
    
    private void drawKeyboardBounds(Canvas canvas, List<KeyPosition> keys, int xOffset, int yOffset) {
        if (keys.isEmpty()) return;
        
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        for (KeyPosition keyPos : keys) {
            int x = keyPos.getX() + xOffset;
            int y = keyPos.getY() + yOffset;
            
            minX = Math.min(minX, x - 50);
            maxX = Math.max(maxX, x + 50);
            minY = Math.min(minY, y - 100);
            maxY = Math.max(maxY, y + 100);
        }
        
        Paint boundsPaint = new Paint();
        boundsPaint.setAntiAlias(true);
        boundsPaint.setStyle(Paint.Style.STROKE);
        boundsPaint.setColor(Color.argb(150, 0, 255, 0));
        boundsPaint.setStrokeWidth(4);
        boundsPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));
        
        canvas.drawRect(minX, minY, maxX, maxY, boundsPaint);
        
        Paint cornerPaint = new Paint();
        cornerPaint.setAntiAlias(true);
        cornerPaint.setStyle(Paint.Style.FILL);
        cornerPaint.setColor(Color.argb(200, 0, 255, 0));
        
        float cornerSize = 20;
        canvas.drawCircle(minX, minY, cornerSize, cornerPaint);
        canvas.drawCircle(maxX, minY, cornerSize, cornerPaint);
        canvas.drawCircle(minX, maxY, cornerSize, cornerPaint);
        canvas.drawCircle(maxX, maxY, cornerSize, cornerPaint);
    }
    
    private void drawKey(Canvas canvas, int index, KeyPosition keyPos, 
                        int xOffset, int yOffset, boolean isBlack) {
        int x = keyPos.getX() + xOffset;
        int y = keyPos.getY() + yOffset;
        
        if (displayBitmap != null) {
            if (x < 0 || x >= displayBitmap.getWidth() || 
                y < 0 || y >= displayBitmap.getHeight()) {
                return;
            }
        }
        
        // Размеры клавиши
        float keyWidth = isBlack ? 20 : 30;
        float keyHeight = isBlack ? 50 : 80;
        
        // Если клавиша нажата - рисуем заливку цветом из colormap
        if (showKeyPresses && keyPressed[index]) {
            pressedKeyPaint.setColor(Color.argb(180, 
                keyPressedColor[index][0], 
                keyPressedColor[index][1], 
                keyPressedColor[index][2]));
            canvas.drawRoundRect(
                x - keyWidth / 2, y - keyHeight / 2,
                x + keyWidth / 2, y + keyHeight / 2,
                5, 5, pressedKeyPaint);
            
            // Дополнительное свечение для нажатых клавиш
            Paint glowPaint = new Paint();
            glowPaint.setAntiAlias(true);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(4);
            glowPaint.setColor(Color.argb(220, 
                keyPressedColor[index][0], 
                keyPressedColor[index][1], 
                keyPressedColor[index][2]));
            canvas.drawRoundRect(
                x - keyWidth / 2 - 2, y - keyHeight / 2 - 2,
                x + keyWidth / 2 + 2, y + keyHeight / 2 + 2,
                5, 5, glowPaint);
        }
        
        // Если клавиша выбрана - рисуем заливку
        if (index == selectedKeyIndex && !moveKeyboardMode) {
            canvas.drawRoundRect(
                x - keyWidth / 2, y - keyHeight / 2,
                x + keyWidth / 2, y + keyHeight / 2,
                5, 5, selectedKeyPaint);
        }
        
        // Обводка клавиши
        if (isBlack) {
            keyPaint.setColor(Color.CYAN);
            keyPaint.setStrokeWidth(moveKeyboardMode ? 2 : 3);
        } else {
            keyPaint.setColor(Color.YELLOW);
            keyPaint.setStrokeWidth(moveKeyboardMode ? 3 : 4);
        }
        
        canvas.drawRoundRect(
            x - keyWidth / 2, y - keyHeight / 2,
            x + keyWidth / 2, y + keyHeight / 2,
            5, 5, keyPaint);
        
        // Центральная точка (не рисуем в режиме перемещения)
        if (!moveKeyboardMode) {
            Paint dotPaint = new Paint();
            dotPaint.setAntiAlias(true);
            dotPaint.setStyle(Paint.Style.FILL);
            
            // Если клавиша нажата - рисуем белую точку
            if (showKeyPresses && keyPressed[index]) {
                dotPaint.setColor(Color.WHITE);
            } else {
                dotPaint.setColor(isBlack ? Color.CYAN : Color.YELLOW);
            }
            
            canvas.drawCircle(x, y, isBlack ? 4 : 6, dotPaint);
            
            // Вертикальная линия
            keyPaint.setStrokeWidth(2);
            canvas.drawLine(x, y - 30, x, y + 30, keyPaint);
        }
        
        // Маркер для базовой октавы
        if (index == preferences.getOctave() * 12 && !moveKeyboardMode) {
            Paint octavePaint = new Paint();
            octavePaint.setAntiAlias(true);
            octavePaint.setStyle(Paint.Style.STROKE);
            octavePaint.setColor(Color.RED);
            octavePaint.setStrokeWidth(4);
            canvas.drawCircle(x, y, 15, octavePaint);
        }
        
        // Номер клавиши для выбранной
        if (index == selectedKeyIndex && !moveKeyboardMode) {
            textPaint.setTextSize(20);
            textPaint.setColor(Color.WHITE);
            String keyText = "K" + index;
            float textWidth = textPaint.measureText(keyText);
            canvas.drawText(keyText, x - textWidth / 2, y - keyHeight / 2 - 5, textPaint);
        }
    }
    
    private void drawSparks(Canvas canvas, List<KeyPosition> keys, int xOffset) {
        int sparkY = preferences.getSparkYPos();
        
        Paint sparkPaint = new Paint();
        sparkPaint.setAntiAlias(true);
        sparkPaint.setStyle(Paint.Style.FILL);
        sparkPaint.setColor(Color.MAGENTA);
        
        Paint sparkLinePaint = new Paint();
        sparkLinePaint.setAntiAlias(true);
        sparkLinePaint.setColor(Color.MAGENTA);
        sparkLinePaint.setStrokeWidth(2);
        sparkLinePaint.setAlpha(100);
        
        for (KeyPosition keyPos : keys) {
            int x = keyPos.getX() + xOffset;
            
            if (displayBitmap != null) {
                if (x >= 0 && x < displayBitmap.getWidth() && 
                    sparkY >= 0 && sparkY < displayBitmap.getHeight()) {
                    canvas.drawCircle(x, sparkY, 4, sparkPaint);
                    if (!moveKeyboardMode) {
                        canvas.drawLine(x, sparkY, x, sparkY + 20, sparkLinePaint);
                    }
                }
            }
        }
    }
    
    private void drawKeyInfo(Canvas canvas) {
        if (preferences == null) return;
        
        KeyPosition keyPos = preferences.getKeysPositions().get(selectedKeyIndex);
        
        String info = String.format(
            "Key: %d | Pos: (%d, %d) | Note: %s%d%s",
            selectedKeyIndex,
            keyPos.getX() + preferences.getXOffsetWhiteKeys(),
            keyPos.getY() + preferences.getYOffsetWhiteKeys(),
            getNoteNameFromIndex(selectedKeyIndex % 12),
            (selectedKeyIndex / 12) + preferences.getOctave(),
            keyPressed[selectedKeyIndex] ? " [PRESSED]" : ""
        );
        
        textPaint.setTextSize(24);
        textPaint.setColor(Color.WHITE);
        
        float textWidth = textPaint.measureText(info);
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawRect(10, 10, textWidth + 20, 45, bgPaint);
        
        canvas.drawText(info, 15, 33, textPaint);
    }
    
    private void drawMoveKeyboardInfo(Canvas canvas) {
        String info = "MOVE KEYBOARD MODE - Drag to move entire keyboard";
        
        textPaint.setTextSize(20);
        textPaint.setColor(Color.GREEN);
        
        float textWidth = textPaint.measureText(info);
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawRect(10, 10, textWidth + 20, 40, bgPaint);
        
        canvas.drawText(info, 15, 30, textPaint);
    }
    
    private void drawPressedKeysStats(Canvas canvas) {
        int pressedCount = 0;
        for (int i = 0; i < keyPressed.length; i++) {
            if (keyPressed[i]) pressedCount++;
        }
        
        if (pressedCount == 0) return;
        
        String stats = String.format("Pressed: %d keys", pressedCount);
        
        textPaint.setTextSize(18);
        textPaint.setColor(Color.GREEN);
        
        float textWidth = textPaint.measureText(stats);
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.argb(180, 0, 0, 0));
        
        float y = getHeight() - 40;
        canvas.drawRect(10, y - 25, textWidth + 20, y + 5, bgPaint);
        canvas.drawText(stats, 15, y - 5, textPaint);
    }
    
    private String getNoteNameFromIndex(int index) {
        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return notes[index];
    }
    
    private boolean isBlackKey(int index) {
        int note = index % 12;
        return note == 1 || note == 3 || note == 6 || note == 8 || note == 10;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        
        if (scaleDetector.isInProgress()) {
            return true;
        }
        
        gestureDetector.onTouchEvent(event);
        
        float touchX = (event.getX() - translateX) / scaleFactor;
        float touchY = (event.getY() - translateY) / scaleFactor;
        
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = touchX;
                lastTouchY = touchY;
                lastRawX = event.getX();
                lastRawY = event.getY();
                
                if (moveKeyboardMode) {
                    isDraggingAllKeys = true;
                } else if (preferences != null) {
                    int keyIndex = findKeyAtPosition(touchX, touchY);
                    if (keyIndex >= 0) {
                        selectedKeyIndex = keyIndex;
                        isDraggingKey = true;
                        if (onKeyClickListener != null) {
                            onKeyClickListener.onKeyClick(keyIndex);
                        }
                        invalidate();
                        return true;
                    }
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1) {
                    float dx = touchX - lastTouchX;
                    float dy = touchY - lastTouchY;
                    
                    if (isDraggingKey && selectedKeyIndex >= 0) {
                        KeyPosition keyPos = preferences.getKeysPositions().get(selectedKeyIndex);
                        keyPos.setX(keyPos.getX() + (int) dx);
                        keyPos.setY(keyPos.getY() + (int) dy);
                        invalidate();
                        
                    } else if (isDraggingAllKeys) {
                        preferences.setXOffsetWhiteKeys(preferences.getXOffsetWhiteKeys() + (int) dx);
                        preferences.setYOffsetWhiteKeys(preferences.getYOffsetWhiteKeys() + (int) dy);
                        
                        if (onKeyboardMovedListener != null) {
                            onKeyboardMovedListener.onKeyboardMoved((int) dx, (int) dy);
                        }
                        
                        // Обновляем детекцию при перемещении
                        if (showKeyPresses) {
                            detectKeyPresses();
                        }
                        
                        invalidate();
                        
                    } else if (!isDraggingView) {
                        float rawDx = event.getX() - lastRawX;
                        float rawDy = event.getY() - lastRawY;
                        translateX += rawDx;
                        translateY += rawDy;
                        invalidate();
                    }
                    
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    lastRawX = event.getX();
                    lastRawY = event.getY();
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDraggingKey = false;
                isDraggingAllKeys = false;
                isDraggingView = false;
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2 && !moveKeyboardMode) {
                    isDraggingAllKeys = true;
                    isDraggingKey = false;
                }
                break;
                
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() == 2) {
                    isDraggingAllKeys = false;
                }
                break;
        }
        
        return true;
    }
    
    private int findKeyAtPosition(float x, float y) {
        if (preferences == null) return -1;
        
        List<KeyPosition> keys = preferences.getKeysPositions();
        int xOffset = preferences.getXOffsetWhiteKeys();
        int yOffset = preferences.getYOffsetWhiteKeys();
        
        // Сначала проверяем черные клавиши
        for (int i = 0; i < keys.size(); i++) {
            if (!isBlackKey(i)) continue;
            
            KeyPosition keyPos = keys.get(i);
            int keyX = keyPos.getX() + xOffset;
            int keyY = keyPos.getY() + yOffset;
            
            if (Math.abs(x - keyX) < 15 && Math.abs(y - keyY) < 30) {
                return i;
            }
        }
        
        // Потом белые клавиши
        for (int i = 0; i < keys.size(); i++) {
            if (isBlackKey(i)) continue;
            
            KeyPosition keyPos = keys.get(i);
            int keyX = keyPos.getX() + xOffset;
            int keyY = keyPos.getY() + yOffset;
            
            if (Math.abs(x - keyX) < 20 && Math.abs(y - keyY) < 45) {
                return i;
            }
        }
        
        return -1;
    }
    
    public void pickColorAt(float x, float y) {
        if (displayBitmap == null || onColorPickListener == null) return;
        
        float touchX = (x - translateX) / scaleFactor;
        float touchY = (y - translateY) / scaleFactor;
        
        int pixelX = (int) touchX;
        int pixelY = (int) touchY;
        
        if (pixelX >= 0 && pixelX < displayBitmap.getWidth() && 
            pixelY >= 0 && pixelY < displayBitmap.getHeight()) {
            
            int pixel = displayBitmap.getPixel(pixelX, pixelY);
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            onColorPickListener.onColorPick(pixelX, pixelY, new int[]{r, g, b});
        }
    }
    
    public void resetTransform() {
        translateX = 0;
        translateY = 0;
        scaleFactor = 1.0f;
        invalidate();
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 5.0f));
            invalidate();
            return true;
        }
    }
    
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            resetTransform();
            return true;
        }
    }
}