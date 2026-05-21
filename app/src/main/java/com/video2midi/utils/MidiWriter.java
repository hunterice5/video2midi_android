package com.video2midi.utils;

import android.util.Log;
import com.video2midi.model.MidiNote;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MidiWriter {
    private static final String TAG = "MidiWriter";
    
    private int tempo;
    private String trackName;
    private int ticksPerQuarterNote = 480;
    
    public MidiWriter(int tempo, String trackName) {
        this.tempo = tempo;
        this.trackName = trackName;
    }
    
    public void writeNotes(List<MidiNote> notes, File outputFile) throws IOException {
        // Сортируем ноты по времени
        List<MidiNote> sortedNotes = new ArrayList<>(notes);
        Collections.sort(sortedNotes, new Comparator<MidiNote>() {
            @Override
            public int compare(MidiNote n1, MidiNote n2) {
                return Float.compare(n1.getTime(), n2.getTime());
            }
        });
        
        // Создаем MIDI файл вручную
        ByteArrayOutputStream trackData = new ByteArrayOutputStream();
        
        // Track name event
        writeMetaEvent(trackData, 0, 0x03, trackName.getBytes());
        
        // Tempo event
        int microsecondsPerQuarter = 60000000 / tempo;
        byte[] tempoBytes = new byte[]{
            (byte) ((microsecondsPerQuarter >> 16) & 0xFF),
            (byte) ((microsecondsPerQuarter >> 8) & 0xFF),
            (byte) (microsecondsPerQuarter & 0xFF)
        };
        writeMetaEvent(trackData, 0, 0x51, tempoBytes);
        
        // Добавляем ноты
        long lastTick = 0;
        for (MidiNote note : sortedNotes) {
            long noteTick = (long) (note.getTime() * ticksPerQuarterNote);
            long durationTicks = (long) (note.getDuration() * ticksPerQuarterNote);
            
            // Note ON
            long deltaTime = noteTick - lastTick;
            writeVariableLength(trackData, deltaTime);
            trackData.write(0x90 | note.getChannel()); // Note ON
            trackData.write(note.getNote());
            trackData.write(note.getVelocity());
            lastTick = noteTick;
            
            // Note OFF
            deltaTime = durationTicks;
            writeVariableLength(trackData, deltaTime);
            trackData.write(0x80 | note.getChannel()); // Note OFF
            trackData.write(note.getNote());
            trackData.write(0);
            lastTick = noteTick + durationTicks;
        }
        
        // End of track
        writeVariableLength(trackData, 0);
        trackData.write(0xFF);
        trackData.write(0x2F);
        trackData.write(0x00);
        
        byte[] track = trackData.toByteArray();
        
        // Записываем MIDI файл
        FileOutputStream fos = new FileOutputStream(outputFile);
        
        // Header chunk
        fos.write("MThd".getBytes());
        writeInt32(fos, 6); // Header length
        writeInt16(fos, 0); // Format 0
        writeInt16(fos, 1); // 1 track
        writeInt16(fos, ticksPerQuarterNote);
        
        // Track chunk
        fos.write("MTrk".getBytes());
        writeInt32(fos, track.length);
        fos.write(track);
        
        fos.close();
        
        Log.d(TAG, "MIDI file written: " + outputFile.getAbsolutePath());
    }
    
    private void writeMetaEvent(ByteArrayOutputStream out, long deltaTime, 
                                int type, byte[] data) throws IOException {
        writeVariableLength(out, deltaTime);
        out.write(0xFF);
        out.write(type);
        writeVariableLength(out, data.length);
        out.write(data);
    }
    
    private void writeVariableLength(ByteArrayOutputStream out, long value) {
        long buffer = value & 0x7F;
        
        while ((value >>= 7) > 0) {
            buffer <<= 8;
            buffer |= ((value & 0x7F) | 0x80);
        }
        
        while (true) {
            out.write((int) (buffer & 0xFF));
            if ((buffer & 0x80) != 0) {
                buffer >>= 8;
            } else {
                break;
            }
        }
    }
    
    private void writeInt32(FileOutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
    
    private void writeInt16(FileOutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
    
    // Внутренний класс для ByteArrayOutputStream
    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        public void write(byte[] b) throws IOException {
            super.write(b, 0, b.length);
        }
    }
}
