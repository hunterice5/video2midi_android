package com.video2midi.model;

public class MidiNote {
    private int channel;
    private int note;
    private float time;
    private float duration;
    private int velocity;
    
    public MidiNote(int channel, int note, float time, float duration, int velocity) {
        this.channel = channel;
        this.note = note;
        this.time = time;
        this.duration = duration;
        this.velocity = velocity;
    }
    
    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }
    
    public int getNote() { return note; }
    public void setNote(int note) { this.note = note; }
    
    public float getTime() { return time; }
    public void setTime(float time) { this.time = time; }
    
    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = duration; }
    
    public int getVelocity() { return velocity; }
    public void setVelocity(int velocity) { this.velocity = velocity; }
}
