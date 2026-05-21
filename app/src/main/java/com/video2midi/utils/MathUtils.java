package com.video2midi.utils;

public class MathUtils {
    
    public static double[] rotate(double x, double y, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        
        double newX = (y * cos) - (x * sin);
        double newY = (y * sin) + (x * cos);
        
        return new double[]{newX, newY};
    }
}
