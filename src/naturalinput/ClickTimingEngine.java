package naturalinput;

/**
 * Component 2: Gaussian click-interval generator.
 *
 * Intervals are drawn from N(mean, sigma(t)) via Box-Muller and kept inside
 * [minMs, maxMs] by resampling (truncated normal, so no pile-up at the edges).
 * Fatigue: sigma and mean both drift upward with session time following
 * 1 - e^(-t/tau), and recover when the engine idles.
 */
public final class ClickTimingEngine {
    private final GaussianSource gauss;
    private final double minMs, maxMs;
    private final double baseMean, baseSigma;
    private final double fatigueSigmaGain;  // max relative sigma increase (0.6 = +60%)
    private final double fatigueMeanShiftMs; // max mean slowdown in ms
    private final double tauMs;              // fatigue time constant
    private final double recoveryTauMs;

    private double activeMs;   // effective accumulated activity
    private long lastClickNanos = -1;

    public ClickTimingEngine(GaussianSource gauss, double minMs, double maxMs,
                             double fatigueSigmaGain, double fatigueMeanShiftMs,
                             double tauMs, double recoveryTauMs) {
        this.gauss = gauss;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.baseMean = (minMs + maxMs) / 2.0;
        this.baseSigma = (maxMs - minMs) / 6.0; // ±3σ spans the envelope
        this.fatigueSigmaGain = fatigueSigmaGain;
        this.fatigueMeanShiftMs = fatigueMeanShiftMs;
        this.tauMs = tauMs;
        this.recoveryTauMs = recoveryTauMs;
    }

    public static ClickTimingEngine defaults(GaussianSource g) {
        return new ClickTimingEngine(g, 90, 130, 0.6, 8.0, 120_000, 30_000);
    }

    /** 0 = fresh, approaches 1 with sustained activity. */
    public double fatigue() { return 1.0 - Math.exp(-activeMs / tauMs); }

    public double currentSigma() { return baseSigma * (1.0 + fatigueSigmaGain * fatigue()); }

    public double currentMean() {
        return Math.min(maxMs, baseMean + fatigueMeanShiftMs * fatigue());
    }

    /** Returns the next interval in ms and updates fatigue state. */
    public synchronized double nextIntervalMs() {
        long now = System.nanoTime();
        if (lastClickNanos >= 0) {
            double gap = (now - lastClickNanos) / 1e6;
            if (gap > maxMs * 4) {
                // idle: recover exponentially for the idle portion
                activeMs *= Math.exp(-(gap - maxMs) / recoveryTauMs);
            } else {
                activeMs += gap;
            }
        }
        lastClickNanos = now;

        double mean = currentMean(), sigma = currentSigma();
        for (int i = 0; i < 64; i++) {
            double v = gauss.next(mean, sigma);
            if (v >= minMs && v <= maxMs) return v;
        }
        return Math.max(minMs, Math.min(maxMs, mean));
    }

    /** Hold duration for the press itself (short, also Gaussian). */
    public synchronized double nextHoldMs() {
        double v = gauss.next(18 + 6 * fatigue(), 4);
        return Math.max(8, Math.min(40, v));
    }

    public synchronized void reset() { activeMs = 0; lastClickNanos = -1; }
}
