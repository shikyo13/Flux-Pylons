package com.zerotheabsolute.quantumflux.client.tutorial;

public final class TutorialTimeline {

    private TutorialTimeline() {}

    public static double transition(double time, double start, double end) {
        if (end <= start) {
            return time >= start ? 1.0D : 0.0D;
        }
        return ease(clamp((time - start) / (end - start)));
    }

    public static double ease(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    public static double lerp(double from, double to, double progress) {
        return from + (to - from) * clamp(progress);
    }

    public static double cubicBezier(
            double start, double control1, double control2, double end, double progress) {
        double clamped = clamp(progress);
        double inverse = 1.0D - clamped;
        return inverse * inverse * inverse * start
                + 3.0D * inverse * inverse * clamped * control1
                + 3.0D * inverse * clamped * clamped * control2
                + clamped * clamped * clamped * end;
    }

    public static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
