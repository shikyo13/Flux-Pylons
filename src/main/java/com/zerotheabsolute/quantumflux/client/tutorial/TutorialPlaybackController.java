package com.zerotheabsolute.quantumflux.client.tutorial;

import java.util.Arrays;

public final class TutorialPlaybackController {

    private final double[] durations;
    private int sceneIndex;
    private double elapsedSeconds;
    private boolean paused;
    private long lastFrameNanos;

    public TutorialPlaybackController(double[] durations, int initialScene) {
        if (durations.length == 0 || Arrays.stream(durations).anyMatch(value -> !Double.isFinite(value) || value <= 0.0D)) {
            throw new IllegalArgumentException("Tutorial scenes require positive durations");
        }
        this.durations = durations.clone();
        this.sceneIndex = Math.max(0, Math.min(durations.length - 1, initialScene));
    }

    public void onFrame(long nowNanos) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos;
            return;
        }
        double delta = Math.min(0.25D, Math.max(0.0D, (nowNanos - lastFrameNanos) / 1_000_000_000.0D));
        lastFrameNanos = nowNanos;
        if (!paused) {
            advance(delta);
        }
    }

    public void advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0D) {
            return;
        }
        elapsedSeconds += deltaSeconds;
        while (elapsedSeconds >= duration()) {
            elapsedSeconds -= duration();
            if (sceneIndex + 1 < durations.length) {
                sceneIndex++;
            } else {
                elapsedSeconds = duration();
                paused = true;
                break;
            }
        }
    }

    public void togglePaused() {
        if (paused && elapsedSeconds >= duration() && sceneIndex == durations.length - 1) {
            replay();
            return;
        }
        paused = !paused;
        lastFrameNanos = 0L;
    }

    public void replay() {
        elapsedSeconds = 0.0D;
        paused = false;
        lastFrameNanos = 0L;
    }

    public void seek(double seconds) {
        if (!Double.isFinite(seconds)) return;
        elapsedSeconds = Math.max(0.0D, Math.min(duration(), seconds));
        lastFrameNanos = 0L;
    }

    public void setPaused(boolean value) {
        paused = value;
        lastFrameNanos = 0L;
    }

    public void previous() {
        if (elapsedSeconds > 1.0D) {
            replay();
            return;
        }
        setScene(sceneIndex - 1);
    }

    public void next() {
        if (sceneIndex + 1 < durations.length) {
            setScene(sceneIndex + 1);
        }
    }

    public void setScene(int index) {
        sceneIndex = Math.max(0, Math.min(durations.length - 1, index));
        elapsedSeconds = 0.0D;
        paused = false;
        lastFrameNanos = 0L;
    }

    public int sceneIndex() {
        return sceneIndex;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public double duration() {
        return durations[sceneIndex];
    }

    public double progress() {
        return TutorialTimeline.clamp(elapsedSeconds / duration());
    }

    public boolean paused() {
        return paused;
    }

    public int sceneCount() {
        return durations.length;
    }
}
