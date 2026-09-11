package com.zerotheabsolute.quantumflux.util;
public final class Numbers {
    private Numbers() {}
    public static int clamp(long x, int min, int max) { return (int)Math.max(min, Math.min(max, x)); }
    public static long clamp(long x, long min, long max) { return Math.max(min, Math.min(max, x)); }
    public static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }
    public static double clamp(double x, double min, double max) { return Math.max(min, Math.min(max, x)); }
}
