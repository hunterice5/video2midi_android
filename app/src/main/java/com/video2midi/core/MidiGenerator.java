package com.video2midi.core;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import com.video2midi.model.MidiNote;
import com.video2midi.model.Preferences;
import com.video2midi.model.KeyPosition;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MidiGenerator {
    private static final String TAG = "MidiGenerator";
    private static final int MAX_NOTES = 144;

    // ДОБАВЛЕНО: Дебаг параметры
    private static final boolean DEBUG_SAVE_FRAMES = false; // Установите true для сохранения кадров
    private static final int DEBUG_SAVE_INTERVAL = 2; // Сохранять каждый N-й кадр
    private static final int DEBUG_JPEG_QUALITY = 85;
    private File debugFrameDir;

    private List<MidiNote> notes;
    private Preferences prefs;
    private VideoProcessor videoProcessor;
    private ColorDetector colorDetector;

    private int[] noteState;
    private int[] noteStartFrame;
    private int[] noteEndFrame;
    private int[] noteChannel;
    private int[] noteTempState;
    private int[][] notePressedColor;
    private long StartTimeMillis = System.currentTimeMillis();

    private ProgressCallback progressCallback;
    private boolean isCancelled = false;

    // ДОБАВЛЕНО: Статистика времени
    private long totalFrameLoadTime = 0;
    private long totalFrameSaveTime = 0;
    private int frameLoadCount = 0;
    private int frameSaveCount = 0;

    public interface ProgressCallback {
        void onProgress(int current, int total);
        void onFrameProcessed(int frameNumber);
        void onNotesUpdated(int noteCount);
    }

    public MidiGenerator(VideoProcessor videoProcessor, Preferences prefs) {
        this.videoProcessor = videoProcessor;
        this.prefs = prefs;
        this.colorDetector = new ColorDetector(prefs);
        this.notes = new ArrayList<>();

        noteState = new int[MAX_NOTES];
        noteStartFrame = new int[MAX_NOTES];
        noteEndFrame = new int[MAX_NOTES];
        noteChannel = new int[MAX_NOTES];
        noteTempState = new int[MAX_NOTES];
        notePressedColor = new int[MAX_NOTES][3];

        resetNoteStates();

        // ДОБАВЛЕНО: Инициализация дебаг директории
        if (DEBUG_SAVE_FRAMES) {
            initDebugDirectory();
        }
    }

    // ДОБАВЛЕНО: Инициализация директории для дебаг кадров
    private void initDebugDirectory() {
        try {
            File musicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC);
            debugFrameDir = new File(musicDir, "frame_strips");

            if (!debugFrameDir.exists()) {
                if (debugFrameDir.mkdirs()) {
                    Log.d(TAG, "Debug frame directory created: " + debugFrameDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to create debug frame directory");
                    debugFrameDir = null;
                }
            } else {
                // Очищаем старые кадры
                File[] oldFiles = debugFrameDir.listFiles();
                if (oldFiles != null) {
                    for (File file : oldFiles) {
                        if (file.getName().endsWith(".jpg")) {
                            file.delete();
                        }
                    }
                }
                Log.d(TAG, "Debug frame directory cleared: " + debugFrameDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing debug directory", e);
            debugFrameDir = null;
        }
    }

    private void resetNoteStates() {
        for (int i = 0; i < MAX_NOTES; i++) {
            noteState[i] = 0;
            noteStartFrame[i] = 0;
            noteEndFrame[i] = 0;
            noteChannel[i] = 0;
            noteTempState[i] = 0;
            notePressedColor[i] = new int[]{0, 0, 0};
        }
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void cancel() {
        isCancelled = true;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public boolean process(int startFrame, int endFrame) {
        isCancelled = false;
        notes.clear();
        resetNoteStates();
        StartTimeMillis = System.currentTimeMillis();

        // ДОБАВЛЕНО: Сброс статистики
        totalFrameLoadTime = 0;
        totalFrameSaveTime = 0;
        frameLoadCount = 0;
        frameSaveCount = 0;

        double fps = videoProcessor.getFPS();
        int baseNote = prefs.getOctave() * 12;
        int lastNoteCount = 0;

        Log.d(TAG, String.format("Starting processing from frame %d to %d (DEBUG_SAVE_FRAMES: %s)",
                startFrame, endFrame, DEBUG_SAVE_FRAMES));

        // ДОБАВЛЕНО: Замер времени загрузки первого кадра
        long frameLoadStart = System.currentTimeMillis();
        videoProcessor.processFrame(startFrame);
        long frameLoadTime = System.currentTimeMillis() - frameLoadStart;
        totalFrameLoadTime += frameLoadTime;
        frameLoadCount++;

        Log.d(TAG, String.format("Initial frame loaded in %d ms", frameLoadTime));

        for (int frame = startFrame; frame < endFrame && !isCancelled; frame++) {
            // ДОБАВЛЕНО: Замер времени загрузки кадра
            frameLoadStart = System.currentTimeMillis();
            boolean frameLoaded = videoProcessor.processFrame(frame);
            frameLoadTime = System.currentTimeMillis() - frameLoadStart;

            totalFrameLoadTime += frameLoadTime;
            frameLoadCount++;

            if (!frameLoaded) {
                Log.w(TAG, String.format("Failed to process frame %d (load time: %d ms)",
                        frame, frameLoadTime));
                continue;
            }

            // ДОБАВЛЕНО: Дебаг сохранение кадра
            if (DEBUG_SAVE_FRAMES && debugFrameDir != null && frame % DEBUG_SAVE_INTERVAL == 0) {
                long saveStart = System.currentTimeMillis();
                saveDebugFrame(frame);
                long saveTime = System.currentTimeMillis() - saveStart;
                totalFrameSaveTime += saveTime;
                frameSaveCount++;

                Log.v(TAG, String.format("Frame %d: load=%d ms, save=%d ms",
                        frame, frameLoadTime, saveTime));
            } else if (frame % 100 == 0) {
                // Логируем время загрузки для обычных кадров
                Log.v(TAG, String.format("Frame %d loaded in %d ms", frame, frameLoadTime));
            }

            processFrame(frame, fps, baseNote);

            if (notes.size() != lastNoteCount) {
                lastNoteCount = notes.size();
                if (progressCallback != null) {
                    progressCallback.onNotesUpdated(notes.size());
                }
            }

            if (progressCallback != null) {
                if (frame % 10 == 0) {
                    progressCallback.onProgress(frame - startFrame, endFrame - startFrame);
                }
                progressCallback.onFrameProcessed(frame);
            }

            if (frame % 100 == 0) {
                long etime = (System.currentTimeMillis() - StartTimeMillis) / 1000;
                float progress = (frame - startFrame) * 100.0f / (endFrame - startFrame);

                float framesPerSecond = (frame - startFrame) / (float)Math.max(1, etime);
                int remainingFrames = endFrame - frame;
                int estimatedSecondsLeft = (int)(remainingFrames / Math.max(0.1f, framesPerSecond));

                // ДОБАВЛЕНО: Средние времена загрузки и сохранения
                long avgLoadTime = frameLoadCount > 0 ? totalFrameLoadTime / frameLoadCount : 0;
                long avgSaveTime = frameSaveCount > 0 ? totalFrameSaveTime / frameSaveCount : 0;

                Log.d(TAG, String.format("Frame: %d/%d (%.1f%%) | Notes: %d | Elapsed: %ds | ETA: %ds | Speed: %.1f fps | Avg Load: %d ms | Avg Save: %d ms",
                        frame, endFrame, progress, notes.size(), etime, estimatedSecondsLeft,
                        framesPerSecond, avgLoadTime, avgSaveTime));
            }
        }

        finalizeNotes(endFrame, fps, baseNote);

        if (progressCallback != null) {
            progressCallback.onNotesUpdated(notes.size());
        }

        long totalTime = (System.currentTimeMillis() - StartTimeMillis) / 1000;

        // ДОБАВЛЕНО: Финальная статистика
        long avgLoadTime = frameLoadCount > 0 ? totalFrameLoadTime / frameLoadCount : 0;
        long avgSaveTime = frameSaveCount > 0 ? totalFrameSaveTime / frameSaveCount : 0;

        Log.d(TAG, String.format("Processing %s. Generated %d notes in %d seconds",
                isCancelled ? "cancelled" : "complete", notes.size(), totalTime));
        Log.d(TAG, String.format("Frame loading stats: count=%d, total=%d ms, avg=%d ms",
                frameLoadCount, totalFrameLoadTime, avgLoadTime));

        if (DEBUG_SAVE_FRAMES && frameSaveCount > 0) {
            Log.d(TAG, String.format("Frame saving stats: count=%d, total=%d ms, avg=%d ms",
                    frameSaveCount, totalFrameSaveTime, avgSaveTime));
            Log.d(TAG, "Debug frames saved to: " + debugFrameDir.getAbsolutePath());
        }

        return !isCancelled;
    }

    // ДОБАВЛЕНО: Метод сохранения дебаг кадра
// ДОБАВЛЕНО: Метод сохранения дебаг кадра с визуализацией позиций клавиш
// РАСШИРЕННАЯ ВЕРСИЯ: с визуализацией spark позиций и зон детекции
    private void saveDebugFrame(int frameNumber) {
        if (debugFrameDir == null) {
            return;
        }

        FileOutputStream out = null;
        try {
            Bitmap originalFrame = videoProcessor.getCurrentFrame();
            if (originalFrame == null || originalFrame.isRecycled()) {
                Log.w(TAG, "Cannot save debug frame " + frameNumber + " - bitmap is null or recycled");
                return;
            }

            Bitmap frame = originalFrame.copy(Bitmap.Config.ARGB_8888, true);
            originalFrame.recycle();

            android.graphics.Canvas canvas = new android.graphics.Canvas(frame);
            List<KeyPosition> keyPositions = prefs.getKeysPositions();

            // 1. Рисуем линию spark позиции (если включено)
            if (prefs.isUseSparks()) {
                android.graphics.Paint sparkLinePaint = new android.graphics.Paint();
                sparkLinePaint.setColor(android.graphics.Color.CYAN);
                sparkLinePaint.setStrokeWidth(2);
                sparkLinePaint.setStyle(android.graphics.Paint.Style.STROKE);
                sparkLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 5}, 0));

                int sparkY = prefs.getSparkYPos();
                canvas.drawLine(0, sparkY, frame.getWidth(), sparkY, sparkLinePaint);
            }

            // 2. Рисуем прямоугольники для каждой клавиши
            android.graphics.Paint keyPaint = new android.graphics.Paint();
            keyPaint.setStyle(android.graphics.Paint.Style.STROKE);
            keyPaint.setStrokeWidth(3);
            keyPaint.setAntiAlias(true);

            android.graphics.Paint fillPaint = new android.graphics.Paint();
            fillPaint.setStyle(android.graphics.Paint.Style.FILL);
            fillPaint.setAntiAlias(true);

            int pressedCount = 0;
            int sparkCount = 0;

            for (int keyIndex = 0; keyIndex < keyPositions.size(); keyIndex++) {
                KeyPosition keyPos = keyPositions.get(keyIndex);

                int pixelX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
                int pixelY = keyPos.getY() + prefs.getYOffsetWhiteKeys();

                if (pixelX < 0 || pixelX >= videoProcessor.getVideoWidth() ||
                        pixelY < 0 || pixelY >= videoProcessor.getVideoHeight()) {
                    continue;
                }

                int[] pixelColor = videoProcessor.getPixelColor(pixelX, pixelY);

                int[] sparkColor = null;
                if (prefs.isUseSparks()) {
                    int sparkX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
                    int sparkY = prefs.getSparkYPos();
                    sparkColor = videoProcessor.getAveragePixelColor(sparkX, sparkY, 1);
                }

                ColorDetector.KeyPressResult result =
                        colorDetector.detectKeyPress(pixelColor, sparkColor, keyIndex);

                // Выбираем цвет
                int color;
                if (result.isPressed) {
                    color = android.graphics.Color.RED;
                    pressedCount++;
                } else if (result.isPressedBySpark) {
                    color = android.graphics.Color.rgb(255, 165, 0);
                    sparkCount++;
                } else {
                    color = android.graphics.Color.GREEN;
                }

                keyPaint.setColor(color);
                fillPaint.setColor(android.graphics.Color.argb(60,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color)));

                int rectSize = 8;

                // Заливка (полупрозрачная)
                canvas.drawRect(
                        pixelX - rectSize / 2,
                        pixelY - rectSize / 2,
                        pixelX + rectSize / 2,
                        pixelY + rectSize / 2,
                        fillPaint
                );

                // Контур
                canvas.drawRect(
                        pixelX - rectSize / 2,
                        pixelY - rectSize / 2,
                        pixelX + rectSize / 2,
                        pixelY + rectSize / 2,
                        keyPaint
                );

                // Центральная точка
                android.graphics.Paint centerPaint = new android.graphics.Paint();
                centerPaint.setColor(android.graphics.Color.WHITE);
                centerPaint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawCircle(pixelX, pixelY, 1.5f, centerPaint);

                // Номер клавиши (только для нажатых клавиш или каждой 12-й)
                if (result.isPressed || result.isPressedBySpark || keyIndex % 12 == 0) {
                    android.graphics.Paint textPaint = new android.graphics.Paint();
                    textPaint.setColor(android.graphics.Color.YELLOW);
                    textPaint.setTextSize(10);
                    textPaint.setAntiAlias(true);

                    android.graphics.Paint bgPaint = new android.graphics.Paint();
                    bgPaint.setColor(android.graphics.Color.argb(180, 0, 0, 0));

                    String keyLabel = String.valueOf(keyIndex);
                    float textWidth = textPaint.measureText(keyLabel);

                    canvas.drawRect(
                            pixelX + rectSize / 2 + 1,
                            pixelY - 6,
                            pixelX + rectSize / 2 + textWidth + 3,
                            pixelY + 6,
                            bgPaint
                    );

                    canvas.drawText(keyLabel, pixelX + rectSize / 2 + 2, pixelY + 3, textPaint);
                }
            }

            // 3. Информационная панель
            android.graphics.Paint infoPaint = new android.graphics.Paint();
            infoPaint.setColor(android.graphics.Color.WHITE);
            infoPaint.setTextSize(14);
            infoPaint.setAntiAlias(true);

            android.graphics.Paint infoBgPaint = new android.graphics.Paint();
            infoBgPaint.setColor(android.graphics.Color.argb(200, 0, 0, 0));

            int infoY = 10;
            int infoLineHeight = 18;

            // Фон панели
            canvas.drawRect(5, 5, 280, 5 + infoLineHeight * 6 + 5, infoBgPaint);

            // Информация
            canvas.drawText(String.format("Frame: %d / %d", frameNumber, videoProcessor.getFrameCount()),
                    10, infoY += infoLineHeight, infoPaint);
            canvas.drawText(String.format("Notes: %d", notes.size()),
                    10, infoY += infoLineHeight, infoPaint);
            canvas.drawText(String.format("Keys: %d (P:%d S:%d)",
                            keyPositions.size(), pressedCount, sparkCount),
                    10, infoY += infoLineHeight, infoPaint);
            canvas.drawText(String.format("FPS: %.2f", videoProcessor.getFPS()),
                    10, infoY += infoLineHeight, infoPaint);

            // Легенда
            infoY += 5;
            int legendX = 10;
            int legendSize = 12;

            keyPaint.setStrokeWidth(2);

            keyPaint.setColor(android.graphics.Color.RED);
            canvas.drawRect(legendX, infoY, legendX + legendSize, infoY + legendSize, keyPaint);
            canvas.drawText("Pressed", legendX + legendSize + 5, infoY + 10, infoPaint);

            keyPaint.setColor(android.graphics.Color.rgb(255, 165, 0));
            canvas.drawRect(legendX + 100, infoY, legendX + 100 + legendSize, infoY + legendSize, keyPaint);
            canvas.drawText("Spark", legendX + 100 + legendSize + 5, infoY + 10, infoPaint);

            keyPaint.setColor(android.graphics.Color.GREEN);
            canvas.drawRect(legendX + 180, infoY, legendX + 180 + legendSize, infoY + legendSize, keyPaint);
            canvas.drawText("Released", legendX + 180 + legendSize + 5, infoY + 10, infoPaint);

            // Сохранение
            File frameFile = new File(debugFrameDir, String.format("frame_%06d.jpg", frameNumber));
            out = new FileOutputStream(frameFile);

            frame.compress(Bitmap.CompressFormat.JPEG, DEBUG_JPEG_QUALITY, out);
            out.flush();

            frame.recycle();

            Log.v(TAG, String.format("Debug frame saved: %s (%.2f KB, %d keys, %d pressed)",
                    frameFile.getName(), frameFile.length() / 1024.0, keyPositions.size(), pressedCount));

        } catch (Exception e) {
            Log.e(TAG, "Error saving debug frame " + frameNumber, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }
    private void saveDebugFrame_old(int frameNumber) {
        if (debugFrameDir == null) {
            return;
        }

        FileOutputStream out = null;
        try {
            Bitmap originalFrame = videoProcessor.getCurrentFrame();
            if (originalFrame == null || originalFrame.isRecycled()) {
                Log.w(TAG, "Cannot save debug frame " + frameNumber + " - bitmap is null or recycled");
                return;
            }

            // Создаем mutable копию для рисования
            Bitmap frame = originalFrame.copy(Bitmap.Config.ARGB_8888, true);
            originalFrame.recycle(); // Освобождаем оригинал

            // Рисуем прямоугольники для позиций клавиш
            android.graphics.Canvas canvas = new android.graphics.Canvas(frame);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setAntiAlias(true);

            List<KeyPosition> keyPositions = prefs.getKeysPositions();

            for (int keyIndex = 0; keyIndex < keyPositions.size(); keyIndex++) {
                KeyPosition keyPos = keyPositions.get(keyIndex);

                int pixelX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
                int pixelY = keyPos.getY() + prefs.getYOffsetWhiteKeys();

                // Проверка границ
                if (pixelX < 0 || pixelX >= videoProcessor.getVideoWidth() ||
                        pixelY < 0 || pixelY >= videoProcessor.getVideoHeight()) {
                    continue;
                }

                // Получаем цвет пикселя для определения состояния клавиши
                int[] pixelColor = videoProcessor.getPixelColor(pixelX, pixelY);

                int[] sparkColor = null;
                if (prefs.isUseSparks()) {
                    int sparkX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
                    int sparkY = prefs.getSparkYPos();
                    sparkColor = videoProcessor.getAveragePixelColor(sparkX, sparkY, 1);
                }

                ColorDetector.KeyPressResult result =
                        colorDetector.detectKeyPress(pixelColor, sparkColor, keyIndex);

                // Выбираем цвет прямоугольника в зависимости от состояния клавиши
                if (result.isPressed) {
                    // Нажата клавиша - красный
                    paint.setColor(android.graphics.Color.RED);
                } else if (result.isPressedBySpark) {
                    // Нажата через spark - оранжевый
                    paint.setColor(android.graphics.Color.rgb(255, 165, 0)); // Orange
                } else {
                    // Не нажата - зеленый
                    paint.setColor(android.graphics.Color.GREEN);
                }

                // Размер прямоугольника (5x5 пикселей с центром в позиции клавиши)
                int rectSize = 5;
                canvas.drawRect(
                        pixelX - rectSize / 2,
                        pixelY - rectSize / 2,
                        pixelX + rectSize / 2,
                        pixelY + rectSize / 2,
                        paint
                );

                // ДОБАВЛЕНО: Рисуем номер клавиши
                android.graphics.Paint textPaint = new android.graphics.Paint();
                textPaint.setColor(android.graphics.Color.YELLOW);
                textPaint.setTextSize(12);
                textPaint.setAntiAlias(true);
                textPaint.setStyle(android.graphics.Paint.Style.FILL);

                // Фон для текста
                android.graphics.Paint bgPaint = new android.graphics.Paint();
                bgPaint.setColor(android.graphics.Color.argb(128, 0, 0, 0)); // Полупрозрачный черный
                bgPaint.setStyle(android.graphics.Paint.Style.FILL);

                String keyLabel = String.valueOf(keyIndex);
                float textWidth = textPaint.measureText(keyLabel);

                canvas.drawRect(
                        pixelX + rectSize / 2 + 1,
                        pixelY - rectSize / 2 - 2,
                        pixelX + rectSize / 2 + textWidth + 4,
                        pixelY - rectSize / 2 + 12,
                        bgPaint
                );

                canvas.drawText(
                        keyLabel,
                        pixelX + rectSize / 2 + 2,
                        pixelY - rectSize / 2 + 9,
                        textPaint
                );
            }

            // ДОБАВЛЕНО: Рисуем информацию о кадре
            android.graphics.Paint infoPaint = new android.graphics.Paint();
            infoPaint.setColor(android.graphics.Color.WHITE);
            infoPaint.setTextSize(16);
            infoPaint.setAntiAlias(true);
            infoPaint.setStyle(android.graphics.Paint.Style.FILL);

            android.graphics.Paint infoBgPaint = new android.graphics.Paint();
            infoBgPaint.setColor(android.graphics.Color.argb(180, 0, 0, 0));
            infoBgPaint.setStyle(android.graphics.Paint.Style.FILL);

            String frameInfo = String.format("Frame: %d | Notes: %d | Keys: %d",
                    frameNumber, notes.size(), keyPositions.size());

            canvas.drawRect(5, 5, 5 + infoPaint.measureText(frameInfo) + 10, 30, infoBgPaint);
            canvas.drawText(frameInfo, 10, 22, infoPaint);

            // ДОБАВЛЕНО: Легенда цветов
            int legendY = 40;
            int legendX = 10;

            // Красный - нажата
            paint.setColor(android.graphics.Color.RED);
            canvas.drawRect(legendX, legendY, legendX + 15, legendY + 15, paint);
            canvas.drawText("Pressed", legendX + 20, legendY + 12, infoPaint);

            // Оранжевый - spark
            legendY += 20;
            paint.setColor(android.graphics.Color.rgb(255, 165, 0));
            canvas.drawRect(legendX, legendY, legendX + 15, legendY + 15, paint);
            canvas.drawText("Spark", legendX + 20, legendY + 12, infoPaint);

            // Зеленый - не нажата
            legendY += 20;
            paint.setColor(android.graphics.Color.GREEN);
            canvas.drawRect(legendX, legendY, legendX + 15, legendY + 15, paint);
            canvas.drawText("Released", legendX + 20, legendY + 12, infoPaint);

            // Сохраняем кадр
            File frameFile = new File(debugFrameDir, String.format("frame_%06d.jpg", frameNumber));
            out = new FileOutputStream(frameFile);

            frame.compress(Bitmap.CompressFormat.JPEG, DEBUG_JPEG_QUALITY, out);
            out.flush();

            frame.recycle();

            Log.v(TAG, String.format("Debug frame saved: %s (%.2f KB, %d keys drawn)",
                    frameFile.getName(), frameFile.length() / 1024.0, keyPositions.size()));

        } catch (Exception e) {
            Log.e(TAG, "Error saving debug frame " + frameNumber, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }

    private void processFrame(int frame, double fps, int baseNote) {
        List<KeyPosition> keyPositions = prefs.getKeysPositions();

        for (int keyIndex = 0; keyIndex < keyPositions.size(); keyIndex++) {
            if (keyIndex >= MAX_NOTES) break;

            KeyPosition keyPos = keyPositions.get(keyIndex);

            int pixelX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
            int pixelY = keyPos.getY() + prefs.getYOffsetWhiteKeys();

            if (pixelX < 0 || pixelX >= videoProcessor.getVideoWidth() ||
                    pixelY < 0 || pixelY >= videoProcessor.getVideoHeight()) {
                noteTempState[keyIndex] = 0;
                continue;
            }

            int[] pixelColor = videoProcessor.getPixelColor(pixelX, pixelY);

            int[] sparkColor = null;
            if (prefs.isUseSparks()) {
                int sparkX = keyPos.getX() + prefs.getXOffsetWhiteKeys();
                int sparkY = prefs.getSparkYPos();
                sparkColor = videoProcessor.getAveragePixelColor(sparkX, sparkY, 1);
            }

            ColorDetector.KeyPressResult result =
                    colorDetector.detectKeyPress(pixelColor, sparkColor, keyIndex);

            int keyPressed = 0;
            if (result.isPressed) {
                keyPressed = 1;
                notePressedColor[keyIndex] = pixelColor;
            } else if (result.isPressedBySpark) {
                keyPressed = 2;
            }

            noteTempState[keyIndex] = keyPressed;

            if (keyPressed == 1 || (keyPressed == 2 && noteState[keyIndex] != 1)) {
                if (noteState[keyIndex] == 0) {
                    noteStartFrame[keyIndex] = frame;
                    noteChannel[keyIndex] = result.channel;
                }
                noteState[keyIndex] = keyPressed;
            }
        }

        if (prefs.isRollcheck()) {
            applyRollcheck();
        }

        for (int keyIndex = 0; keyIndex < keyPositions.size(); keyIndex++) {
            if (keyIndex >= MAX_NOTES) break;

            int keyPressed = noteTempState[keyIndex];

            if (keyPressed == 0 && noteState[keyIndex] != 0) {
                noteEndFrame[keyIndex] = frame;

                float time = noteStartFrame[keyIndex] / (float) fps;
                float duration = (noteEndFrame[keyIndex] - noteStartFrame[keyIndex]) / (float) fps;

                boolean ignore = false;
                if (duration < prefs.getMinimalDuration()) {
                    duration = prefs.getMinimalDuration();
                    if (prefs.isIgnoreMinimalDuration()) {
                        ignore = true;
                    }
                }

                if (!ignore) {
                    addNote(keyIndex, time, duration, baseNote);
                }

                noteState[keyIndex] = 0;

            } else if (keyPressed == 2 && noteState[keyIndex] == 1) {
                noteEndFrame[keyIndex] = frame;

                float time = noteStartFrame[keyIndex] / (float) fps;
                float duration = (noteEndFrame[keyIndex] - noteStartFrame[keyIndex]) / (float) fps;

                boolean ignore = false;
                if (duration < prefs.getMinimalDuration()) {
                    duration = prefs.getMinimalDuration();
                    if (prefs.isIgnoreMinimalDuration()) {
                        ignore = true;
                    }
                }

                if (!ignore) {
                    addNote(keyIndex, time, duration, baseNote);
                }

                noteState[keyIndex] = 2;
                noteStartFrame[keyIndex] = frame;
            }

            if (prefs.isNotesOverlap() && keyPressed != 0 && noteState[keyIndex] != 0) {
                ColorDetector.KeyPressResult currentResult =
                        colorDetector.detectKeyPress(
                                notePressedColor[keyIndex],
                                null,
                                keyIndex
                        );

                if (noteChannel[keyIndex] != currentResult.channel) {
                    float time = noteStartFrame[keyIndex] / (float) fps;
                    float duration = (frame - noteStartFrame[keyIndex]) / (float) fps;

                    boolean ignore = false;
                    if (duration < prefs.getMinimalDuration()) {
                        duration = prefs.getMinimalDuration();
                        if (prefs.isIgnoreMinimalDuration()) {
                            ignore = true;
                        }
                    }

                    if (!ignore) {
                        addNote(keyIndex, time, duration, baseNote);
                    }

                    noteStartFrame[keyIndex] = frame;
                    noteChannel[keyIndex] = currentResult.channel;
                }
            }
        }
    }

    private void applyRollcheck() {
        List<KeyPosition> keyPositions = prefs.getKeysPositions();

        for (int i = 1; i < keyPositions.size() - 1; i++) {
            if (i >= MAX_NOTES) break;

            if (noteState[i] == 0) continue;

            boolean isCurrentBlack = KeyPositionCalculator.isBlackKeyByIndex(i);

            if (prefs.isRollcheckPriority()) {
                if (!isCurrentBlack) {
                    if (noteTempState[i + 1] > 0) noteState[i] = 0;
                    if (noteTempState[i - 1] > 0) noteState[i] = 0;
                }
            } else {
                if (isCurrentBlack) {
                    if (noteTempState[i + 1] > 0) noteState[i] = 0;
                    if (noteTempState[i - 1] > 0) noteState[i] = 0;
                }
            }
        }
    }

    private void addNote(int noteId, float time, float duration, int baseNote) {
        float noteTime = time * prefs.getTempo() / 60.0f;
        float noteDuration = duration * prefs.getTempo() / 60.0f;

        MidiNote note = new MidiNote(
                noteChannel[noteId],
                baseNote + noteId,
                noteTime,
                noteDuration,
                100
        );

        notes.add(note);
    }

    private void finalizeNotes(int endFrame, double fps, int baseNote) {
        for (int i = 0; i < MAX_NOTES; i++) {
            if (noteState[i] != 0) {
                float time = noteStartFrame[i] / (float) fps;
                float duration = (endFrame - noteStartFrame[i]) / (float) fps;

                boolean ignore = false;
                if (duration < prefs.getMinimalDuration()) {
                    duration = prefs.getMinimalDuration();
                    if (prefs.isIgnoreMinimalDuration()) {
                        ignore = true;
                    }
                }

                if (!ignore) {
                    addNote(i, time, duration, baseNote);
                }

                noteState[i] = 0;
            }
        }
    }

    public List<MidiNote> getNotes() {
        return notes;
    }

    public int getNoteCount() {
        return notes.size();
    }

    // ДОБАВЛЕНО: Получение статистики времени
    public long getAverageFrameLoadTime() {
        return frameLoadCount > 0 ? totalFrameLoadTime / frameLoadCount : 0;
    }

    public long getAverageFrameSaveTime() {
        return frameSaveCount > 0 ? totalFrameSaveTime / frameSaveCount : 0;
    }

    public long getTotalFrameLoadTime() {
        return totalFrameLoadTime;
    }

    public long getTotalFrameSaveTime() {
        return totalFrameSaveTime;
    }

    public void syncNotesStartPosition() {
        if (!prefs.isSyncNotesStartPos() || notes.isEmpty()) {
            return;
        }

        float timeDelta = prefs.getSyncNotesStartPosTimeDelta() / 1000.0f;

        for (int i = 0; i < notes.size(); i++) {
            for (int j = i + 1; j < notes.size(); j++) {
                MidiNote note1 = notes.get(i);
                MidiNote note2 = notes.get(j);

                if (Math.abs(note2.getTime() - note1.getTime()) < timeDelta) {
                    note2.setTime(note1.getTime());
                }
            }
        }
    }
}