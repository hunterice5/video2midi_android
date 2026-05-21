package com.video2midi.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileHelper {
    private static final String TAG = "FileHelper";
    
    /**
     * Получает путь к файлу из URI
     * Для content:// URI создает временную копию
     */
    public static String getPath(Context context, Uri uri) {
        // Для file:// URI просто возвращаем путь
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        
        // Для content:// URI пытаемся получить реальный путь
        String path = getRealPathFromURI(context, uri);
        
        if (path != null && new File(path).exists()) {
            return path;
        }
        
        // Если не получилось - создаем временную копию
        return createTempCopy(context, uri);
    }
    
    /**
     * Получает FileDescriptor для URI
     */
    public static FileDescriptor getFileDescriptor(Context context, Uri uri) {
        try {
            return context.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor();
        } catch (Exception e) {
            Log.e(TAG, "Error getting file descriptor", e);
            return null;
        }
    }
    
    /**
     * Пытается получить реальный путь из URI
     */
    private static String getRealPathFromURI(Context context, Uri uri) {
        String path = null;
        
        try {
            // Для MediaStore URIs
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = {MediaStore.Video.Media.DATA};
                Cursor cursor = context.getContentResolver().query(
                    uri, projection, null, null, null
                );
                
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                            path = cursor.getString(columnIndex);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
            
            // Для DocumentProvider URIs на Android 4.4+
            if (path == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    String docId = DocumentsContract.getDocumentId(uri);
                    
                    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                        String[] split = docId.split(":");
                        String type = split[0];
                        
                        Uri contentUri = null;
                        if ("video".equals(type)) {
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        }
                        
                        if (contentUri != null) {
                            String selection = "_id=?";
                            String[] selectionArgs = new String[]{split[1]};
                            path = getDataColumn(context, contentUri, selection, selectionArgs);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting real path from URI", e);
        }
        
        return path;
    }
    
    /**
     * Получает значение колонки _data
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        
        try {
            cursor = context.getContentResolver().query(uri, projection, 
                selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting data column", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
    
    /**
     * Создает временную копию файла из URI
     */
    private static String createTempCopy(Context context, Uri uri) {
        try {
            // Создаем временный файл
            File tempFile = File.createTempFile("video2midi_", ".mp4", 
                context.getCacheDir());
            tempFile.deleteOnExit();
            
            // Копируем содержимое
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            Log.d(TAG, "Creating temporary copy of video...");
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            inputStream.close();
            outputStream.close();
            
            Log.d(TAG, String.format("Temporary copy created: %s (%.2f MB)", 
                tempFile.getAbsolutePath(), totalBytes / (1024.0 * 1024.0)));
            
            return tempFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e(TAG, "Error creating temporary copy", e);
            return null;
        }
    }
}