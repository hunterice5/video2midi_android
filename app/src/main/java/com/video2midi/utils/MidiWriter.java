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
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            writeNotes(notes, fos);
        }
        Log.d(TAG, "MIDI file successfully written: " + outputFile.getAbsolutePath() + " with " + notes.size() + " notes");
    }

    public void writeNotes(List<MidiNote> notes, java.io.OutputStream os) throws IOException {
        List<MidiEvent> events = new ArrayList<>();
        
        // Разделяем каждую ноту на события нажатия (Note ON) и отпускания (Note OFF)
        for (MidiNote note : notes) {
            long noteTick = (long) (note.getTime() * ticksPerQuarterNote);
            long durationTicks = (long) (note.getDuration() * ticksPerQuarterNote);
            long endTick = noteTick + durationTicks;
            
            // Note ON event (0x90)
            events.add(new MidiEvent(noteTick, 0x90, note.getChannel(), note.getNote(), note.getVelocity()));
            // Note OFF event (0x80)
            events.add(new MidiEvent(endTick, 0x80, note.getChannel(), note.getNote(), 0));
        }
        
        // Сортируем все события по времени их возникновения
        Collections.sort(events, new Comparator<MidiEvent>() {
            @Override
            public int compare(MidiEvent e1, MidiEvent e2) {
                if (e1.tick != e2.tick) {
                    return Long.compare(e1.tick, e2.tick);
                }
                // Если тики совпадают, записываем сначала Note OFF (0x80), затем Note ON (0x90)
                return Integer.compare(e1.type, e2.type);
            }
        });
        
        // Создаем MIDI данные трека
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
        
        // Записываем события с дельта-тиками
        long lastTick = 0;
        for (MidiEvent event : events) {
            long deltaTime = event.tick - lastTick;
            if (deltaTime < 0) {
                deltaTime = 0; // Защита от переполнения
            }
            writeVariableLength(trackData, deltaTime);
            trackData.write(event.type | event.channel); // Статус байт (тип + канал)
            trackData.write(event.note);
            trackData.write(event.velocity);
            lastTick = event.tick;
        }
        
        // End of track
        writeVariableLength(trackData, 0);
        trackData.write(0xFF);
        trackData.write(0x2F);
        trackData.write(0x00);
        
        byte[] track = trackData.toByteArray();
        
        // Header chunk (MThd)
        os.write("MThd".getBytes());
        writeInt32(os, 6); // Длина заголовка
        writeInt16(os, 0); // Формат 0 (один трек)
        writeInt16(os, 1); // 1 трек
        writeInt16(os, ticksPerQuarterNote);
        
        // Track chunk (MTrk)
        os.write("MTrk".getBytes());
        writeInt32(os, track.length);
        os.write(track);
        
        os.flush();
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
    
    private void writeInt32(java.io.OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
    
    private void writeInt16(java.io.OutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
    
    // Внутренний класс для удобства записи
    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        public void write(byte[] b) throws IOException {
            super.write(b, 0, b.length);
        }
    }

    // Вспомогательный класс для представления единичного MIDI события
    private static class MidiEvent {
        long tick;
        int type; // 0x90 (Note ON) или 0x80 (Note OFF)
        int channel;
        int note;
        int velocity;

        public MidiEvent(long tick, int type, int channel, int note, int velocity) {
            this.tick = tick;
            this.type = type;
            this.channel = channel;
            this.note = note;
            this.velocity = velocity;
        }
    }
}
