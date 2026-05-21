package com.video2midi.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.video2midi.model.Preferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class VideoProcessor {
    private static final String TAG = "VideoProcessor";

    private FFmpegMediaMetadataRetriever retriever;
    private Preferences prefs;
    private Context context;

    private int videoWidth;
    private int videoHeight;
    private double fps;
    private long durationUs;
    private int frameCount;

    private Bitmap currentFrameBitmap;
    private Bitmap previousBitmap;

    private int currentFrameIndex = -1;
    private String videoPath;
    private File tempVideoFile;

    // Режим извлечения кадров
    public enum FrameExtractionMode {
        PRECISE,
        CLOSEST,
        CLOSEST_SYNC
    }

    private FrameExtractionMode extractionMode = FrameExtractionMode.CLOSEST;

    public VideoProcessor(Context context, String videoPath, Preferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.videoPath = videoPath;

        try {
            initializeVideo(videoPath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize video processor", e);
        }
    }

    private void initializeVideo(String videoPath) throws Exception {
        retriever = new FFmpegMediaMetadataRetriever();

        try {
            if (videoPath.startsWith("content://")) {
                Uri uri = Uri.parse(videoPath);

                // Копируем во временный файл для стабильности
                tempVideoFile = copyUriToTempFile(uri);
                if (tempVideoFile == null) {
                    throw new Exception("Failed to copy content URI to temp file");
                }
                retriever.setDataSource(tempVideoFile.getAbsolutePath());
                Log.d(TAG, "Initialized with temp file: " + tempVideoFile.getAbsolutePath());

            } else if (videoPath.startsWith("file://")) {
                String filePath = videoPath.substring(7);
                retriever.setDataSource(filePath);
                Log.d(TAG, "Initialized with file path: " + filePath);

            } else {
                retriever.setDataSource(videoPath);
                Log.d(TAG, "Initialized with direct path: " + videoPath);
            }

            extractMetadata();

            Log.d(TAG, String.format("Video initialized: %dx%d, fps: %.2f, frames: %d, mode: %s",
                    videoWidth, videoHeight, fps, frameCount, extractionMode));

        } catch (Exception e) {
            if (retriever != null) {
                retriever.release();
                retriever = null;
            }
            throw new Exception("Failed to initialize video", e);
        }
    }

    private File copyUriToTempFile(Uri uri) {
        File tempFile = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            tempFile = new File(context.getCacheDir(), "temp_video_" + System.currentTimeMillis() + ".mp4");

            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                return null;
            }

            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            outputStream.flush();

            Log.d(TAG, String.format("Copied %d bytes to temp file", totalBytes));
            return tempFile;

        } catch (Exception e) {
            Log.e(TAG, "Failed to copy URI", e);
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;

        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    private void extractMetadata() {
        try {
            String widthStr = retriever.extractMetadata(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            videoWidth = widthStr != null ? Integer.parseInt(widthStr) : 1920;

            String heightStr = retriever.extractMetadata(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            videoHeight = heightStr != null ? Integer.parseInt(heightStr) : 1080;

            String durationStr = retriever.extractMetadata(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
            durationUs = durationMs * 1000;

            String fpsStr = retriever.extractMetadata(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE);

            if (fpsStr != null) {
                try {
                    if (fpsStr.contains("/")) {
                        String[] parts = fpsStr.split("/");
                        double num = Double.parseDouble(parts[0]);
                        double den = Double.parseDouble(parts[1]);
                        fps = num / den;
                    } else {
                        fps = Double.parseDouble(fpsStr);
                    }
                } catch (Exception e) {
                    fps = 30.0;
                }
            } else {
                fps = 30.0;
            }

            if (fps < 1.0 || fps > 120.0) {
                Log.w(TAG, "Suspicious FPS: " + fps + ", using 30.0");
                fps = 30.0;
            }

            frameCount = (int) ((durationUs / 1000000.0) * fps);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata", e);
            videoWidth = 1920;
            videoHeight = 1080;
            fps = 30.0;
            durationUs = 0;
            frameCount = 0;
        }
    }

    public void setFrameExtractionMode(FrameExtractionMode mode) {
        this.extractionMode = mode;
        Log.d(TAG, "Frame extraction mode set to: " + mode);
    }

    public boolean processFrame(int frameNumber) {
        if (retriever == null) {
            Log.e(TAG, "Retriever not initialized");
            return false;
        }

        if (frameNumber < 0 || frameNumber >= frameCount) {
            Log.e(TAG, "Invalid frame number: " + frameNumber);
            return false;
        }

        try {
            // Если кадр уже загружен - возвращаем true
            if (currentFrameIndex == frameNumber && currentFrameBitmap != null
                    && !currentFrameBitmap.isRecycled()) {
                return true;
            }

            // Загружаем кадр
            boolean success = loadFrame(frameNumber);

            if (success) {
                currentFrameIndex = frameNumber;
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error processing frame " + frameNumber, e);
            return false;
        }
    }

    private boolean loadFrame(int frameNumber) {
        try {
            // Вычисляем точное время кадра
            long timeUs = (long) ((frameNumber * 1000000.0) / fps);

            if (timeUs > durationUs) {
                timeUs = durationUs;
            }

            // Выбираем опцию извлечения
            int option = getExtractionOption();

            // Загружаем кадр
            Bitmap newBitmap = retriever.getFrameAtTime(timeUs, option);

            if (newBitmap == null) {
                Log.w(TAG, "Failed to get frame " + frameNumber);
                return false;
            }

            // Освобождаем старые bitmap'ы
            if (previousBitmap != null && !previousBitmap.isRecycled()) {
                previousBitmap.recycle();
            }

            previousBitmap = currentFrameBitmap;
            currentFrameBitmap = newBitmap;

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading frame " + frameNumber, e);
            return false;
        }
    }

    private int getExtractionOption() {
        switch (extractionMode) {
            case PRECISE:
            case CLOSEST:
                return FFmpegMediaMetadataRetriever.OPTION_CLOSEST;

            case CLOSEST_SYNC:
                return FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC;

            default:
                return FFmpegMediaMetadataRetriever.OPTION_CLOSEST;
        }
    }

    public int[] getPixelColor(int x, int y) {
        if (currentFrameBitmap == null || currentFrameBitmap.isRecycled()) {
            return new int[]{0, 0, 0};
        }

        int bitmapWidth = currentFrameBitmap.getWidth();
        int bitmapHeight = currentFrameBitmap.getHeight();

        if (bitmapWidth != videoWidth || bitmapHeight != videoHeight) {
            x = (int) ((float) x / videoWidth * bitmapWidth);
            y = (int) ((float) y / videoHeight * bitmapHeight);
        }

        if (x < 0 || x >= bitmapWidth || y < 0 || y >= bitmapHeight) {
            return new int[]{0, 0, 0};
        }

        try {
            int pixel = currentFrameBitmap.getPixel(x, y);
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            return new int[]{r, g, b};
        } catch (Exception e) {
            Log.e(TAG, "Error reading pixel", e);
            return new int[]{0, 0, 0};
        }
    }

    public int[] getAveragePixelColor(int x, int y, int height) {
        if (currentFrameBitmap == null || currentFrameBitmap.isRecycled()) {
            return new int[]{0, 0, 0};
        }

        int bitmapWidth = currentFrameBitmap.getWidth();
        int bitmapHeight = currentFrameBitmap.getHeight();

        if (bitmapWidth != videoWidth || bitmapHeight != videoHeight) {
            x = (int) ((float) x / videoWidth * bitmapWidth);
            y = (int) ((float) y / videoHeight * bitmapHeight);
            height = (int) ((float) height / videoHeight * bitmapHeight);
        }

        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        for (int yOffset = 0; yOffset < height; yOffset++) {
            int currentY = y - yOffset;

            if (currentY < 0 || currentY >= bitmapHeight ||
                    x < 0 || x >= bitmapWidth) {
                continue;
            }

            try {
                int pixel = currentFrameBitmap.getPixel(x, currentY);
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

    public Bitmap getCurrentFrame() {
        if (currentFrameBitmap != null && !currentFrameBitmap.isRecycled()) {
            try {
                return currentFrameBitmap.copy(Bitmap.Config.ARGB_8888, false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to copy bitmap", e);
                return currentFrameBitmap;
            }
        }
        return null;
    }

    public Bitmap getVideoThumbnail() {
        try {
            long timeUs = durationUs / 2;
            return retriever.getFrameAtTime(timeUs,
                    FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            Log.e(TAG, "Error getting thumbnail", e);
            return null;
        }
    }

    public Bitmap getScaledFrame(int frameNumber, int width, int height) {
        try {
            long timeUs = (long) ((frameNumber * 1000000.0) / fps);

            if (timeUs > durationUs) {
                timeUs = durationUs;
            }

            return retriever.getScaledFrameAtTime(
                    timeUs,
                    getExtractionOption(),
                    width,
                    height
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting scaled frame", e);
            return null;
        }
    }

    public int getFrameCount() {
        return frameCount;
    }

    public double getFPS() {
        return fps;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public int getKeyframeCount() {
        return (int) Math.ceil(durationUs / 1000000.0 / 2.0);
    }

    public long getDurationUs() {
        return durationUs;
    }

    public String getMetadata(String key) {
        if (retriever == null) {
            return null;
        }
        try {
            return retriever.extractMetadata(key);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata: " + key, e);
            return null;
        }
    }

    public void release() {
        Log.d(TAG, "Releasing VideoProcessor resources");

        if (retriever != null) {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing retriever", e);
            }
            retriever = null;
        }

        if (currentFrameBitmap != null && !currentFrameBitmap.isRecycled()) {
            currentFrameBitmap.recycle();
            currentFrameBitmap = null;
        }

        if (previousBitmap != null && !previousBitmap.isRecycled()) {
            previousBitmap.recycle();
            previousBitmap = null;
        }

        if (tempVideoFile != null && tempVideoFile.exists()) {
            try {
                if (tempVideoFile.delete()) {
                    Log.d(TAG, "Deleted temp video file");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting temp file", e);
            }
            tempVideoFile = null;
        }

        Log.d(TAG, "VideoProcessor resources released");
    }
}