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

    private android.media.MediaMetadataRetriever nativeRetriever;
    private FFmpegMediaMetadataRetriever ffmpegRetriever;
    private boolean useNative = true;

    private Preferences prefs;
    private Context context;

    private int videoWidth;
    private int videoHeight;
    private double fps;
    private long durationUs;
    private int frameCount;

    private Bitmap currentFrameBitmap;
    private Bitmap previousBitmap;
    private int[] framePixels;
    private int framePixelsWidth;
    private int framePixelsHeight;

    private int currentFrameIndex = -1;
    private String videoPath;
    private File tempVideoFile;

    // Последовательный декодер для быстрой конвертации
    private SequentialDecoder sequentialDecoder;

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

    private String detectVideoCodec(String videoPath) {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            if (videoPath.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(videoPath), null);
            } else if (videoPath.startsWith("file://")) {
                extractor.setDataSource(videoPath.substring(7));
            } else {
                extractor.setDataSource(videoPath);
            }
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    return mime;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting video codec", e);
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }
        return "";
    }

    private void initializeVideo(String videoPath) throws Exception {
        String codecMime = detectVideoCodec(videoPath);
        Log.d(TAG, "Detected video codec MIME: " + codecMime);

        // AV1 video lacks codec support in bundled FFmpeg, so we MUST use native.
        // For other codecs (H.264, HEVC, etc.), we use FFmpeg for precise, frame-accurate seeking.
        if (codecMime.equalsIgnoreCase("video/av01") || codecMime.contains("av01")) {
            useNative = true;
            Log.i(TAG, "Codec is AV1. Prioritizing Native MediaMetadataRetriever.");
        } else {
            useNative = false;
            Log.i(TAG, "Codec is " + codecMime + ". Prioritizing FFmpegMediaMetadataRetriever for frame-accuracy.");
        }

        try {
            if (useNative) {
                nativeRetriever = new android.media.MediaMetadataRetriever();
                Log.d(TAG, "Initialized native MediaMetadataRetriever");
            } else {
                ffmpegRetriever = new FFmpegMediaMetadataRetriever();
                Log.d(TAG, "Initialized FFmpegMediaMetadataRetriever");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize preferred retriever, trying fallback", t);
            useNative = !useNative;
            try {
                if (useNative) {
                    nativeRetriever = new android.media.MediaMetadataRetriever();
                } else {
                    ffmpegRetriever = new FFmpegMediaMetadataRetriever();
                }
            } catch (Throwable t2) {
                Log.e(TAG, "Both retrievers failed initialization", t2);
                throw new Exception("Retriever initialization failed", t2);
            }
        }

        try {
            String pathForDataSource = videoPath;
            if (videoPath.startsWith("content://")) {
                Uri uri = Uri.parse(videoPath);

                // Копируем во временный файл для стабильности
                tempVideoFile = copyUriToTempFile(uri);
                if (tempVideoFile == null) {
                    throw new Exception("Failed to copy content URI to temp file");
                }
                pathForDataSource = tempVideoFile.getAbsolutePath();

            } else if (videoPath.startsWith("file://")) {
                pathForDataSource = videoPath.substring(7);
            }

            boolean success = false;
            Throwable firstError = null;

            try {
                if (useNative) {
                    nativeRetriever.setDataSource(pathForDataSource);
                    Log.d(TAG, "Native retriever setDataSource: " + pathForDataSource);
                } else {
                    ffmpegRetriever.setDataSource(pathForDataSource);
                    Log.d(TAG, "FFmpeg retriever setDataSource: " + pathForDataSource);
                }
                extractMetadata();
                success = true;
                Log.d(TAG, String.format("Video initialized on first attempt: %dx%d, fps: %.2f, frames: %d, mode: %s, useNative: %b",
                        videoWidth, videoHeight, fps, frameCount, extractionMode, useNative));
            } catch (Throwable e) {
                Log.w(TAG, "First attempt failed (useNative=" + useNative + "), trying fallback...", e);
                firstError = e;
            }

            if (!success) {
                useNative = !useNative; // Switch to fallback retriever
                try {
                    if (useNative) {
                        if (ffmpegRetriever != null) {
                            try { ffmpegRetriever.release(); } catch (Exception ignored) {}
                            ffmpegRetriever = null;
                        }
                        nativeRetriever = new android.media.MediaMetadataRetriever();
                        nativeRetriever.setDataSource(pathForDataSource);
                        Log.d(TAG, "Native fallback setDataSource: " + pathForDataSource);
                    } else {
                        if (nativeRetriever != null) {
                            try { nativeRetriever.release(); } catch (Exception ignored) {}
                            nativeRetriever = null;
                        }
                        ffmpegRetriever = new FFmpegMediaMetadataRetriever();
                        ffmpegRetriever.setDataSource(pathForDataSource);
                        Log.d(TAG, "FFmpeg fallback setDataSource: " + pathForDataSource);
                    }
                    extractMetadata();
                    success = true;
                    Log.d(TAG, String.format("Video initialized on fallback attempt: %dx%d, fps: %.2f, frames: %d, mode: %s, useNative: %b",
                            videoWidth, videoHeight, fps, frameCount, extractionMode, useNative));
                } catch (Throwable e2) {
                    Log.e(TAG, "Fallback attempt also failed", e2);
                    throw new Exception("Both FFmpeg and native retrievers failed to initialize video. First error: " 
                            + (firstError != null ? firstError.getMessage() : "null") + ", Fallback error: " + e2.getMessage(), e2);
                }
            }

        } catch (Throwable e) {
            Log.e(TAG, "Error setting data source or extracting metadata", e);
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
            String widthStr = null;
            String heightStr = null;
            String durationStr = null;
            String fpsStr = null;

            if (useNative) {
                widthStr = nativeRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                heightStr = nativeRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                durationStr = nativeRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    fpsStr = nativeRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                }
            } else {
                widthStr = ffmpegRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                heightStr = ffmpegRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                durationStr = ffmpegRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
                fpsStr = ffmpegRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE);
            }

            videoWidth = widthStr != null ? Integer.parseInt(widthStr) : 1920;
            videoHeight = heightStr != null ? Integer.parseInt(heightStr) : 1080;

            long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
            durationUs = durationMs * 1000;

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
                    fps = 0;
                }
            }

            // Fallback for FPS
            if (fps <= 0 || fps < 1.0 || fps > 120.0) {
                fps = getFrameRateFromExtractor(videoPath);
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

    private double getFrameRateFromExtractor(String videoPath) {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            if (videoPath.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(videoPath), null);
            } else if (videoPath.startsWith("file://")) {
                extractor.setDataSource(videoPath.substring(7));
            } else {
                extractor.setDataSource(videoPath);
            }
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                        return format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting frame rate with MediaExtractor", e);
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }
        return 30.0;
    }

    public void setFrameExtractionMode(FrameExtractionMode mode) {
        this.extractionMode = mode;
        Log.d(TAG, "Frame extraction mode set to: " + mode);
    }

    public void startSequentialDecoding(int startFrame, int frameStep) {
        try {
            if (sequentialDecoder != null) {
                sequentialDecoder.release();
            }
            sequentialDecoder = new SequentialDecoder(videoPath, startFrame, frameStep);
            Log.d(TAG, "Sequential decoding started at frame " + startFrame);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start sequential decoding, using fallback (retriever)", e);
            sequentialDecoder = null;
        }
    }

    public void stopSequentialDecoding() {
        if (sequentialDecoder != null) {
            sequentialDecoder.release();
            sequentialDecoder = null;
            Log.d(TAG, "Sequential decoding stopped");
        }
    }

    public boolean processFrame(int frameNumber) {
        if (sequentialDecoder != null) {
            if (sequentialDecoder.getCurrentFrameIndex() == frameNumber) {
                return true;
            }
            while (sequentialDecoder.getCurrentFrameIndex() < frameNumber) {
                if (!sequentialDecoder.decodeNextFrame()) {
                    return false;
                }
            }
            return sequentialDecoder.getCurrentFrameIndex() == frameNumber;
        }

        if (useNative ? (nativeRetriever == null) : (ffmpegRetriever == null)) {
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
            long timeUs = (long) ((frameNumber * 1000000.0) / fps);
            if (timeUs > durationUs) {
                timeUs = durationUs;
            }

            Bitmap newBitmap = null;
            if (useNative) {
                newBitmap = tryNativeLoad(timeUs, frameNumber);
                if (newBitmap == null) {
                    Log.w(TAG, "Native load failed for frame " + frameNumber + ", trying FFmpeg fallback");
                    newBitmap = tryFFmpegLoad(timeUs);
                }
            } else {
                newBitmap = tryFFmpegLoad(timeUs);
                if (newBitmap == null) {
                    Log.w(TAG, "FFmpeg load failed for frame " + frameNumber + ", trying native fallback");
                    newBitmap = tryNativeLoad(timeUs, frameNumber);
                }
            }

            if (newBitmap == null) {
                Log.w(TAG, "Failed to get frame " + frameNumber + " from both native and FFmpeg retrievers");
                return false;
            }

            // Copy pixels for faster access in getPixelColor / getAveragePixelColor
            try {
                int w = newBitmap.getWidth();
                int h = newBitmap.getHeight();
                if (framePixels == null || framePixels.length != w * h) {
                    framePixels = new int[w * h];
                }
                newBitmap.getPixels(framePixels, 0, w, 0, 0, w, h);
                framePixelsWidth = w;
                framePixelsHeight = h;
            } catch (Exception e) {
                Log.e(TAG, "Failed to copy pixels from bitmap", e);
                framePixels = null;
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

    private Bitmap tryNativeLoad(long timeUs, int frameNumber) {
        if (nativeRetriever == null) {
            try {
                nativeRetriever = new android.media.MediaMetadataRetriever();
                String pathForDataSource = videoPath;
                if (videoPath.startsWith("content://")) {
                    if (tempVideoFile != null) {
                        pathForDataSource = tempVideoFile.getAbsolutePath();
                    } else {
                        Uri uri = Uri.parse(videoPath);
                        tempVideoFile = copyUriToTempFile(uri);
                        if (tempVideoFile != null) {
                            pathForDataSource = tempVideoFile.getAbsolutePath();
                        }
                    }
                } else if (videoPath.startsWith("file://")) {
                    pathForDataSource = videoPath.substring(7);
                }
                nativeRetriever.setDataSource(pathForDataSource);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize native fallback", e);
                return null;
            }
        }

        // Try getFrameAtIndex on API 28+ first for 100% precise frame-accurate decoding
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && frameNumber >= 0) {
            try {
                Bitmap bmp = nativeRetriever.getFrameAtIndex(frameNumber);
                if (bmp != null) {
                    if (videoWidth > 0 && videoHeight > 0 && (bmp.getWidth() != videoWidth || bmp.getHeight() != videoHeight)) {
                        Bitmap upscaled = Bitmap.createScaledBitmap(bmp, videoWidth, videoHeight, true);
                        if (upscaled != bmp) {
                            bmp.recycle();
                        }
                        return upscaled;
                    }
                    return bmp;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Native getFrameAtIndex failed for frame " + frameNumber + ", falling back to time-based extraction", t);
            }
        }
        
        try {
            int option = getNativeExtractionOption();
            Bitmap scaled = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && videoWidth > 0 && videoHeight > 0) {
                try {
                    scaled = nativeRetriever.getScaledFrameAtTime(timeUs, option, 960, 540);
                } catch (Throwable t) {
                    Log.w(TAG, "Native getScaledFrameAtTime threw exception, trying unscaled fallback", t);
                }
                if (scaled != null) {
                    Bitmap upscaled = Bitmap.createScaledBitmap(scaled, videoWidth, videoHeight, true);
                    if (upscaled != scaled) {
                        scaled.recycle();
                    }
                    return upscaled;
                }
            }
            return nativeRetriever.getFrameAtTime(timeUs, option);
        } catch (Exception e) {
            Log.e(TAG, "Native tryLoad failed", e);
            return null;
        }
    }

    private Bitmap tryFFmpegLoad(long timeUs) {
        if (ffmpegRetriever == null) {
            try {
                ffmpegRetriever = new FFmpegMediaMetadataRetriever();
                String pathForDataSource = videoPath;
                if (videoPath.startsWith("content://")) {
                    if (tempVideoFile != null) {
                        pathForDataSource = tempVideoFile.getAbsolutePath();
                    } else {
                        Uri uri = Uri.parse(videoPath);
                        tempVideoFile = copyUriToTempFile(uri);
                        if (tempVideoFile != null) {
                            pathForDataSource = tempVideoFile.getAbsolutePath();
                        }
                    }
                } else if (videoPath.startsWith("file://")) {
                    pathForDataSource = videoPath.substring(7);
                }
                ffmpegRetriever.setDataSource(pathForDataSource);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize FFmpeg fallback", e);
                return null;
            }
        }
        
        try {
            int option = getFFmpegExtractionOption();
            Bitmap scaled = null;
            if (videoWidth > 0 && videoHeight > 0) {
                try {
                    scaled = ffmpegRetriever.getScaledFrameAtTime(timeUs, option, 960, 540);
                } catch (Throwable t) {
                    Log.w(TAG, "FFmpeg getScaledFrameAtTime threw exception, trying unscaled fallback", t);
                }
                if (scaled != null) {
                    Bitmap upscaled = Bitmap.createScaledBitmap(scaled, videoWidth, videoHeight, true);
                    if (upscaled != scaled) {
                        scaled.recycle();
                    }
                    return upscaled;
                }
            }
            return ffmpegRetriever.getFrameAtTime(timeUs, option);
        } catch (Exception e) {
            Log.e(TAG, "FFmpeg tryLoad failed", e);
            return null;
        }
    }

    private int getNativeExtractionOption() {
        switch (extractionMode) {
            case PRECISE:
            case CLOSEST:
                return android.media.MediaMetadataRetriever.OPTION_CLOSEST;

            case CLOSEST_SYNC:
                return android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;

            default:
                return android.media.MediaMetadataRetriever.OPTION_CLOSEST;
        }
    }

    private int getFFmpegExtractionOption() {
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
        if (sequentialDecoder != null) {
            return sequentialDecoder.getPixelColor(x, y);
        }

        if (framePixels == null) {
            return new int[]{0, 0, 0};
        }

        if (framePixelsWidth != videoWidth || framePixelsHeight != videoHeight) {
            x = (int) ((float) x / videoWidth * framePixelsWidth);
            y = (int) ((float) y / videoHeight * framePixelsHeight);
        }

        if (x < 0 || x >= framePixelsWidth || y < 0 || y >= framePixelsHeight) {
            return new int[]{0, 0, 0};
        }

        try {
            int pixel = framePixels[y * framePixelsWidth + x];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            return new int[]{r, g, b};
        } catch (Exception e) {
            return new int[]{0, 0, 0};
        }
    }

    public int[] getAveragePixelColor(int x, int y, int height) {
        if (sequentialDecoder != null) {
            return sequentialDecoder.getAveragePixelColor(x, y, height);
        }

        if (framePixels == null) {
            return new int[]{0, 0, 0};
        }

        if (framePixelsWidth != videoWidth || framePixelsHeight != videoHeight) {
            x = (int) ((float) x / videoWidth * framePixelsWidth);
            y = (int) ((float) y / videoHeight * framePixelsHeight);
        }

        int halfH = height / 2;
        int startY = Math.max(0, y - halfH);
        int endY = Math.min(framePixelsHeight - 1, y + halfH);

        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        for (int currY = startY; currY <= endY; currY++) {
            if (x >= 0 && x < framePixelsWidth) {
                int pixel = framePixels[currY * framePixelsWidth + x];
                sumR += (pixel >> 16) & 0xFF;
                sumG += (pixel >> 8) & 0xFF;
                sumB += pixel & 0xFF;
                count++;
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
        if (sequentialDecoder != null) {
            return sequentialDecoder.getCurrentFrameBitmap();
        }

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
            Bitmap result = null;
            if (useNative) {
                if (nativeRetriever != null) {
                    try {
                        result = nativeRetriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } catch (Throwable t) {
                        Log.w(TAG, "Native getVideoThumbnail failed, trying FFmpeg fallback", t);
                    }
                }
                if (result == null && ffmpegRetriever != null) {
                    try {
                        result = ffmpegRetriever.getFrameAtTime(timeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } catch (Throwable t) {
                        Log.e(TAG, "FFmpeg getVideoThumbnail fallback failed", t);
                    }
                }
            } else {
                if (ffmpegRetriever != null) {
                    try {
                        result = ffmpegRetriever.getFrameAtTime(timeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } catch (Throwable t) {
                        Log.w(TAG, "FFmpeg getVideoThumbnail failed, trying native fallback", t);
                    }
                }
                if (result == null && nativeRetriever != null) {
                    try {
                        result = nativeRetriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } catch (Throwable t) {
                        Log.e(TAG, "Native getVideoThumbnail fallback failed", t);
                    }
                }
            }
            return result;
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

            Bitmap result = null;
            if (useNative) {
                result = tryNativeGetScaledFrame(timeUs, width, height);
                if (result == null) {
                    Log.w(TAG, "Native getScaledFrame failed, trying FFmpeg fallback");
                    result = tryFFmpegGetScaledFrame(timeUs, width, height);
                }
            } else {
                result = tryFFmpegGetScaledFrame(timeUs, width, height);
                if (result == null) {
                    Log.w(TAG, "FFmpeg getScaledFrame failed, trying native fallback");
                    result = tryNativeGetScaledFrame(timeUs, width, height);
                }
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error getting scaled frame", e);
            return null;
        }
    }

    private Bitmap tryNativeGetScaledFrame(long timeUs, int width, int height) {
        if (nativeRetriever == null) return null;
        try {
            int option = getNativeExtractionOption();
            // Try getFrameAtIndex on API 28+ first for precise frame-accurate scaling
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    int frameNumber = (int) Math.round((timeUs / 1000000.0) * fps);
                    Bitmap bmp = nativeRetriever.getFrameAtIndex(frameNumber);
                    if (bmp != null) {
                        return Bitmap.createScaledBitmap(bmp, width, height, true);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Native tryNativeGetScaledFrame getFrameAtIndex failed, falling back to time-based extraction", t);
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                try {
                    Bitmap bmp = nativeRetriever.getScaledFrameAtTime(timeUs, option, width, height);
                    if (bmp != null) return bmp;
                } catch (Throwable t) {
                    Log.w(TAG, "Native getScaledFrameAtTime threw exception, trying unscaled + manual scale", t);
                }
            }
            Bitmap bmp = nativeRetriever.getFrameAtTime(timeUs, option);
            if (bmp != null) {
                return Bitmap.createScaledBitmap(bmp, width, height, true);
            }
        } catch (Throwable e) {
            Log.e(TAG, "tryNativeGetScaledFrame failed", e);
        }
        return null;
    }

    private Bitmap tryFFmpegGetScaledFrame(long timeUs, int width, int height) {
        if (ffmpegRetriever == null) return null;
        try {
            int option = getFFmpegExtractionOption();
            try {
                Bitmap bmp = ffmpegRetriever.getScaledFrameAtTime(timeUs, option, width, height);
                if (bmp != null) return bmp;
            } catch (Throwable t) {
                Log.w(TAG, "FFmpeg getScaledFrameAtTime threw exception, trying unscaled + manual scale", t);
            }
            Bitmap bmp = ffmpegRetriever.getFrameAtTime(timeUs, option);
            if (bmp != null) {
                return Bitmap.createScaledBitmap(bmp, width, height, true);
            }
        } catch (Throwable e) {
            Log.e(TAG, "tryFFmpegGetScaledFrame failed", e);
        }
        return null;
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
        if (sequentialDecoder != null) {
            return sequentialDecoder.getCurrentFrameIndex();
        }
        return currentFrameIndex;
    }

    public int getKeyframeCount() {
        return (int) Math.ceil(durationUs / 1000000.0 / 2.0);
    }

    public long getDurationUs() {
        return durationUs;
    }

    public String getMetadata(String key) {
        try {
            if (useNative) {
                if (nativeRetriever == null) return null;
                int nativeKey = -1;
                if (key.equals(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) {
                    nativeKey = android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH;
                } else if (key.equals(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) {
                    nativeKey = android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT;
                } else if (key.equals(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)) {
                    nativeKey = android.media.MediaMetadataRetriever.METADATA_KEY_DURATION;
                }
                if (nativeKey != -1) {
                    return nativeRetriever.extractMetadata(nativeKey);
                }
                return null;
            } else {
                if (ffmpegRetriever == null) return null;
                return ffmpegRetriever.extractMetadata(key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata: " + key, e);
            return null;
        }
    }

    public void release() {
        Log.d(TAG, "Releasing VideoProcessor resources");
        stopSequentialDecoding();

        if (nativeRetriever != null) {
            try {
                nativeRetriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing native retriever", e);
            }
            nativeRetriever = null;
        }

        if (ffmpegRetriever != null) {
            try {
                ffmpegRetriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ffmpeg retriever", e);
            }
            ffmpegRetriever = null;
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

    // Класс для последовательного и быстрого аппаратного декодирования кадров через MediaCodec
    private class SequentialDecoder {
        private android.media.MediaExtractor extractor;
        private android.media.MediaCodec decoder;
        private int trackIdx = -1;
        private int currentFrameIdx = -1;
        private android.media.Image currentImage;
        private android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
        private boolean isInputEOS = false;
        private boolean isOutputEOS = false;
        private int videoW;
        private int videoH;
        private int step;
        private int outputBufferId = -1;

        public SequentialDecoder(String videoPath, int startFrame, int step) throws Exception {
            this.step = step;
            extractor = new android.media.MediaExtractor();

            if (videoPath.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(videoPath), null);
            } else if (videoPath.startsWith("file://")) {
                extractor.setDataSource(videoPath.substring(7));
            } else {
                extractor.setDataSource(videoPath);
            }

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    trackIdx = i;
                    videoW = format.getInteger(android.media.MediaFormat.KEY_WIDTH);
                    videoH = format.getInteger(android.media.MediaFormat.KEY_HEIGHT);
                    break;
                }
            }

            if (trackIdx == -1) {
                throw new Exception("No video track found");
            }

            extractor.selectTrack(trackIdx);

            android.media.MediaFormat format = extractor.getTrackFormat(trackIdx);
            String mime = format.getString(android.media.MediaFormat.KEY_MIME);
            decoder = android.media.MediaCodec.createDecoderByType(mime);
            format.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, 
                    android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

            decoder.configure(format, null, null, 0);
            decoder.start();

            // Seek to start frame
            long seekTimeUs = (long) ((startFrame * 1000000.0) / fps);
            extractor.seekTo(seekTimeUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            currentFrameIdx = startFrame - step;
            decodeNextFrame();
        }

        public int getCurrentFrameIndex() {
            return currentFrameIdx;
        }

        public boolean decodeNextFrame() {
            if (isOutputEOS) return false;

            try {
                if (currentImage != null) {
                    currentImage.close();
                    currentImage = null;
                }

                if (outputBufferId >= 0) {
                    decoder.releaseOutputBuffer(outputBufferId, false);
                    outputBufferId = -1;
                }

                while (true) {
                    if (!isInputEOS) {
                        int inputBufferId = decoder.dequeueInputBuffer(5000);
                        if (inputBufferId >= 0) {
                            java.nio.ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferId, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isInputEOS = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }

                    int outIndex = decoder.dequeueOutputBuffer(bufferInfo, 5000);
                    if (outIndex >= 0) {
                        outputBufferId = outIndex;
                        if ((bufferInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEOS = true;
                        }

                        currentImage = decoder.getOutputImage(outputBufferId);
                        currentFrameIdx = (int) Math.round((bufferInfo.presentationTimeUs * fps) / 1000000.0);
                        return true;
                    } else if (outIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        android.media.MediaFormat newFormat = decoder.getOutputFormat();
                        videoW = newFormat.getInteger(android.media.MediaFormat.KEY_WIDTH);
                        videoH = newFormat.getInteger(android.media.MediaFormat.KEY_HEIGHT);
                    } else if (outIndex == android.media.MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (isInputEOS && isOutputEOS) {
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding next frame", e);
                return false;
            }
        }

        public int[] getPixelColor(int x, int y) {
            if (currentImage == null) return new int[]{0, 0, 0};

            try {
                int imgW = currentImage.getWidth();
                int imgH = currentImage.getHeight();
                if (imgW != videoWidth || imgH != videoHeight) {
                    x = (int) ((float) x / videoWidth * imgW);
                    y = (int) ((float) y / videoHeight * imgH);
                }

                x = Math.max(0, Math.min(x, imgW - 1));
                y = Math.max(0, Math.min(y, imgH - 1));

                android.media.Image.Plane[] planes = currentImage.getPlanes();

                // Y
                java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
                int yRowStride = planes[0].getRowStride();
                int yPixelStride = planes[0].getPixelStride();
                int yVal = yBuffer.get(y * yRowStride + x * yPixelStride) & 0xFF;

                // U & V
                int uvX = x / 2;
                int uvY = y / 2;

                java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
                int uRowStride = planes[1].getRowStride();
                int uPixelStride = planes[1].getPixelStride();
                int uVal = uBuffer.get(uvY * uRowStride + uvX * uPixelStride) & 0xFF;

                java.nio.ByteBuffer vBuffer = planes[2].getBuffer();
                int vRowStride = planes[2].getRowStride();
                int vPixelStride = planes[2].getPixelStride();
                int vVal = vBuffer.get(uvY * vRowStride + uvX * vPixelStride) & 0xFF;

                // Convert YUV to RGB
                int r = (int) (yVal + 1.370705f * (vVal - 128));
                int g = (int) (yVal - 0.337633f * (uVal - 128) - 0.698001f * (vVal - 128));
                int b = (int) (yVal + 1.732446f * (uVal - 128));
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                return new int[]{r, g, b};
            } catch (Exception e) {
                return new int[]{0, 0, 0};
            }
        }

        public int[] getAveragePixelColor(int x, int y, int height) {
            if (currentImage == null) return new int[]{0, 0, 0};

            try {
                int imgW = currentImage.getWidth();
                int imgH = currentImage.getHeight();
                if (imgW != videoWidth || imgH != videoHeight) {
                    x = (int) ((float) x / videoWidth * imgW);
                    y = (int) ((float) y / videoHeight * imgH);
                }

                int halfH = height / 2;
                int startY = Math.max(0, y - halfH);
                int endY = Math.min(imgH - 1, y + halfH);

                long sumR = 0, sumG = 0, sumB = 0;
                int count = 0;

                android.media.Image.Plane[] planes = currentImage.getPlanes();
                java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
                int yRowStride = planes[0].getRowStride();
                int yPixelStride = planes[0].getPixelStride();

                java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
                int uRowStride = planes[1].getRowStride();
                int uPixelStride = planes[1].getPixelStride();

                java.nio.ByteBuffer vBuffer = planes[2].getBuffer();
                int vRowStride = planes[2].getRowStride();
                int vPixelStride = planes[2].getPixelStride();

                for (int currY = startY; currY <= endY; currY++) {
                    if (x >= 0 && x < imgW) {
                        int yVal = yBuffer.get(currY * yRowStride + x * yPixelStride) & 0xFF;

                        int uvX = x / 2;
                        int uvY = currY / 2;
                        int uVal = uBuffer.get(uvY * uRowStride + uvX * uPixelStride) & 0xFF;
                        int vVal = vBuffer.get(uvY * vRowStride + uvX * vPixelStride) & 0xFF;

                        int r = (int) (yVal + 1.370705f * (vVal - 128));
                        int g = (int) (yVal - 0.337633f * (uVal - 128) - 0.698001f * (vVal - 128));
                        int b = (int) (yVal + 1.732446f * (uVal - 128));
                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        sumR += r;
                        sumG += g;
                        sumB += b;
                        count++;
                    }
                }

                if (count == 0) return new int[]{0, 0, 0};
                return new int[]{(int)(sumR/count), (int)(sumG/count), (int)(sumB/count)};
            } catch (Exception e) {
                return new int[]{0, 0, 0};
            }
        }

        public Bitmap getCurrentFrameBitmap() {
            if (currentImage == null) return null;
            try {
                int imgW = currentImage.getWidth();
                int imgH = currentImage.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888);
                int[] pixels = new int[imgW * imgH];

                android.media.Image.Plane[] planes = currentImage.getPlanes();
                java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
                int yRowStride = planes[0].getRowStride();
                int yPixelStride = planes[0].getPixelStride();

                java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
                int uRowStride = planes[1].getRowStride();
                int uPixelStride = planes[1].getPixelStride();

                java.nio.ByteBuffer vBuffer = planes[2].getBuffer();
                int vRowStride = planes[2].getRowStride();
                int vPixelStride = planes[2].getPixelStride();

                for (int cy = 0; cy < imgH; cy++) {
                    for (int cx = 0; cx < imgW; cx++) {
                        int yVal = yBuffer.get(cy * yRowStride + cx * yPixelStride) & 0xFF;

                        int uvX = cx / 2;
                        int uvY = cy / 2;
                        int uVal = uBuffer.get(uvY * uRowStride + uvX * uPixelStride) & 0xFF;
                        int vVal = vBuffer.get(uvY * vRowStride + uvX * vPixelStride) & 0xFF;

                        int r = (int) (yVal + 1.370705f * (vVal - 128));
                        int g = (int) (yVal - 0.337633f * (uVal - 128) - 0.698001f * (vVal - 128));
                        int b = (int) (yVal + 1.732446f * (uVal - 128));
                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        pixels[cy * imgW + cx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                bitmap.setPixels(pixels, 0, imgW, 0, 0, imgW, imgH);
                return bitmap;
            } catch (Exception e) {
                Log.e(TAG, "Error converting image to bitmap", e);
                return null;
            }
        }

        public void release() {
            if (currentImage != null) {
                try { currentImage.close(); } catch (Exception ignored) {}
                currentImage = null;
            }
            if (outputBufferId >= 0) {
                try { decoder.releaseOutputBuffer(outputBufferId, false); } catch (Exception ignored) {}
                outputBufferId = -1;
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {}
                decoder = null;
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception ignored) {}
                extractor = null;
            }
        }
    }
}