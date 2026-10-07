package org.localcam.app;

/** Changes compression only: never hides a resolution or FPS downgrade. */
public final class AdaptiveQuality {
    private int quality, ceiling, healthy;
    public int quality(int selected, boolean enabled) {
        if (ceiling != selected || !enabled || quality == 0) { ceiling = selected; quality = selected; healthy = 0; }
        return quality;
    }
    public void observe(double conversionMs, double writeMs, long dropped, int fps, boolean enabled) {
        if (!enabled) return;
        double budget = 1000d / fps;
        if (conversionMs > budget * .85 || writeMs > budget || dropped > 0) {
            quality = Math.max(40, quality - 5); healthy = 0;
        } else if (conversionMs < budget * .6 && writeMs < budget * .7 && ++healthy >= 5) {
            quality = Math.min(ceiling, quality + 2); healthy = 0;
        } else if (conversionMs >= budget * .6 || writeMs >= budget * .7) healthy = 0;
    }
    public void reset() { quality = ceiling = healthy = 0; }
}
