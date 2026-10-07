package org.localcam.app;

// A deadline avoids halving 30 fps when a camera frame arrives a fraction of a millisecond early.
public final class CaptureCadence {
    private long nextNanos;
    private int previousFps;
    public boolean ready(long nowNanos, int fps) {
        if (fps != 15 && fps != 24 && fps != 30) throw new IllegalArgumentException("Invalid FPS");
        long interval = 1_000_000_000L / fps;
        if (previousFps != fps || nextNanos == 0) {
            previousFps = fps; nextNanos = nowNanos + interval; return true;
        }
        if (nowNanos + 2_000_000L < nextNanos) return false;
        nextNanos = Math.max(nextNanos + interval, nowNanos);
        return true;
    }
    public void reset() { nextNanos = 0; previousFps = 0; }
}
