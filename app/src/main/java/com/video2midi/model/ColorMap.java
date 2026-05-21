package com.video2midi.model;

public class ColorMap {
    private int r;
    private int g;
    private int b;
    
    public ColorMap(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }
    
    public int getR() { return r; }
    public void setR(int r) { this.r = r; }
    
    public int getG() { return g; }
    public void setG(int g) { this.g = g; }
    
    public int getB() { return b; }
    public void setB(int b) { this.b = b; }
    
    public boolean isEmpty() {
        return r == 0 && g == 0 && b == 0;
    }
    
    public int toAndroidColor() {
        return android.graphics.Color.rgb(r, g, b);
    }
    
    public int[] toArray() {
        return new int[]{r, g, b};
    }
}
